package com.biometricattendance.devicesettings;

import java.time.Instant;

import com.biometricattendance.devicesettings.DeviceSettingsDtos.SettingsPackage;
import com.biometricattendance.devicesettings.DeviceSettingsDtos.SettingsValues;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** Paket pengaturan ringan berversi untuk satu alat rakitan (spec §7.2, §18.1). */
@Entity
@Table(name = "device_settings")
public class DeviceSettings {

	@Id
	private Long deviceId;

	/** Naik setiap kali nilainya berubah; mesin menerima paket lengkap jika versinya berbeda (spec §18.2). */
	@Column(nullable = false)
	private int version;

	@Column(nullable = false)
	private String headerText;

	@Column(nullable = false)
	private String greeting;

	@Column(nullable = false)
	private String nameDisplay;

	@Column(nullable = false)
	private int brightness;

	@Column(nullable = false)
	private int buzzerVolume;

	@Column(nullable = false)
	private int duplicateNoticeSeconds;

	@Column(nullable = false)
	private int heartbeatSeconds;

	@Column(nullable = false)
	private String updatedBy;

	@Column(nullable = false)
	private Instant updatedAt;

	/** Dua simpan bersamaan tidak boleh sama-sama menaikkan versi dari angka yang sama. Null = baris baru. */
	@Version
	private Long rowVersion;

	protected DeviceSettings() {
	}

	DeviceSettings(Long deviceId) {
		this.deviceId = deviceId;
	}

	/** Mengganti nilai; versi hanya naik jika ada yang berubah. Mengembalikan true jika berubah. */
	boolean apply(SettingsValues values, String actor, Instant now) {
		if (version > 0 && values().equals(values)) {
			return false;
		}
		headerText = values.headerText();
		greeting = values.greeting();
		nameDisplay = values.nameDisplay();
		brightness = values.brightness();
		buzzerVolume = values.buzzerVolume();
		duplicateNoticeSeconds = values.duplicateNoticeSeconds();
		heartbeatSeconds = values.heartbeatSeconds();
		version++;
		updatedBy = actor;
		updatedAt = now;
		return true;
	}

	SettingsValues values() {
		return new SettingsValues(headerText, greeting, nameDisplay, brightness, buzzerVolume, duplicateNoticeSeconds,
				heartbeatSeconds);
	}

	SettingsPackage toPackage() {
		return new SettingsPackage(version, headerText, greeting, nameDisplay, brightness, buzzerVolume,
				duplicateNoticeSeconds, heartbeatSeconds);
	}

	Long getDeviceId() {
		return deviceId;
	}

	int getVersion() {
		return version;
	}

	String getUpdatedBy() {
		return updatedBy;
	}

	Instant getUpdatedAt() {
		return updatedAt;
	}

}
