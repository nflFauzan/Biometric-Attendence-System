package com.biometricattendance.leave;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.biometricattendance.AttendanceFixtures;
import com.lowagie.text.pdf.PdfDictionary;
import com.lowagie.text.pdf.PdfName;
import com.lowagie.text.pdf.PdfObject;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;

/** Surat PDF izin/cuti dan verifikasi publik (spec 2026-10-24 bagian 8). Data sintetis. */
class LeaveLetterTests extends AttendanceFixtures {

	private static final String ME = "/api/me/leave-requests";
	private static final ZoneId ZONE = ZoneId.of("Asia/Jakarta");
	private static final byte[] PDF_EVIDENCE = "%PDF-1.4\n% bukti uji sintetis\n".getBytes(StandardCharsets.US_ASCII);

	@MockitoBean
	HrReviewerCounter hrCounter;

	@BeforeEach
	void otherHrExists() {
		when(hrCounter.countOtherActiveHr(org.mockito.ArgumentMatchers.any())).thenReturn(1L);
	}

	record Person(long employeeId, String name, RequestPostProcessor as) {
	}

	@Test
	void draftLetterIsWatermarkedAndHasNoQr() throws Exception {
		Person p = person(false);
		long type = createType("IZIN", false);
		long id = idOf(mockMvc.perform(json(post(ME), "{\"draft\":true,\"leaveTypeId\":" + type
				+ ",\"startDate\":\"2026-10-05\",\"endDate\":\"2026-10-06\",\"reason\":\"Urusan keluarga\"}").with(p.as()))
			.andExpect(status().isCreated()));

		byte[] pdf = mockMvc.perform(get(ME + "/" + id + "/letter.pdf").with(p.as()))
			.andExpect(status().isOk())
			.andExpect(header().string("Content-Type", "application/pdf"))
			.andExpect(header().string("Content-Disposition", String.format("attachment; filename=\"IZ-%06d.pdf\"", id)))
			.andReturn()
			.getResponse()
			.getContentAsByteArray();

		String text = text(pdf);
		assertThat(text).contains("DRAFT").contains(p.name()).contains("SURAT PERMOHONAN IZIN");
		assertThat(text).doesNotContain("/verifikasi/");
		assertThat(imageCount(pdf)).isZero();
		// Draft tidak terlihat oleh manajer.
		mockMvc.perform(get("/api/leave-requests/" + id + "/letter.pdf").with(HR)).andExpect(status().isNotFound());
	}

	@Test
	void approvedDoubleLetterShowsBothStagesAndVerificationLink() throws Exception {
		Person p = person(false);
		long id = submitMine(p, createType("UMUM", true), "2026-10-05");
		mockMvc.perform(decide(id, "approve").with(HR)).andExpect(status().isOk());
		mockMvc.perform(decide(id, "approve").with(OWNER)).andExpect(status().isOk());

		String text = text(download(ME, id, p.as()));
		assertThat(text).contains("HR/Finance").contains("Owner").contains("/verifikasi/");
		assertThat(text).contains("SURAT PERMOHONAN IZIN / CUTI").contains(String.format("IZ-%06d", id));
		assertThat(text).contains("disetujui secara elektronik");
		assertThat(text).doesNotContain("MENUNGGU PERSETUJUAN").doesNotContain("DRAFT");
		String token = jdbc.queryForObject("select verification_token from leave_requests where id = ?", String.class, id);
		assertThat(text).contains("/verifikasi/" + token);
	}

	@Test
	void pendingAndRejectedLettersCarryStatusStamp() throws Exception {
		Person p = person(false);
		long pending = submitMine(p, createType("IZIN", false), "2026-10-05");
		assertThat(text(download(ME, pending, p.as()))).contains("MENUNGGU PERSETUJUAN").contains("Disetujui oleh");

		long rejected = submitMine(p, createType("IZIN", false), "2026-10-12");
		mockMvc.perform(decide(rejected, "reject", "Bentrok jadwal").with(HR)).andExpect(status().isOk());
		assertThat(text(download(ME, rejected, p.as()))).contains("DITOLAK");

		long cancelled = submitMine(p, createType("IZIN", false), "2026-10-19");
		mockMvc.perform(decide(cancelled, "cancel", "Batal").with(HR)).andExpect(status().isOk());
		assertThat(text(download(ME, cancelled, p.as()))).contains("DIBATALKAN");
	}

