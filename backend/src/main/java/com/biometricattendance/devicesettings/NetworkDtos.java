package com.biometricattendance.devicesettings;

import java.util.List;

import com.biometricattendance.devicecommand.DeviceCommandDtos.CommandResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Jaringan alat rakitan (spec §18.5). Password Wi-Fi hanya ditulis, tidak pernah dibaca kembali lewat web. */
public final class NetworkDtos {

	private NetworkDtos() {
	}

	/** @param password kosong = pakai password tersimpan untuk SSID yang sudah berlaku, atau Wi-Fi terbuka */
	public record WifiInput(
			@NotBlank(message = "Nama Wi-Fi wajib diisi") @Size(max = 32,
					message = "Nama Wi-Fi maksimal 32 karakter") String ssid,
			String password) {
	}

	public record NetworkRequest(@NotBlank(message = "Password wajib diisi") String confirmPassword,
			@Size(max = 5, message = "Maksimal 5 Wi-Fi") List<@Valid WifiInput> wifi, String serverUrl) {
	}

	/**
	 * @param currentWifi nama Wi-Fi yang berlaku; kosong bersama {@code currentServerUrl} null = belum pernah diatur
	 * dari web
	 * @param latest perintah jaringan atau kunci terakhir mesin ini, atau null
	 */
	public record NetworkView(Long deviceId, List<String> currentWifi, String currentServerUrl,
			CommandResponse latest) {
	}

	/** Isi perintah APPLY_NETWORK di heartbeat, password sudah didekripsi; null = bagian itu tidak diubah. */
	public record NetworkPayload(List<WifiPayload> wifi, String serverUrl) {
	}

	/** @param password string kosong = Wi-Fi terbuka */
	public record WifiPayload(String ssid, String password) {
	}

}
