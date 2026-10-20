package com.biometricattendance.devicesettings;

import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.biometricattendance.account.PasswordConfirmation;
import com.biometricattendance.common.ApiException;
import com.biometricattendance.common.CurrentUser;
import com.biometricattendance.common.SecretCipher;
import com.biometricattendance.device.Device;
import com.biometricattendance.device.DeviceRepository;
import com.biometricattendance.devicecommand.DeviceCommand;
import com.biometricattendance.devicecommand.DeviceCommandDtos.CommandResponse;
import com.biometricattendance.devicecommand.DeviceCommandRepository;
import com.biometricattendance.devicecommand.DeviceCommandService;
import com.biometricattendance.devicesettings.DeviceNetworkConfig.WifiEntry;
import com.biometricattendance.devicesettings.NetworkDtos.NetworkPayload;
import com.biometricattendance.devicesettings.NetworkDtos.NetworkRequest;
import com.biometricattendance.devicesettings.NetworkDtos.NetworkView;
import com.biometricattendance.devicesettings.NetworkDtos.WifiInput;
import com.biometricattendance.devicesettings.NetworkDtos.WifiPayload;

/**
 * Ganti Wi-Fi dan alamat server alat rakitan lewat perintah APPLY_NETWORK (spec §18.5). Password Wi-Fi disimpan
 * terenkripsi dan hanya keluar dalam bentuk asli di balasan heartbeat ke mesin.
 */
@Service
public class NetworkService {

	private static final Logger log = LoggerFactory.getLogger(NetworkService.class);
	private static final Pattern SERVER_URL = Pattern.compile("^https?://\\S+$");
	private static final int MAX_SERVER_URL = 255;
	static final List<String> RISKY_TYPES = List.of(DeviceCommand.TYPE_APPLY_NETWORK, DeviceCommand.TYPE_ROTATE_KEY);

	private final DeviceRepository devices;
	private final DeviceNetworkConfigRepository configs;
	private final DeviceCommandRepository commands;
	private final DeviceCommandService commandService;
	private final PasswordConfirmation passwords;
	private final SecretCipher cipher;

	public NetworkService(DeviceRepository devices, DeviceNetworkConfigRepository configs,
			DeviceCommandRepository commands, DeviceCommandService commandService, PasswordConfirmation passwords,
			SecretCipher cipher) {
		this.devices = devices;
		this.configs = configs;
		this.commands = commands;
		this.commandService = commandService;
		this.passwords = passwords;
		this.cipher = cipher;
	}

	@Transactional(readOnly = true)
	public NetworkView view(Long deviceId) {
		Device device = rakitan(deviceId);
		List<DeviceNetworkConfig> applied = configs.findApplied(device.getId());
		List<String> wifi = currentWifi(applied).stream().map(WifiEntry::getSsid).toList();
		String serverUrl = applied.stream()
			.map(DeviceNetworkConfig::getServerUrl)
			.filter(url -> url != null)
			.findFirst()
			.orElse(null);
		CommandResponse latest = commands.findFirstByDeviceIdAndTypeInOrderByCreatedAtDesc(device.getId(), RISKY_TYPES)
			.map(CommandResponse::from)
			.orElse(null);
		return new NetworkView(device.getId(), wifi, serverUrl, latest);
	}

	@Transactional
	public CommandResponse request(Long deviceId, NetworkRequest request) {
		Device device = rakitan(deviceId);
		if (!device.isActive()) {
			throw ApiException.badRequest("Mesin nonaktif.");
		}
		passwords.require(request.confirmPassword());
		List<WifiInput> wifi = request.wifi() == null ? List.of() : request.wifi();
		String serverUrl = request.serverUrl() == null || request.serverUrl().isBlank() ? null
				: request.serverUrl().trim();
		validate(wifi, serverUrl);
		if (!cipher.isConfigured()) {
			throw ApiException.badRequest(SecretCipher.NOT_CONFIGURED);
		}
		Map<String, String> stored = new LinkedHashMap<>();
		currentWifi(configs.findApplied(device.getId())).forEach(e -> stored.put(e.getSsid(), e.getPasswordEnc()));
		List<WifiEntry> entries = wifi.stream().map(w -> {
			String password = w.password() == null ? "" : w.password();
			String sealed = password.isEmpty() && stored.containsKey(w.ssid()) ? stored.get(w.ssid())
					: cipher.encrypt(password);
			return new WifiEntry(w.ssid(), sealed);
		}).toList();
		Instant now = Instant.now();
		DeviceNetworkConfig config = configs.saveAndFlush(
				new DeviceNetworkConfig(device.getId(), entries, serverUrl, CurrentUser.username(), now));
		DeviceCommand command = DeviceCommand.applyNetwork(device, config.getId(), CurrentUser.username(), now);
		// Audit hanya nama Wi-Fi dan alamat server; password tidak pernah dicatat (spec §18.8).
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("ssids", config.ssids());
		details.put("serverUrl", serverUrl);
		commandService.createRisky(command, "NETWORK_REQUEST", CurrentUser.username(), details);
		return CommandResponse.from(command);
	}

	/**
	 * Isi perintah untuk heartbeat dengan password asli. Kosong jika paket tidak bisa dibuka (mis. kunci server
	 * diganti); perintah itu tidak dikirim dan akhirnya gagal karena tidak ada hasil, bukan membuat heartbeat gagal.
	 */
	@Transactional(readOnly = true)
	public Optional<NetworkPayload> payload(Long configId) {
		try {
			return configs.findById(configId).map(config -> new NetworkPayload(config.getWifi().isEmpty() ? null
					: config.getWifi()
						.stream()
						.map(e -> new WifiPayload(e.getSsid(), cipher.decrypt(e.getPasswordEnc())))
						.toList(),
					config.getServerUrl()));
		}
		catch (IllegalStateException ex) {
			log.error("Paket jaringan {} tidak bisa dibuka; perintah tidak dikirim", configId, ex);
			return Optional.empty();
		}
	}

	private static void validate(List<WifiInput> wifi, String serverUrl) {
		if (wifi.isEmpty() && serverUrl == null) {
			throw ApiException.badRequest("Isi minimal satu Wi-Fi atau alamat server.");
		}
		Set<String> seen = new HashSet<>();
		for (WifiInput entry : wifi) {
			if (!seen.add(entry.ssid())) {
				throw ApiException.badRequest("Nama Wi-Fi tidak boleh ganda");
			}
			int length = entry.password() == null ? 0 : entry.password().length();
			if (length > 0 && (length < 8 || length > 63)) {
				throw ApiException.badRequest("Password Wi-Fi 8–63 karakter atau kosong untuk Wi-Fi terbuka");
			}
		}
		if (serverUrl != null && !SERVER_URL.matcher(serverUrl).matches()) {
			throw ApiException.badRequest("Alamat server harus diawali http:// atau https://");
		}
		if (serverUrl != null && serverUrl.length() > MAX_SERVER_URL) {
			throw ApiException.badRequest("Alamat server maksimal 255 karakter");
		}
	}

	/** Wi-Fi dari paket berhasil terbaru yang memuat Wi-Fi. */
	private static List<WifiEntry> currentWifi(List<DeviceNetworkConfig> applied) {
		return applied.stream().map(DeviceNetworkConfig::getWifi).filter(w -> !w.isEmpty()).findFirst().orElse(List.of());
	}

	private Device rakitan(Long deviceId) {
		Device device = devices.findById(deviceId).orElseThrow(() -> ApiException.notFound("Mesin"));
		if (!device.isRakitan()) {
			throw ApiException.badRequest("Pengaturan hanya untuk alat rakitan");
		}
		return device;
	}

}
