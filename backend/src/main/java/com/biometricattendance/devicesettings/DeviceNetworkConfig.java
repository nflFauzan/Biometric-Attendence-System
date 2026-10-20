package com.biometricattendance.devicesettings;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

/**
 * Paket jaringan satu permintaan APPLY_NETWORK (spec §18.5). Tidak pernah diubah setelah dibuat. Wi-Fi kosong = Wi-Fi
 * tidak diubah; {@code serverUrl} null = alamat server tidak diubah.
 */
@Entity
@Table(name = "device_network_configs")
public class DeviceNetworkConfig {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long deviceId;

	private String serverUrl;

	@ElementCollection
	@CollectionTable(name = "device_network_wifi", joinColumns = @JoinColumn(name = "config_id"))
	@OrderColumn(name = "position")
	private List<WifiEntry> wifi = new ArrayList<>();

	@Column(nullable = false)
	private String createdBy;

	@Column(nullable = false)
	private Instant createdAt;

	protected DeviceNetworkConfig() {
	}

	public DeviceNetworkConfig(Long deviceId, List<WifiEntry> wifi, String serverUrl, String createdBy,
			Instant createdAt) {
		this.deviceId = deviceId;
		this.wifi = new ArrayList<>(wifi);
		this.serverUrl = serverUrl;
		this.createdBy = createdBy;
		this.createdAt = createdAt;
	}

	public Long getId() {
		return id;
	}

	public Long getDeviceId() {
		return deviceId;
	}

	public String getServerUrl() {
		return serverUrl;
	}

	public List<WifiEntry> getWifi() {
		return wifi;
	}

	public List<String> ssids() {
		return wifi.stream().map(WifiEntry::getSsid).toList();
	}

	/** Satu Wi-Fi; password terenkripsi (format {@code v1:}), string kosong terenkripsi = Wi-Fi terbuka. */
	@Embeddable
	public static class WifiEntry {

		@Column(nullable = false)
		private String ssid;

		@Column(name = "password_enc", nullable = false)
		private String passwordEnc;

		protected WifiEntry() {
		}

		public WifiEntry(String ssid, String passwordEnc) {
			this.ssid = ssid;
			this.passwordEnc = passwordEnc;
		}

		public String getSsid() {
			return ssid;
		}

		public String getPasswordEnc() {
			return passwordEnc;
		}

	}

}
