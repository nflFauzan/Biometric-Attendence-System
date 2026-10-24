package com.biometricattendance.signature;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import com.biometricattendance.account.UserAccount;
import com.biometricattendance.account.UserAccountRepository;
import com.biometricattendance.audit.AuditService;
import com.biometricattendance.common.ApiException;
import com.biometricattendance.common.CurrentUser;
import com.biometricattendance.leave.AttachmentStorage;
import com.biometricattendance.signature.SignatureDtos.SignatureStatus;

/**
 * Spesimen tanda tangan per akun. Gambar dinormalkan menjadi PNG transparan maksimal 600x200 (latar putih
 * dibuang), disimpan di volume yang sama dengan lampiran, dan setiap perubahan diaudit tanpa isi gambar.
 */
@Service
public class SignatureService {

	static final String ENTITY = "USER_SIGNATURE";
	static final long MAX_BYTES = 1024L * 1024;
	static final int MAX_WIDTH = 600;
	static final int MAX_HEIGHT = 200;
	/** Batas piksel gambar masukan agar gambar berukuran raksasa tidak menghabiskan memori. */
	private static final long MAX_PIXELS = 25_000_000L;
	/** Piksel dengan R, G, B semuanya minimal nilai ini dianggap latar putih. */
	private static final int WHITE_THRESHOLD = 245;
	private static final String INVALID = "File tanda tangan harus PNG atau JPG";

	static {
		System.setProperty("java.awt.headless", "true");
	}

	private final UserSignatureRepository signatures;
	private final UserAccountRepository accounts;
	private final AttachmentStorage storage;
	private final AuditService audit;

	public SignatureService(UserSignatureRepository signatures, UserAccountRepository accounts,
			AttachmentStorage storage, AuditService audit) {
		this.signatures = signatures;
		this.accounts = accounts;
		this.storage = storage;
		this.audit = audit;
	}

	/** Isi audit: hash dan ukuran saja, tanpa gambar. */
	record AuditView(String sha256, Long sizeBytes) {
	}

	@Transactional(readOnly = true)
	public byte[] mine() {
		UserSignature s = signatures.findByUserAccountId(currentAccount().getId())
			.orElseThrow(() -> ApiException.notFound("Tanda tangan"));
		return storage.readBytes(s.getStorageKey());
	}

	@Transactional(readOnly = true)
	public SignatureStatus status() {
		return signatures.findByUserAccountId(currentAccount().getId())
			.map(s -> new SignatureStatus(true, s.getUpdatedAt()))
			.orElse(new SignatureStatus(false, null));
	}

	@Transactional
	public SignatureStatus save(MultipartFile file) {
		UserAccount account = currentAccount();
		byte[] png = normalize(file);
		String key = storeWithCleanup(png);
		String hash = sha256(png);
		Instant now = Instant.now();
		UserSignature existing = signatures.findByUserAccountId(account.getId()).orElse(null);
		AuditView before = null;
		UserSignature saved;
		if (existing == null) {
			saved = signatures.saveAndFlush(new UserSignature(account.getId(), key, hash, now));
		}
		else {
			before = view(existing);
			String oldKey = existing.getStorageKey();
			existing.replace(key, hash, now);
			saved = signatures.saveAndFlush(existing);
			deleteAfterCommit(oldKey);
		}
		audit.record(CurrentUser.username(), "UPLOAD", ENTITY, saved.getId(), before, new AuditView(hash, (long) png.length),
				null);
		return new SignatureStatus(true, saved.getUpdatedAt());
	}

	/** Idempoten: tanpa spesimen tidak ada yang dihapus dan tidak ada audit. */
	@Transactional
	public void deleteMine() {
		UserAccount account = currentAccount();
		signatures.findByUserAccountId(account.getId()).ifPresent(s -> {
			AuditView before = view(s);
			signatures.delete(s);
			signatures.flush();
			audit.record(CurrentUser.username(), "DELETE", ENTITY, s.getId(), before, null, null);
			deleteAfterCommit(s.getStorageKey());
		});
	}

	/**
	 * Menyalin spesimen akun ke kunci baru (dibekukan ke rekaman surat atau tahap) dan mengembalikan kuncinya, atau
	 * null bila akun tidak ada, belum punya spesimen, atau file spesimennya hilang dari volume. Mengganti spesimen
	 * belakangan tidak mengubah salinan.
	 */
	@Transactional
	public String freezeCopy(String username) {
		if (username == null) {
			return null;
		}
		return accounts.findByUsername(username)
			.flatMap(a -> signatures.findByUserAccountId(a.getId()))
			.map(s -> readOrNull(s.getStorageKey()))
			.map(this::storeWithCleanup)
			.orElse(null);
	}

