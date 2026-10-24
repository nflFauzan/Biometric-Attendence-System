package com.biometricattendance.leave;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.biometricattendance.common.ApiException;

/**
 * Penyimpanan isi surat pendukung di disk (K-078). Nama file di disk adalah UUID acak, jadi nama asli dari
 * pengguna tidak pernah dipakai sebagai path. Jenis file ditentukan dari isi (magic bytes), bukan dari nama atau
 * Content-Type kiriman browser.
 */
@Component
public class AttachmentStorage {

	static final long MAX_BYTES = 5L * 1024 * 1024;
	private static final Pattern KEY = Pattern.compile("[0-9a-f]{32}");

	private final Path root;

	public AttachmentStorage(@Value("${app.uploads.dir}") String dir) {
		this.root = Path.of(dir).toAbsolutePath().normalize();
		try {
			Files.createDirectories(root);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Folder lampiran tidak bisa dibuat: " + root, ex);
		}
	}

	public record Stored(String key, String contentType, long size, String sha256) {
	}

	public Stored store(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw ApiException.badRequest("Pilih file surat yang akan diunggah");
		}
		if (file.getSize() > MAX_BYTES) {
			throw new ApiException(HttpStatus.CONTENT_TOO_LARGE, "Ukuran file maksimal 5 MB");
		}
		try {
			byte[] bytes = file.getBytes();
			String contentType = detect(bytes);
			if (contentType == null) {
				throw ApiException.badRequest("Format file harus PDF, JPG, atau PNG");
			}
			String key = UUID.randomUUID().toString().replace("-", "");
			Path temp = Files.createTempFile(root, "upload-", ".tmp");
			try {
				Files.write(temp, bytes);
				Files.move(temp, path(key), StandardCopyOption.ATOMIC_MOVE);
			}
			finally {
				Files.deleteIfExists(temp);
			}
			return new Stored(key, contentType, bytes.length, sha256(bytes));
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Gagal menyimpan lampiran", ex);
		}
	}

	/**
	 * Menulis byte mentah ke volume tanpa aturan jenis lampiran (dipakai spesimen tanda tangan yang sudah
	 * divalidasi dan dinormalkan pemanggilnya). Mengembalikan kunci acak.
	 */
	public String storeBytes(byte[] bytes) {
		try {
			String key = UUID.randomUUID().toString().replace("-", "");
			Path temp = Files.createTempFile(root, "upload-", ".tmp");
			try {
				Files.write(temp, bytes);
				Files.move(temp, path(key), StandardCopyOption.ATOMIC_MOVE);
			}
			finally {
				Files.deleteIfExists(temp);
			}
			return key;
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Gagal menyimpan berkas", ex);
		}
	}

	public byte[] readBytes(String key) {
		try {
			return Files.readAllBytes(path(key));
		}
		catch (java.nio.file.NoSuchFileException ex) {
			throw ApiException.notFound("File");
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Gagal membaca berkas", ex);
		}
	}

	public Resource load(String key) {
		Path file = path(key);
		if (!Files.isRegularFile(file)) {
			throw ApiException.notFound("File lampiran");
		}
		return new FileSystemResource(file);
	}

	/** Dipakai untuk membersihkan file bila transaksi yang mencatat metadatanya dibatalkan. */
	public void delete(String key) {
		try {
			Files.deleteIfExists(path(key));
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Gagal menghapus file lampiran yatim", ex);
		}
	}

	private Path path(String key) {
		if (key == null || !KEY.matcher(key).matches()) {
			throw new IllegalArgumentException("Kunci lampiran tidak sah");
		}
		return root.resolve(key);
	}

	/** PDF ("%PDF-"), JPEG (FF D8 FF), atau PNG (89 50 4E 47 0D 0A 1A 0A); selain itu null. */
	static String detect(byte[] b) {
		if (startsWith(b, 0x25, 0x50, 0x44, 0x46, 0x2D)) {
			return "application/pdf";
		}
		if (startsWith(b, 0xFF, 0xD8, 0xFF)) {
			return "image/jpeg";
		}
		if (startsWith(b, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
			return "image/png";
		}
		return null;
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
