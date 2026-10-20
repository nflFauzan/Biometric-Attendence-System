package com.biometricattendance.devicecommand;

/** Pesan status perintah untuk pengguna web (spec §9, §16). */
public final class DeviceCommandMessages {

	private DeviceCommandMessages() {
	}

	public static String message(DeviceCommand command) {
		if (command.isDelete()) {
			return deletion(command);
		}
		if (command.isRisky()) {
			return risky(command);
		}
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

	private static String deletion(DeviceCommand command) {
		if (command.isActive()) {
			return "Menunggu mesin…";
		}
		if (DeviceCommand.BERHASIL.equals(command.getStatus())) {
			return "Terhapus";
		}
		return "SENSOR_ERROR".equals(command.getErrorCode()) ? "Sensor bermasalah. Hubungi teknisi."
				: "Penghapusan gagal (" + command.getErrorCode() + ").";
	}

	/** APPLY_NETWORK dan ROTATE_KEY (spec §18.5–§18.6). */
	private static String risky(DeviceCommand command) {
		boolean network = DeviceCommand.TYPE_APPLY_NETWORK.equals(command.getType());
		if (command.isActive()) {
			return "Menunggu mesin…";
		}
		String code = command.getErrorCode() == null ? "" : command.getErrorCode();
		return switch (command.getStatus()) {
			case DeviceCommand.BERHASIL -> network ? "Jaringan baru diterapkan" : "Kunci baru dipakai";
			case DeviceCommand.DIBATALKAN -> "Dibatalkan.";
			default -> network ? switch (code) {
				case "WIFI_GAGAL" ->
					"Gagal tersambung ke Wi-Fi '" + command.getErrorDetail() + "', kembali ke pengaturan lama.";
				case "SERVER_TIDAK_TERJANGKAU" -> "Server baru tidak terjangkau, kembali ke pengaturan lama.";
				case DeviceCommand.TIDAK_ADA_HASIL -> "Mesin tidak merespons. Periksa koneksi mesin.";
				default -> "Perubahan jaringan gagal (" + code + ").";
			} : DeviceCommand.KUNCI_BARU_TIDAK_DIPAKAI.equals(code)
					? "Mesin tidak memakai kunci baru dalam 24 jam. Kunci lama tetap berlaku."
					: "Ganti kunci gagal (" + code + ").";
		};
	}

	private static String failure(String code, String detail) {
		return switch (code == null ? "" : code) {
			case "JARI_TIDAK_COCOK" -> "Tiga tempelan tidak cocok satu sama lain. Ulangi dengan jari yang sama.";
			case "KUALITAS_BURUK" -> "Sidik jari tidak terbaca jelas. Bersihkan jari atau sensor, lalu ulangi.";
			case "JARI_SUDAH_TERDAFTAR" -> "Sidik jari ini sudah terdaftar atas nama " + detail + ".";
			case "SENSOR_PENUH" -> "Kapasitas sensor mesin penuh.";
			case DeviceCommand.WAKTU_HABIS -> "Tidak ada jari yang ditempelkan. Ulangi pendaftaran.";
			case DeviceCommand.TIDAK_ADA_HASIL -> "Mesin tidak merespons. Periksa koneksi mesin.";
			case "SENSOR_ERROR" -> "Sensor bermasalah. Hubungi teknisi.";
			default -> "Pendaftaran gagal (" + code + ").";
		};
	}

}