	/** Isi berkas PNG menurut kunci (salinan beku atau spesimen). Untuk dipakai server (PDF), bukan endpoint. */
	public byte[] load(String key) {
		return storage.readBytes(key);
	}

	private UserAccount currentAccount() {
		return accounts.findByUsername(CurrentUser.username()).orElseThrow(() -> ApiException.notFound("Akun"));
	}

	/** Ukuran spesimen untuk audit; null bila file-nya hilang dari volume (jangan menggagalkan penggantian/hapus). */
	private AuditView view(UserSignature s) {
		byte[] bytes = readOrNull(s.getStorageKey());
		return new AuditView(s.getSha256(), bytes == null ? null : (long) bytes.length);
	}

	/** Isi berkas, atau null bila hilang dari volume: spesimen yang hilang dianggap "tanpa spesimen". */
	private byte[] readOrNull(String key) {
		try {
			return storage.readBytes(key);
		}
		catch (ApiException ex) {
			if (ex.getStatus() == org.springframework.http.HttpStatus.NOT_FOUND) {
				return null;
			}
			throw ex;
		}
	}

	private String storeWithCleanup(byte[] bytes) {
		String key = storage.storeBytes(bytes);
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCompletion(int status) {
				if (status != STATUS_COMMITTED) {
					storage.delete(key);
				}
			}
		});
		return key;
	}

	private void deleteAfterCommit(String key) {
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				storage.delete(key);
			}
		});
	}

	// ---- Normalisasi gambar ----

	static byte[] normalize(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw ApiException.badRequest(INVALID);
		}
		if (file.getSize() > MAX_BYTES) {
			throw ApiException.badRequest("Ukuran file tanda tangan maksimal 1 MB");
		}
		byte[] bytes;
		try {
			bytes = file.getBytes();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		if (!isPng(bytes) && !isJpeg(bytes)) {
			throw ApiException.badRequest(INVALID);
		}
		return encode(whiteToTransparent(scaleToFit(decode(bytes))));
	}

	private static BufferedImage decode(byte[] bytes) {
		try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
			Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
			if (!readers.hasNext()) {
				throw ApiException.badRequest(INVALID);
			}
			ImageReader reader = readers.next();
			try {
				reader.setInput(in, true, true);
				if ((long) reader.getWidth(0) * reader.getHeight(0) > MAX_PIXELS) {
					throw ApiException.badRequest("Dimensi gambar tanda tangan terlalu besar");
				}
				BufferedImage image = reader.read(0);
				if (image == null) {
					throw ApiException.badRequest(INVALID);
				}
				return image;
			}
			finally {
				reader.dispose();
			}
		}
		catch (ApiException ex) {
			throw ex;
		}
		catch (IOException | RuntimeException ex) {
			throw ApiException.badRequest(INVALID);
		}
	}

	private static BufferedImage scaleToFit(BufferedImage source) {
		double scale = Math.min(1.0,
				Math.min((double) MAX_WIDTH / source.getWidth(), (double) MAX_HEIGHT / source.getHeight()));
		int w = Math.max(1, (int) Math.round(source.getWidth() * scale));
		int h = Math.max(1, (int) Math.round(source.getHeight() * scale));
		BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		try {
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			g.drawImage(source, 0, 0, w, h, null);
		}
		finally {
			g.dispose();
		}
		return out;
	}

	/** Piksel yang R, G, dan B-nya semua minimal 245 menjadi transparan penuh. */
	private static BufferedImage whiteToTransparent(BufferedImage image) {
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				int argb = image.getRGB(x, y);
				int r = (argb >> 16) & 0xFF;
				int g = (argb >> 8) & 0xFF;
				int b = argb & 0xFF;
				if (r >= WHITE_THRESHOLD && g >= WHITE_THRESHOLD && b >= WHITE_THRESHOLD) {
					image.setRGB(x, y, 0);
				}
			}
		}
		return image;
	}

	private static byte[] encode(BufferedImage image) {
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			if (!ImageIO.write(image, "png", out)) {
				throw new IllegalStateException("Penulis PNG tidak tersedia");
			}
			return out.toByteArray();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	private static boolean isPng(byte[] b) {
		return startsWith(b, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
	}

	private static boolean isJpeg(byte[] b) {
		return startsWith(b, 0xFF, 0xD8, 0xFF);
	}

	private static boolean startsWith(byte[] bytes, int... prefix) {
		if (bytes.length < prefix.length) {
			return false;
		}
		for (int i = 0; i < prefix.length; i++) {
			if ((bytes[i] & 0xFF) != prefix[i]) {
				return false;
			}
		}
		return true;
	}

	private static String sha256(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
