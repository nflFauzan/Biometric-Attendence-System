package com.biometricattendance.leave.letter;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.biometricattendance.leave.letter.LetterModel.Field;
import com.biometricattendance.leave.letter.LetterModel.SignatureBox;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfGState;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;

/**
 * Menggambar surat izin/cuti menjadi PDF A4 (spec 2026-10-24 bagian 8). Font Poppins Bold dan Open Sans
 * (Regular/SemiBold) dimuat sekali dari classpath dan di-embed; bila berkasnya tidak ada, dipakai Helvetica bawaan.
 */
@Component
public class LeaveLetterRenderer {

	private static final Logger log = LoggerFactory.getLogger(LeaveLetterRenderer.class);

	private static final float MM = 72f / 25.4f;
	private static final float MARGIN = 18 * MM;
	private static final float QR_SIZE = 30 * MM;
	private static final Color STEEL = new Color(0x10, 0x4F, 0xA6);
	private static final Color GOLD = new Color(0xF5, 0xCE, 0x02);
	private static final Color MUTED = new Color(0x55, 0x5B, 0x66);
	private static final Color LINE = new Color(0xB8, 0xBE, 0xC8);

	private static final Map<String, String> FONT_FILES = Map.of("bold", "fonts/Poppins-Bold.ttf", "regular",
			"fonts/OpenSans-Regular.ttf", "semibold", "fonts/OpenSans-SemiBold.ttf");

	static {
		System.setProperty("java.awt.headless", "true");
	}

	/** Byte TTF dimuat sekali; BaseFont dibuat per render karena menyimpan status glyph yang bisa berubah. */
	private record Fonts(byte[] heading, byte[] body, byte[] bodyStrong, boolean embedded) {
	}

	/** Font milik satu kali render. */
	private record Ctx(BaseFont heading, BaseFont body, BaseFont bodyStrong) {
	}

	private final Fonts fonts;

	public LeaveLetterRenderer() {
		byte[] heading = read("bold");
		byte[] body = read("regular");
		byte[] strong = read("semibold");
		this.fonts = heading != null && body != null && strong != null ? new Fonts(heading, body, strong, true)
				: new Fonts(null, null, null, false);
		if (!fonts.embedded()) {
			log.warn("Font surat tidak lengkap di classpath; memakai Helvetica bawaan");
		}
	}

	private static byte[] read(String role) {
		String path = FONT_FILES.get(role);
		try (InputStream in = LeaveLetterRenderer.class.getClassLoader().getResourceAsStream(path)) {
			return in == null ? null : in.readAllBytes();
		}
		catch (IOException ex) {
			log.warn("Font {} tidak bisa dibaca: {}", path, ex.getMessage());
			return null;
		}
	}

	private BaseFont create(String role, byte[] ttf) throws DocumentException, IOException {
		if (fonts.embedded()) {
			// cached=false: tiap render punya instance sendiri, jadi render bersamaan tidak berbagi status glyph.
			return BaseFont.createFont(FONT_FILES.get(role), BaseFont.IDENTITY_H, BaseFont.EMBEDDED, false, ttf, null);
		}
		return BaseFont.createFont(role.equals("regular") ? BaseFont.HELVETICA : BaseFont.HELVETICA_BOLD,
				BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
	}

	public byte[] render(LetterModel model) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		Document document = new Document(PageSize.A4, MARGIN, MARGIN, MARGIN, MARGIN);
		try {
			Ctx ctx = new Ctx(create("bold", fonts.heading()), create("regular", fonts.body()),
					create("semibold", fonts.bodyStrong()));
			PdfWriter writer = PdfWriter.getInstance(document, out);
			writer.setPageEvent(new Stamp(model.stamp(), ctx.heading()));
			document.addTitle("Surat " + model.letterNumber());
			document.addCreator("Sistem absensi TAKKA STEEL");
			document.open();
			document.add(header(ctx, model));
			document.add(title(ctx, model));
			document.add(identity(ctx, model));
			document.add(intro(ctx, model));
			if (!model.fields().isEmpty()) {
				document.add(fields(ctx, model.fields()));
			}
			if (!model.attachmentNames().isEmpty()) {
				document.add(attachments(ctx, model.attachmentNames()));
			}
			document.add(boxes(ctx, model.boxes()));
			if (model.verifyUrl() != null) {
				document.add(verification(ctx, model.verifyUrl()));
			}
			document.add(footer(ctx, model.generatedAt()));
			document.close();
		}
		catch (DocumentException | IOException ex) {
			throw new IllegalStateException("Gagal membuat PDF surat", ex);
		}
		return out.toByteArray();
	}

