package com.biometricattendance.device;

/** Mesin pertama kali memakai kunci barunya; kunci tertunda sudah menjadi kunci utama (spec §18.6). */
public record KeyRotatedEvent(Long deviceId, String serialNumber) {
}
