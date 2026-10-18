package com.biometricattendance.devicecommand;

import java.util.UUID;

/** Status atau langkah perintah berubah; web mengambil data terbaru lewat API (SSE {@code DEVICE_COMMAND}). */
public record DeviceCommandChangedEvent(UUID commandId, Long employeeId) {
}
