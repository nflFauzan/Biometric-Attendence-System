package com.biometricattendance.devicesettings;

import java.time.Instant;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.biometricattendance.account.PasswordConfirmation;
import com.biometricattendance.common.ApiException;
import com.biometricattendance.common.CurrentUser;
import com.biometricattendance.common.SecretCipher;
import com.biometricattendance.device.Device;
import com.biometricattendance.device.DeviceKeys;
import com.biometricattendance.device.DeviceRepository;
import com.biometricattendance.devicecommand.DeviceCommand;
import com.biometricattendance.devicecommand.DeviceCommandDtos.CommandResponse;
import com.biometricattendance.devicecommand.DeviceCommandRepository;
import com.biometricattendance.devicecommand.DeviceCommandService;

/**
 * "Ganti kunci" alat rakitan lewat ROTATE_KEY (spec §18.6). Kunci baru disimpan sebagai hash (untuk dikenali) dan
 * terenkripsi (untuk dikirim ulang di heartbeat sampai dipakai); kunci lama tetap berlaku selama masa transisi.
 */
@Service
public class KeyRotationService {

	private static final Logger log = LoggerFactory.getLogger(KeyRotationService.class);

	private final DeviceRepository devices;
	private final DeviceCommandRepository commands;
	private final DeviceCommandService commandService;
	private final PasswordConfirmation passwords;
	private final SecretCipher cipher;

	public KeyRotationService(DeviceRepository devices, DeviceCommandRepository commands,
			DeviceCommandService commandService, PasswordConfirmation passwords, SecretCipher cipher) {
		this.devices = devices;
		this.commands = commands;
		this.commandService = commandService;
		this.passwords = passwords;
		this.cipher = cipher;
	}

	@Transactional
	public CommandResponse request(Long deviceId, String confirmPassword) {
		Device device = devices.findById(deviceId).orElseThrow(() -> ApiException.notFound("Mesin"));
		if (!device.isRakitan()) {
			throw ApiException.badRequest("Mesin ZK-Push tidak memakai kunci");
		}
		if (!device.isActive()) {
			throw ApiException.badRequest("Mesin nonaktif.");
		}
		passwords.require(confirmPassword);
		if (!cipher.isConfigured()) {
			throw ApiException.badRequest(SecretCipher.NOT_CONFIGURED);
		}
		// Diperiksa sebelum kunci tertunda ditulis, supaya ganti kunci yang sedang berjalan tidak tertimpa.
		if (commands.existsActiveRisky(device.getId())) {
			throw ApiException.conflict(DeviceCommandService.RISKY_BUSY);
		}
		String key = DeviceKeys.newKey();
		device.setPendingKey(DeviceKeys.hash(key), cipher.encrypt(key));
		DeviceCommand command = DeviceCommand.rotateKey(device, CurrentUser.username(), Instant.now());
		// Audit hanya berisi data perintah; kunci, hash, dan sandinya tidak pernah dicatat (spec §18.8).
		commandService.createRisky(command, "ROTATE_KEY_REQUEST");
		return CommandResponse.from(command);
	}

	/** Kunci baru untuk heartbeat, atau kosong jika tidak ada atau tidak bisa dibuka (perintah tidak dikirim). */
	public Optional<String> pendingKey(Device device) {
		if (device.getPendingKeyEnc() == null) {
			return Optional.empty();
		}
		try {
			return Optional.of(cipher.decrypt(device.getPendingKeyEnc()));
		}
		catch (IllegalStateException ex) {
			log.error("Kunci tertunda mesin {} tidak bisa dibuka; perintah ganti kunci tidak dikirim", device.getId(),
					ex);
			return Optional.empty();
		}
	}

}