	@Test
	void recordedOwnerDecisionIsWrittenAsTextNotSignature() throws Exception {
		Person p = person(false);
		long id = submitMine(p, createType("UMUM", true), "2026-10-05");
		mockMvc.perform(decide(id, "approve").with(HR)).andExpect(status().isOk());
		String json = "{\"approved\":true,\"method\":\"WHATSAPP\",\"decidedAt\":\"" + LocalDateTime.now(ZONE)
				+ "\",\"note\":\"Owner setuju lewat WA\"}";
		var request = multipart("/api/leave-requests/" + id + "/owner-decision")
			.file(new MockMultipartFile("decision", "", "application/json", json.getBytes(StandardCharsets.UTF_8)))
			.file(new MockMultipartFile("evidence", "bukti.pdf", "application/pdf", PDF_EVIDENCE))
			.with(csrf())
			.with(HR);
		mockMvc.perform(request).andExpect(status().isOk());

		String text = text(download("/api/leave-requests", id, HR));
		assertThat(text).contains("Disetujui melalui WhatsApp").contains("dicatat oleh HR/Finance");
		// Username login tidak boleh muncul di surat (akun HR uji tidak terhubung ke karyawan).
		assertThat(text).doesNotContain("hr-absensi");
		assertThat(text).doesNotContain("MENUNGGU PERSETUJUAN");
	}

	@Test
	void cutiLetterShowsTemplateFields() throws Exception {
		Person p = person(false);
		long type = createType("CUTI", false);
		long id = idOf(mockMvc.perform(json(post(ME), "{\"leaveTypeId\":" + type
				+ ",\"startDate\":\"2026-10-05\",\"endDate\":\"2026-10-07\",\"reason\":\"Liburan keluarga\","
				+ "\"contactPhone\":\"081234567890\",\"leaveAddress\":\"Jl. Uji Coba No. 1\","
				+ "\"delegateTo\":\"Rekan Satu\"}").with(p.as())).andExpect(status().isCreated()));

		String text = text(download(ME, id, p.as()));
		assertThat(text).contains("SURAT PERMOHONAN CUTI").contains("Alamat selama cuti").contains("Jl. Uji Coba No. 1");
		assertThat(text).contains("Keperluan").contains("Liburan keluarga").contains("No. HP").contains("081234567890");
		assertThat(text).contains("Tugas dilimpahkan kepada").contains("Rekan Satu");
		assertThat(text).contains("3 hari");
	}

	@Test
	void sickLetterShowsTitleAndHealthFacility() throws Exception {
		Person p = person(false);
		long type = createType("SAKIT", false);
		long id = idOf(mockMvc.perform(json(post(ME), "{\"leaveTypeId\":" + type
				+ ",\"startDate\":\"2026-10-05\",\"endDate\":\"2026-10-05\",\"reason\":\"Demam\","
				+ "\"healthFacility\":\"Klinik Sehat Uji\"}").with(p.as())).andExpect(status().isCreated()));

		String text = text(download(ME, id, p.as()));
		assertThat(text).contains("SURAT KETERANGAN SAKIT").contains("Faskes / dokter").contains("Klinik Sehat Uji");
	}

	@Test
	void employeeLetterEmbedsApplicantSpecimenAndNamesAttachments() throws Exception {
		Person withSpecimen = person(true);
		Person without = person(false);
		long type = createType("IZIN", false);
		long a = submitMine(withSpecimen, type, "2026-10-05");
		long b = submitMine(without, type, "2026-10-05");

		assertThat(jdbc.queryForObject("select applicant_signature_key from leave_requests where id = ?", String.class, a))
			.isNotNull();
		assertThat(jdbc.queryForObject("select applicant_signature_key from leave_requests where id = ?", String.class, b))
			.isNull();
		mockMvc.perform(multipart(ME + "/" + a + "/attachments")
			.file(new MockMultipartFile("file", "surat-dokter.pdf", "application/pdf", PDF_EVIDENCE))
			.with(csrf())
			.with(withSpecimen.as())).andExpect(status().isCreated());

		byte[] pdfA = download(ME, a, withSpecimen.as());
		byte[] pdfB = download(ME, b, without.as());
		// QR selalu ada pada surat yang sudah dikirim (1 gambar). Spesimen pemohon (PNG transparan) menambah gambar
		// beserta maskenya, jadi jumlahnya lebih banyak daripada surat tanpa spesimen.
		assertThat(imageCount(pdfB)).isEqualTo(1);
		assertThat(imageCount(pdfA)).isGreaterThan(imageCount(pdfB));
		assertThat(text(pdfA)).contains("surat-dokter.pdf");
	}

