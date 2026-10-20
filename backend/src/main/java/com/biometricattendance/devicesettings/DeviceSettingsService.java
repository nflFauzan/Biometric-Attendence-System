package com.biometricattendance.devicesettings;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.biometricattendance.audit.AuditService;
import com.biometricattendance.common.ApiException;
import com.biometricattendance.common.CurrentUser;
import com.biometricattendance.device.Device;
import com.biometricattendance.device.DeviceRepository;
import com.biometricattendance.devicesettings.DeviceSettingsDtos.SettingsPackage;
import com.biometricattendance.devicesettings.DeviceSettingsDtos.SettingsResponse;
import com.biometricattendance.devicesettings.DeviceSettingsDtos.SettingsValues;

/**
 * Pengaturan ringan alat rakitan sebagai paket berversi (spec §7.2, §18.1–18.2). Nilai bawaan adalah parameter teknis
 * yang wajib sama dengan bawaan firmware; selama belum ada yang disimpan, server tidak mengirim paket apa pun.
 */
@Service
public class DeviceSettingsService {

	static final String ENTITY = "DEVICE";

	private final DeviceSettingsRepository settings;
	private final DeviceRepository devices;
	private final AuditService audit;
	private final SettingsValues defaults;

	public DeviceSettingsService(DeviceSettingsRepository settings, DeviceRepository devices, AuditService audit,
			@Value("${app.device.settings.default.header-text:}") String headerText,
			@Value("${app.device.settings.default.greeting:Silakan tempelkan jari}") String greeting,
			@Value("${app.device.settings.default.name-display:FULL}") String nameDisplay,
			@Value("${app.device.settings.default.brightness:80}") int brightness,
			@Value("${app.device.settings.default.buzzer-volume:50}") int buzzerVolume,
			@Value("${app.device.settings.default.duplicate-notice-seconds:60}") int duplicateNoticeSeconds,
			@Value("${app.device.settings.default.heartbeat-seconds:5}") int heartbeatSeconds) {
		this.settings = settings;
		this.devices = devices;
		this.audit = audit;
		this.defaults = new SettingsValues(headerText, greeting, nameDisplay, brightness, buzzerVolume,
				duplicateNoticeSeconds, heartbeatSeconds);
	}

	@Transactional(readOnly = true)
	public SettingsResponse get(Long deviceId) {
		Device device = rakitan(deviceId);
		return response(device, settings.findById(deviceId).orElse(null));
	}

	@Transactional
	public SettingsResponse update(Long deviceId, SettingsValues values) {
		return save(rakitan(deviceId), values.normalized(), Instant.now());
	}

	/** "Pengaturan semua mesin": semua alat rakitan aktif; versi hanya naik di mesin yang nilainya berubah. */
	@Transactional
	public List<SettingsResponse> updateAll(SettingsValues values) {
		SettingsValues normalized = values.normalized();
		Instant now = Instant.now();
		List<SettingsResponse> saved = new ArrayList<>();
		for (Device device : devices.findByProtocolAndActiveTrueOrderById(Device.PROTOCOL_RAKITAN)) {
			saved.add(save(device, normalized, now));
		}
		return saved;
	}

	/**
	 * Dipanggil setiap heartbeat. Mencatat versi yang dipakai mesin dan mengembalikan paket jika versinya berbeda dari
	 * server, termasuk jika mesin "lebih baru" (mis. database dipulihkan dari cadangan). Null = tidak perlu dikirim.
	 */
	@Transactional
	public SettingsPackage packageFor(Device device, Integer reportedVersion) {
		DeviceSettings current = settings.findById(device.getId()).orElse(null);
		int version = current == null ? 0 : current.getVersion();
		if (reportedVersion != null && reportedVersion >= 0 && reportedVersion <= version) {
			devices.recordSettingsApplied(device.getId(), reportedVersion);
		}
		return current == null || Objects.equals(reportedVersion, version) ? null : current.toPackage();
	}

	private SettingsResponse save(Device device, SettingsValues values, Instant now) {
		DeviceSettings row = settings.findById(device.getId()).orElse(null);
		SettingsResponse before = response(device, row);
		DeviceSettings target = row == null ? new DeviceSettings(device.getId()) : row;
		if (!target.apply(values, CurrentUser.username(), now)) {
			return before;
		}
		SettingsResponse after = response(device, settings.saveAndFlush(target));
		audit.record("UPDATE_SETTINGS", ENTITY, device.getId(), row == null ? null : before, after);
		return after;
	}

	private Device rakitan(Long deviceId) {
		Device device = devices.findById(deviceId).orElseThrow(() -> ApiException.notFound("Mesin"));
		if (!device.isRakitan()) {
			throw ApiException.badRequest("Pengaturan hanya untuk alat rakitan");
		}
		return device;
	}

	private SettingsResponse response(Device device, DeviceSettings row) {
		if (row == null) {
			return new SettingsResponse(device.getId(), 0, device.getSettingsAppliedVersion(), defaults, null, null);
		}
		return new SettingsResponse(device.getId(), row.getVersion(), device.getSettingsAppliedVersion(), row.values(),
				row.getUpdatedAt(), row.getUpdatedBy());
	}

}