	// ---- Bagian surat ----

	private PdfPTable header(Ctx c, LetterModel m) throws DocumentException {
		PdfPTable table = new PdfPTable(new float[] { 3f, 2f });
		table.setWidthPercentage(100);
		PdfPCell left = plainCell();
		left.addElement(new Paragraph("TAKKA STEEL", font(c.heading(), 18, STEEL)));
		left.addElement(new Paragraph(m.branchName(), font(c.bodyStrong(), 10, MUTED)));
		PdfPCell right = plainCell();
		Paragraph number = new Paragraph("No. " + m.letterNumber(), font(c.bodyStrong(), 11, Color.BLACK));
		number.setAlignment(Element.ALIGN_RIGHT);
		right.addElement(number);
		right.setVerticalAlignment(Element.ALIGN_MIDDLE);
		table.addCell(left);
		table.addCell(right);
		PdfPCell rule = plainCell();
		rule.setColspan(2);
		rule.setFixedHeight(4);
		rule.setBorder(Rectangle.BOTTOM);
		rule.setBorderColorBottom(GOLD);
		rule.setBorderWidthBottom(2f);
		table.addCell(rule);
		table.setSpacingAfter(14);
		return table;
	}

	private Paragraph title(Ctx c, LetterModel m) {
		Paragraph p = new Paragraph(m.title(), font(c.heading(), 14, Color.BLACK));
		p.setAlignment(Element.ALIGN_CENTER);
		p.setSpacingAfter(14);
		return p;
	}

	private PdfPTable identity(Ctx c, LetterModel m) throws DocumentException {
		PdfPTable table = labelValueTable();
		addRow(c, table, "Nama", m.employeeName());
		addRow(c, table, "No. Karyawan", m.employeeNumber());
		addRow(c, table, "Jabatan", m.position());
		addRow(c, table, "Cabang", m.branchName());
		table.setSpacingAfter(10);
		return table;
	}

	private Paragraph intro(Ctx c, LetterModel m) {
		Paragraph p = new Paragraph(m.intro(), font(c.body(), 10.5f, Color.BLACK));
		p.setLeading(16);
		p.setSpacingAfter(8);
		return p;
	}

	private PdfPTable fields(Ctx c, List<Field> fields) throws DocumentException {
		PdfPTable table = labelValueTable();
		for (Field f : fields) {
			addRow(c, table, f.label(), f.value());
		}
		table.setSpacingAfter(10);
		return table;
	}

	private PdfPTable attachments(Ctx c, List<String> names) throws DocumentException {
		PdfPTable table = labelValueTable();
		addRow(c, table, "Lampiran", String.join(", ", names));
		table.setSpacingAfter(10);
		return table;
	}

	private PdfPTable boxes(Ctx c, List<SignatureBox> boxes) throws DocumentException, IOException {
		PdfPTable table = new PdfPTable(boxes.size());
		table.setWidthPercentage(100);
		table.setSpacingBefore(8);
		table.setSpacingAfter(14);
		table.setKeepTogether(true);
		for (SignatureBox box : boxes) {
			PdfPCell cell = new PdfPCell();
			cell.setBorderColor(LINE);
			cell.setPadding(8);
			cell.setMinimumHeight(110);
			Paragraph title = new Paragraph(box.title(), font(c.bodyStrong(), 9.5f, STEEL));
			title.setAlignment(Element.ALIGN_CENTER);
			title.setSpacingAfter(4);
			cell.addElement(title);
			if (box.image() != null) {
				Image signature = Image.getInstance(box.image());
				signature.scaleToFit(130, 44);
				signature.setAlignment(Element.ALIGN_CENTER);
				cell.addElement(signature);
			}
			if (box.name() != null) {
				Paragraph name = new Paragraph(box.name(), font(c.bodyStrong(), 9.5f, Color.BLACK));
				name.setAlignment(Element.ALIGN_CENTER);
				cell.addElement(name);
			}
			if (box.text() != null) {
				Paragraph text = new Paragraph(box.text(), font(c.body(), 8.5f, MUTED));
				text.setAlignment(Element.ALIGN_CENTER);
				cell.addElement(text);
			}
			table.addCell(cell);
		}
		return table;
	}