	@Test
	void employeeCannotDownloadAnotherEmployeesLetter() throws Exception {
		Person mine = person(false);
		Person other = person(false);
		long id = submitMine(mine, createType("IZIN", false), "2026-10-05");

		mockMvc.perform(get(ME + "/" + id + "/letter.pdf").with(other.as())).andExpect(status().isNotFound());
		mockMvc.perform(get(ME + "/" + id + "/letter.pdf").with(mine.as())).andExpect(status().isOk());
		// Pengunduhan oleh pemilik tidak diaudit.
		assertThat(letterViews(id)).isZero();
	}

	@Test
	void managerDownloadIsAudited() throws Exception {
		Person p = person(false);
		long id = submitMine(p, createType("IZIN", false), "2026-10-05");

		mockMvc.perform(get("/api/leave-requests/" + id + "/letter.pdf").with(HR))
			.andExpect(status().isOk())
			.andExpect(header().string("Content-Type", "application/pdf"));
		assertThat(letterViews(id)).isEqualTo(1);
	}

	@Test
	void publicVerificationShowsMinimalData() throws Exception {
		Person p = person(false);
		long id = submitMine(p, createType("CUTI", true), "2026-10-05", true);
		mockMvc.perform(decide(id, "approve").with(HR)).andExpect(status().isOk());
		String token = jdbc.queryForObject("select verification_token from leave_requests where id = ?", String.class, id);

		mockMvc.perform(get("/api/public/letters/" + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.letterNumber").value(String.format("IZ-%06d", id)))
			.andExpect(jsonPath("$.employeeName").value(p.name()))
			.andExpect(jsonPath("$.branchName").exists())
			.andExpect(jsonPath("$.leaveTypeName").exists())
			.andExpect(jsonPath("$.startDate").value("2026-10-05"))
			.andExpect(jsonPath("$.endDate").value("2026-10-05"))
			.andExpect(jsonPath("$.status").value("PERLU_TINJAU_OWNER"))
			.andExpect(jsonPath("$.approvals.length()").value(1))
			.andExpect(jsonPath("$.approvals[0].stage").value("HR"))
			.andExpect(jsonPath("$.approvals[0].approved").value(true))
			.andExpect(jsonPath("$.approvals[0].decidedByName").value("HR/Finance"))
			.andExpect(jsonPath("$.approvals[0].method").value("WEB"))
			.andExpect(jsonPath("$.reason").doesNotExist())
			.andExpect(jsonPath("$.leaveAddress").doesNotExist())
			.andExpect(jsonPath("$.contactPhone").doesNotExist())
			.andExpect(jsonPath("$.attachments").doesNotExist())
			.andExpect(jsonPath("$.verificationToken").doesNotExist());

		// Username login (hr-absensi) tidak boleh bocor ke halaman publik.
		String body = mockMvc.perform(get("/api/public/letters/" + token)).andReturn().getResponse().getContentAsString();
		assertThat(body).doesNotContain("hr-absensi");

		mockMvc.perform(get("/api/public/letters/" + "0".repeat(32)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.detail").value("Surat tidak ditemukan"));
		mockMvc.perform(get("/api/public/letters/xxx")).andExpect(status().isNotFound());
	}

	@Test
	void tokenOfDraftIsNotFound() throws Exception {
		Person p = person(false);
		long id = idOf(mockMvc.perform(json(post(ME), "{\"draft\":true,\"leaveTypeId\":" + createType("IZIN", false) + "}")
			.with(p.as())).andExpect(status().isCreated()));
		jdbc.update("update leave_requests set verification_token = ? where id = ?", "a".repeat(32), id);

		mockMvc.perform(get("/api/public/letters/" + "a".repeat(32)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.detail").value("Surat tidak ditemukan"));
	}

	@Test
	void draftWithoutTypeAndDatesStillRenders() throws Exception {
		Person p = person(false);
		long id = idOf(mockMvc.perform(json(post(ME), "{\"draft\":true}").with(p.as())).andExpect(status().isCreated()));

		String text = text(download(ME, id, p.as()));
		assertThat(text).contains("DRAFT").contains(p.name()).contains("tanggal belum diisi");
		assertThat(text).doesNotContain("/verifikasi/");
	}

	@Test
	void publicBaseUrlIsDerivedFromRequestWhenNotConfigured() throws Exception {
		Person p = person(false);
		long id = submitMine(p, createType("IZIN", false), "2026-10-05");
		String token = jdbc.queryForObject("select verification_token from leave_requests where id = ?", String.class, id);
		String text = text(download(ME, id, p.as()));
		assertThat(text).contains("http://localhost/verifikasi/" + token);
	}

	// ---- Helper ----

	private long letterViews(long requestId) {
		return jdbc.queryForObject("select count(*) from audit_logs where action = 'VIEW' and entity_type = 'LEAVE_LETTER' "
				+ "and entity_id = ?", Long.class, String.valueOf(requestId));
	}

	private byte[] download(String base, long id, RequestPostProcessor as) throws Exception {
		return mockMvc.perform(get(base + "/" + id + "/letter.pdf").with(as))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsByteArray();
	}

	static String text(byte[] pdf) throws Exception {
		PdfReader reader = new PdfReader(pdf);
		try {
			PdfTextExtractor extractor = new PdfTextExtractor(reader);
			StringBuilder all = new StringBuilder();
			for (int page = 1; page <= reader.getNumberOfPages(); page++) {
				all.append(extractor.getTextFromPage(page)).append(' ');
			}
			return all.toString().replaceAll("\\s+", " ");
		}
		finally {
			reader.close();
		}
	}

	static int imageCount(byte[] pdf) throws Exception {
		PdfReader reader = new PdfReader(pdf);
		try {
			int count = 0;
			for (int page = 1; page <= reader.getNumberOfPages(); page++) {
				PdfDictionary resources = reader.getPageN(page).getAsDict(PdfName.RESOURCES);
				PdfDictionary xobjects = resources == null ? null : resources.getAsDict(PdfName.XOBJECT);
				if (xobjects == null) {
					continue;
				}
				for (PdfName name : xobjects.getKeys()) {
					// Gambar adalah stream, jadi getAsDict() mengembalikan null: baca objeknya langsung.
					PdfObject object = PdfReader.getPdfObject(xobjects.get(name));
					if (object instanceof PdfDictionary x && PdfName.IMAGE.equals(x.getAsName(PdfName.SUBTYPE))) {
						count++;
					}
				}
			}
			return count;
		}
		finally {
			reader.close();
		}
	}

	private long submitMine(Person p, long type, String day) throws Exception {
		return submitMine(p, type, day, false);
	}

	private long submitMine(Person p, long type, String day, boolean cuti) throws Exception {
		String extra = cuti ? ",\"contactPhone\":\"081234567890\",\"leaveAddress\":\"Jl. Uji 1\"" : "";
		return idOf(mockMvc.perform(json(post(ME), "{\"leaveTypeId\":" + type + ",\"startDate\":\"" + day
				+ "\",\"endDate\":\"" + day + "\",\"reason\":\"Keperluan uji\"" + extra + "}").with(p.as()))
			.andExpect(status().isCreated()));
	}

	private Person person(boolean specimen) throws Exception {
		long employee = createEmployee(createBranch(), null);
		String name = jdbc.queryForObject("select full_name from employees where id = ?", String.class, employee);
		String username = unique("akun-");
		mockMvc.perform(json(post("/api/users"), "{\"username\":\"" + username
				+ "\",\"password\":\"rahasia-uji-123\",\"role\":\"EMPLOYEE\",\"employeeId\":" + employee + "}")
			.with(OWNER)).andExpect(status().isCreated());
		RequestPostProcessor as = user(username).roles("EMPLOYEE");
		if (specimen) {
			mockMvc.perform(multipart(HttpMethod.PUT, "/api/me/signature")
				.file(new MockMultipartFile("file", "ttd.png", "image/png", signaturePng()))
				.with(as)
				.with(csrf())).andExpect(status().isOk());
		}
		return new Person(employee, name, as);
	}

	private static byte[] signaturePng() throws Exception {
		BufferedImage img = new BufferedImage(300, 100, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = img.createGraphics();
		g.setColor(Color.WHITE);
		g.fillRect(0, 0, 300, 100);
		g.setColor(Color.BLACK);
		g.drawLine(40, 60, 250, 20);
		g.dispose();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(img, "png", out);
		return out.toByteArray();
	}

	private long createType(String template, boolean ownerApproval) throws Exception {
		return idOf(mockMvc.perform(json(post("/api/leave-types"), "{\"name\":\"" + unique("Jenis ")
				+ "\",\"requiresDocument\":false,\"active\":true,\"requiresOwnerApproval\":" + ownerApproval
				+ ",\"letterTemplate\":\"" + template + "\"}").with(OWNER)).andExpect(status().isCreated()));
	}

	private static MockHttpServletRequestBuilder decide(long id, String action) {
		return decide(id, action, null);
	}

	private static MockHttpServletRequestBuilder decide(long id, String action, String note) {
		return json(post("/api/leave-requests/" + id + "/" + action), note == null ? "{}" : "{\"note\":\"" + note + "\"}");
	}

	private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String content) {
		return request.with(csrf()).contentType(MediaType.APPLICATION_JSON).content(content);
	}

}
