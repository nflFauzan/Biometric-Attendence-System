package com.biometricattendance.auth;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mencabut sesi login milik satu akun (B-03): setelah password di-reset, akun dinonaktifkan,
 * atau role diubah, sesi lama tidak boleh dipakai lagi.
 *
 * Sesi disimpan Spring Session JDBC (tabel dari Flyway V2) dan diindeks per username di
 * PRINCIPAL_NAME. Penghapusan berjalan di transaksi yang sama dengan perubahan akunnya, jadi
 * jika perubahan akun dibatalkan, sesi juga tidak ikut terhapus. Atribut sesi ikut terhapus
 * lewat ON DELETE CASCADE.
 */
@Component
public class SessionRevoker {

	private final JdbcTemplate jdbc;

	public SessionRevoker(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** Menghapus semua sesi milik username. Mengembalikan jumlah sesi yang dihapus. */
	@Transactional(propagation = Propagation.MANDATORY)
	public int revokeAll(String username) {
		return jdbc.update("DELETE FROM SPRING_SESSION WHERE PRINCIPAL_NAME = ?", username);
	}

	/** Menghapus semua sesi milik username kecuali sesi yang sedang dipakai. */
	@Transactional(propagation = Propagation.MANDATORY)
	public int revokeOthers(String username, String currentSessionId) {
		return jdbc.update("DELETE FROM SPRING_SESSION WHERE PRINCIPAL_NAME = ? AND SESSION_ID <> ?", username,
				currentSessionId);
	}

}