	private PdfPTable verification(Ctx c, String url) throws DocumentException, IOException {
		PdfPTable table = new PdfPTable(new float[] { QR_SIZE + 10, 400 });
		table.setWidthPercentage(100);
		table.setKeepTogether(true);
		PdfPCell qr = plainCell();
		Image image = Image.getInstance(qrPng(url));
		image.scaleAbsolute(QR_SIZE, QR_SIZE);
		qr.addElement(image);
		PdfPCell text = plainCell();
		text.setVerticalAlignment(Element.ALIGN_MIDDLE);
		text.addElement(new Paragraph("Verifikasi keaslian surat", font(c.bodyStrong(), 10, Color.BLACK)));
		Paragraph hint = new Paragraph("Pindai kode QR atau buka alamat berikut:", font(c.body(), 8.5f, MUTED));
		hint.setSpacingAfter(2);
		text.addElement(hint);
		text.addElement(new Paragraph(url, font(c.body(), 8, STEEL)));
		table.addCell(qr);
		table.addCell(text);
		table.setSpacingAfter(10);
		return table;
	}

	private Paragraph footer(Ctx c, String generatedAt) {
		Paragraph p = new Paragraph("Dibuat dari sistem absensi pada " + generatedAt, font(c.body(), 8, MUTED));
		p.setSpacingBefore(6);
		return p;
	}

	// ---- Bantu ----

	private PdfPTable labelValueTable() throws DocumentException {
		PdfPTable table = new PdfPTable(new float[] { 1.7f, 3f });
		table.setWidthPercentage(100);
		return table;
	}

	private void addRow(Ctx c, PdfPTable table, String label, String value) {
		PdfPCell l = plainCell();
		l.setPaddingBottom(3);
		l.addElement(new Paragraph(label, font(c.bodyStrong(), 10, Color.BLACK)));
		PdfPCell v = plainCell();
		v.setPaddingBottom(3);
		v.addElement(new Paragraph(": " + (value == null || value.isBlank() ? "-" : value), font(c.body(), 10, Color.BLACK)));
		table.addCell(l);
		table.addCell(v);
	}

	private static PdfPCell plainCell() {
		PdfPCell cell = new PdfPCell();
		cell.setBorder(Rectangle.NO_BORDER);
		cell.setPadding(0);
		return cell;
	}

	private static Font font(BaseFont base, float size, Color color) {
		return new Font(base, size, Font.NORMAL, color);
	}

	private static byte[] qrPng(String content) throws IOException {
		try {
			BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 300, 300,
					Map.of(EncodeHintType.MARGIN, 1, EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M));
			BufferedImage image = new BufferedImage(matrix.getWidth(), matrix.getHeight(), BufferedImage.TYPE_INT_RGB);
			for (int x = 0; x < matrix.getWidth(); x++) {
				for (int y = 0; y < matrix.getHeight(); y++) {
					image.setRGB(x, y, matrix.get(x, y) ? 0x000000 : 0xFFFFFF);
				}
			}
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			ImageIO.write(image, "png", out);
			return out.toByteArray();
		}
		catch (WriterException ex) {
			throw new UncheckedIOException(new IOException(ex));
		}
	}

	/** Cap diagonal abu-abu semi-transparan di tengah setiap halaman. */
	private static final class Stamp extends PdfPageEventHelper {

		private final String text;
		private final BaseFont font;

		Stamp(String text, BaseFont font) {
			this.text = text;
			this.font = font;
		}

		@Override
		public void onEndPage(PdfWriter writer, Document document) {
			if (text == null) {
				return;
			}
			PdfContentByte cb = writer.getDirectContent();
			cb.saveState();
			PdfGState state = new PdfGState();
			state.setFillOpacity(0.18f);
			cb.setGState(state);
			cb.setColorFill(new Color(0x70, 0x70, 0x70));
			cb.beginText();
			cb.setFontAndSize(font, text.length() > 12 ? 40 : 84);
			Rectangle page = document.getPageSize();
			cb.showTextAligned(Element.ALIGN_CENTER, text, page.getWidth() / 2, page.getHeight() / 2, 45);
			cb.endText();
			cb.restoreState();
		}

	}

}
