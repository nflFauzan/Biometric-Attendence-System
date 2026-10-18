package com.biometricattendance.devicecommand;

/** Pesan status perintah untuk pengguna web (spec §9, §16). */
public final class DeviceCommandMessages {

	private DeviceCommandMessages() {
	}

	public static String message(DeviceCommand command) {
		if (command.isActive()) {
			return command.getStep() == null ? "Menunggu mesin…" : "Tempelkan jari (" + command.getStep() + "/3)";
		}
		return switch (command.getStatus()) {
			case DeviceCommand.BERHASIL -> "Berhasil";
			case DeviceCommand.KEDALUWARSA -> "Mesin tidak mengambil perintah. Periksa koneksi mesin.";
			case DeviceCommand.DIBATALKAN -> "Pendaftaran dibatalkan.";
			default -> failure(command.getErrorCode(), command.getErrorDetail());
		};
	}

	private static String failure(String code, String detail) {
		return switch (code == null ? "" : code) {
			case "JARI_TIDAK_COCOK" -> "Tiga tempelan tidak cocok satu sama lain. Ulangi dengan jari yang sama.";
			case "KUALITAS_BURUK" -> "Sidik jari tidak terbaca jelas. Bersihkan jari atau sensor, lalu ulangi.";
			case "JARI_SUDAH_TERDAFTAR" -> "Sidik jari ini sudah terdaftar atas nama " + detail + ".";
			case "SENSOR_PENUH" -> "Kapasitas sensor mesin penuh.";
			case DeviceCommand.WAKTU_HABIS -> "Mesin tidak merespons. Periksa koneksi mesin.";
			case "SENSOR_ERROR" -> "Sensor bermasalah. Hubungi teknisi.";
			default -> "Pendaftaran gagal (" + code + ").";
		};
	}

}
