package com.biometricattendance.devicesettings;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeviceNetworkConfigRepository extends JpaRepository<DeviceNetworkConfig, Long> {

	/**
	 * Paket yang sudah berhasil diterapkan mesin ini, terbaru dulu. Jaringan yang berlaku = Wi-Fi dari paket terbaru
	 * yang memuat Wi-Fi, dan alamat server dari paket terbaru yang memuat alamat server.
	 */
	@Query("select n from DeviceNetworkConfig n where n.id in (select c.networkConfigId from DeviceCommand c"
			+ " where c.device.id = :deviceId and c.type = 'APPLY_NETWORK' and c.status = 'BERHASIL') order by n.id desc")
	List<DeviceNetworkConfig> findApplied(@Param("deviceId") Long deviceId);

}
