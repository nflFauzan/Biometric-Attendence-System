package com.biometricattendance.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.biometricattendance.device.Device;
import com.biometricattendance.device.DeviceRepository;
import com.biometricattendance.devicecommand.DeviceCommand;
import com.biometricattendance.devicecommand.DeviceCommandChangedEvent;
import com.biometricattendance.devicecommand.DeviceCommandService;
import com.biometricattendance.employee.Employee;
import com.biometricattendance.employee.EmployeeRepository;
import com.jayway.jsonpath.JsonPath;

/**
 * Sisi mesin antrean perintah (spec §4, §16): heartbeat membawa ENROLL dan daftar batal, laporan langkah, dan hasil.
 * Template sidik jari di body hasil tidak pernah disimpan (Fokus review #2).
 */
@RecordApplicationEvents
class RakitanCommandTests extends RakitanFixtures {

	private static final String TEMPLATE = "VEVNUExBVEUtUkFIQVNJQQ";

	@Autowired
	private ApplicationEvents events;

	@Autowired
	private DeviceCommandService commandService;

	@Autowired
	private DeviceRepository devices;

	@Autowired
	private EmployeeRepository employees;

	@Autowired
	private PlatformTransactionManager transactionManager;

	/** Satu mesin online dan satu karyawan siap dengan perintah ENROLL jari 1 yang baru dibuat. */
	private record Setup(Rakitan rakitan, long employee, String commandId) {
	}

	private Setup setup() throws Exception {
		long branch = createBranch();
		Rakitan rakitan = onlineRakitan(branch);
		long employee = readyEmployee(branch);
		return new Setup(rakitan, employee, enrollOk(employee, rakitan.id(), 1));
	}

	/** Perintah yang sudah diambil mesin (DIKIRIM). */
	private Setup sent() throws Exception {
		Setup s = setup();
		heartbeat(s.rakitan().key(), "{}").andExpect(status().isOk());
		return s;
	}

	// --- Pengiriman lewat heartbeat ---

	@Test
	void heartbeatDeliversEnrollAndMarksSent() throws Exception {
		Setup s = setup();

		heartbeat(s.rakitan().key(), "{}").andExpect(status().isOk())
			.andExpect(jsonPath("$.commands[0].id").value(s.commandId()))
			.andExpect(jsonPath("$.commands[0].type").value("ENROLL"))
			.andExpect(jsonPath("$.commands[0].pin").value(pinOf(s.employee())))
			.andExpect(jsonPath("$.commands[0].finger").value(1))
			.andExpect(jsonPath("$.commands[0].displayName").value(nameOf(s.employee())))
			.andExpect(jsonPath("$.commands[0].timeoutSeconds").value(60))
			.andExpect(jsonPath("$.cancel").isEmpty());

		assertThat(statusOf(s.commandId())).isEqualTo("DIKIRIM");
	}

	@Test
	void sentCommandIsRedeliveredUntilFinished() throws Exception {
		Setup s = sent();
		heartbeat(s.rakitan().key(), "{}").andExpect(jsonPath("$.commands[0].id").value(s.commandId()));
	}

	@Test
	void otherDevicesDoNotSeeCommand() throws Exception {
		setup();
		heartbeat(onlineRakitan(createBranch()).key(), "{}").andExpect(jsonPath("$.commands").isEmpty());
	}

	@Test
	void expiredCommandIsNotDelivered() throws Exception {
		Setup s = setup();
		jdbc.update("update device_commands set created_at = now() - interval '31 seconds' where id = ?::uuid",
				s.commandId());
		heartbeat(s.rakitan().key(), "{}").andExpect(jsonPath("$.commands").isEmpty());
	}

	/** Final review I1: heartbeat hanya menulis perintah milik mesinnya sendiri; kedaluwarsa urusan sweeper. */
	@Test
	void heartbeatDoesNotWriteOtherDevicesCommands() throws Exception {
		Setup s = setup();
		jdbc.update("update device_commands set created_at = now() - interval '31 seconds' where id = ?::uuid",
				s.commandId());

		heartbeat(onlineRakitan(createBranch()).key(), "{}").andExpect(status().isOk());

		assertThat(statusOf(s.commandId())).isEqualTo("MENUNGGU");
	}

	@Test
	void cancelledSentCommandAppearsInCancelList() throws Exception {
		Setup s = sent();
		cancel(s.commandId()).andExpect(status().isOk());

		heartbeat(s.rakitan().key(), "{}").andExpect(jsonPath("$.commands").isEmpty())
			.andExpect(jsonPath("$.cancel[0]").value(s.commandId()));
	}

	// --- Progress ---

	@Test
	void progressUpdatesStep() throws Exception {
		Setup s = sent();
		progress(s.rakitan().key(), s.commandId(), 2).andExpect(status().isOk());
		command(s.commandId()).andExpect(jsonPath("$.message").value("Tempelkan jari (2/3)"));
	}

	@Test
	void progressValidation() throws Exception {
		Setup s = sent();
		String key = s.rakitan().key();
		expectError(progress(key, s.commandId(), 0), 400, "FORMAT_SALAH");
		expectError(progress(key, s.commandId(), 4), 400, "FORMAT_SALAH");
		expectError(progress(onlineRakitan(createBranch()).key(), s.commandId(), 1), 404, "PERINTAH_TIDAK_DIKENAL");
		expectError(progress(key, "bukan-uuid", 1), 404, "PERINTAH_TIDAK_DIKENAL");
		cancel(s.commandId()).andExpect(status().isOk());
		expectError(progress(key, s.commandId(), 1), 409, "PERINTAH_TIDAK_AKTIF");
	}

	@Test
	void progressAndResultRejectWrongKey() throws Exception {
		Setup s = sent();
		expectError(progress("salah", s.commandId(), 1), 401, "KUNCI_DITOLAK");
		expectError(result("salah", s.commandId(), "{\"status\":\"BERHASIL\"}"), 401, "KUNCI_DITOLAK");

		expectUntouched(s);
	}

	@Test
	void progressAndResultRejectInactiveDevice() throws Exception {
		Setup s = sent();
		long branch = jdbc.queryForObject("select branch_id from employees where id = ?", Long.class, s.employee());
		deactivate(s.rakitan(), branch);

		expectError(progress(s.rakitan().key(), s.commandId(), 1), 403, "MESIN_NONAKTIF");
		expectError(result(s.rakitan().key(), s.commandId(), "{\"status\":\"BERHASIL\"}"), 403, "MESIN_NONAKTIF");

		expectUntouched(s);
	}

	/** Laporan yang ditolak tidak boleh mengubah perintah atau mencatat jari. */
	private void expectUntouched(Setup s) throws Exception {
		command(s.commandId()).andExpect(jsonPath("$.status").value("DIKIRIM"))
			.andExpect(jsonPath("$.message").value("Menunggu mesin…"));
		assertThat(fingerprintRows(s.employee())).isZero();
	}

	// --- Perintah hapus ---

	@Test
	void heartbeatDeliversDeleteCommands() throws Exception {
		long branch = createBranch();
		Rakitan rakitan = onlineRakitan(branch);
		long employee = readyEmployee(branch);
		String template = queueDelete(rakitan, employee, 2);
		String all = queueDelete(rakitan, employee, null);

		heartbeat(rakitan.key(), "{}").andExpect(status().isOk())
			.andExpect(jsonPath("$.commands[0].id").value(template))
			.andExpect(jsonPath("$.commands[0].type").value("DELETE_TEMPLATE"))
			.andExpect(jsonPath("$.commands[0].pin").value(pinOf(employee)))
			.andExpect(jsonPath("$.commands[0].finger").value(2))
			.andExpect(jsonPath("$.commands[0].displayName").doesNotExist())
			.andExpect(jsonPath("$.commands[1].id").value(all))
			.andExpect(jsonPath("$.commands[1].type").value("DELETE_EMPLOYEE"))
			.andExpect(jsonPath("$.commands[1].finger").doesNotExist());
		assertThat(statusOf(template)).isEqualTo("DIKIRIM");
	}

	@Test
	void oldDeleteIsStillDelivered() throws Exception {
		long branch = createBranch();
		Rakitan rakitan = onlineRakitan(branch);
		String id = queueDelete(rakitan, readyEmployee(branch), 1);
		jdbc.update("update device_commands set created_at = now() - interval '1 hour' where id = ?::uuid", id);

		heartbeat(rakitan.key(), "{}").andExpect(status().isOk())
			.andExpect(jsonPath("$.commands[0].id").value(id));
	}

	@Test
	void progressOnDeleteIs409() throws Exception {
		long branch = createBranch();
		Rakitan rakitan = onlineRakitan(branch);
		String id = queueDelete(rakitan, readyEmployee(branch), 1);
		heartbeat(rakitan.key(), "{}").andExpect(status().isOk());

		expectError(progress(rakitan.key(), id, 1), 409, "PERINTAH_TIDAK_AKTIF");
	}

	@Test
	void deleteResultDoesNotRecordFingerprint() throws Exception {
		long branch = createBranch();
		Rakitan rakitan = onlineRakitan(branch);
		long employee = readyEmployee(branch);
		String id = queueDelete(rakitan, employee, 1);
		heartbeat(rakitan.key(), "{}").andExpect(status().isOk());

		result(rakitan.key(), id, "{\"status\":\"BERHASIL\"}").andExpect(status().isOk());

		assertThat(statusOf(id)).isEqualTo("BERHASIL");
		assertThat(fingerprintRows(employee)).isZero();
		command(id).andExpect(jsonPath("$.message").value("Terhapus"));
	}

	@Test
	void cancelDeleteIs409() throws Exception {
		long branch = createBranch();
		Rakitan rakitan = onlineRakitan(branch);
		String id = queueDelete(rakitan, readyEmployee(branch), 1);

		cancel(id).andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value("Perintah penghapusan tidak bisa dibatalkan."));
		assertThat(statusOf(id)).isEqualTo("MENUNGGU");
	}

	/** Perintah hapus jari tertentu, atau seluruh karyawan jika {@code finger} null. */
	private String queueDelete(Rakitan rakitan, long employeeId, Integer finger) {
		return new TransactionTemplate(transactionManager).execute(s -> {
			Device device = devices.findById(rakitan.id()).orElseThrow();
			Employee employee = employees.findById(employeeId).orElseThrow();
			DeviceCommand command = finger == null
					? DeviceCommand.deleteEmployee(device, employee, employee.getDevicePin(), "owner-absensi", Instant.now())
					: DeviceCommand.deleteTemplate(device, employee, employee.getDevicePin(), finger, "owner-absensi",
							Instant.now());
			commandService.create(command, "owner-absensi");
			return command.getId().toString();
		});
	}

	// --- Hasil ---

	@Test
	void successRecordsFingerprint() throws Exception {
		Setup s = sent();
		result(s.rakitan().key(), s.commandId(), "{\"status\":\"BERHASIL\"}").andExpect(status().isOk());

		overview(s.employee()).andExpect(jsonPath("$.fingers[0].finger").value(1))
			.andExpect(jsonPath("$.fingers[0].deviceId").value(s.rakitan().id()))
			.andExpect(jsonPath("$.fingers[0].deviceName").value(s.rakitan().name()))
			.andExpect(jsonPath("$.activeCommands").isEmpty());
		command(s.commandId()).andExpect(jsonPath("$.status").value("BERHASIL"))
			.andExpect(jsonPath("$.message").value("Berhasil"));
		assertThat(jdbc.queryForObject(
				"select actor_username from audit_logs where action = 'COMMAND_RESULT' and entity_id = ?",
				String.class, s.commandId()))
			.isEqualTo("mesin " + s.rakitan().deviceId());
		assertThat(jdbc.queryForList(
				"select actor_username, before_data from audit_logs where action = 'FINGERPRINT_ENROLLED'"
						+ " and after_data like ?",
				"%\"commandId\":\"" + s.commandId() + "\"%"))
			.singleElement()
			.satisfies(row -> {
				assertThat(row.get("actor_username")).isEqualTo("mesin " + s.rakitan().deviceId());
				assertThat(row.get("before_data")).isNull();
			});
	}

	@Test
	void reEnrollIsAuditedWithBeforeAndAfter() throws Exception {
		Setup s = sent();
		result(s.rakitan().key(), s.commandId(), "{\"status\":\"BERHASIL\"}").andExpect(status().isOk());
		long branch = jdbc.queryForObject("select branch_id from employees where id = ?", Long.class, s.employee());
		Rakitan other = onlineRakitan(branch);
		String second = enrollOk(s.employee(), other.id(), 1);
		heartbeat(other.key(), "{}").andExpect(status().isOk());
		result(other.key(), second, "{\"status\":\"BERHASIL\"}").andExpect(status().isOk());

		Long fingerprint = jdbc.queryForObject("select id from employee_fingerprints where employee_id = ?",
				Long.class, s.employee());
		Map<String, Object> row = jdbc.queryForMap(
				"select actor_username, before_data, after_data from audit_logs where action = 'FINGERPRINT_REENROLLED'"
						+ " and entity_type = 'EmployeeFingerprint' and entity_id = ?",
				String.valueOf(fingerprint));
		assertThat(row.get("actor_username")).isEqualTo("mesin " + other.deviceId());
		Map<String, Object> before = JsonPath.read((String) row.get("before_data"), "$");
		Map<String, Object> after = JsonPath.read((String) row.get("after_data"), "$");
		assertThat(((Number) before.get("deviceId")).longValue()).isEqualTo(s.rakitan().id());
		assertThat(((Number) after.get("deviceId")).longValue()).isEqualTo(other.id());
		assertThat(after).containsOnlyKeys("finger", "deviceId", "commandId", "enrolledAt")
			.containsEntry("commandId", second);
	}

	@Test
	void reEnrollReplacesRecord() throws Exception {
		Setup s = sent();
		result(s.rakitan().key(), s.commandId(), "{\"status\":\"BERHASIL\"}").andExpect(status().isOk());

		long branch = jdbc.queryForObject("select branch_id from employees where id = ?", Long.class, s.employee());
		Rakitan other = onlineRakitan(branch);
		String second = enrollOk(s.employee(), other.id(), 1);
		heartbeat(other.key(), "{}").andExpect(status().isOk());
		result(other.key(), second, "{\"status\":\"BERHASIL\"}").andExpect(status().isOk());

		assertThat(fingerprintRows(s.employee())).isEqualTo(1);
		overview(s.employee()).andExpect(jsonPath("$.fingers[0].deviceId").value(other.id()));
	}

	@Test
	void duplicateResultIsIgnored() throws Exception {
		Setup s = sent();
		result(s.rakitan().key(), s.commandId(), "{\"status\":\"BERHASIL\"}").andExpect(status().isOk());
		result(s.rakitan().key(), s.commandId(), "{\"status\":\"BERHASIL\"}").andExpect(status().isOk());

		assertThat(fingerprintRows(s.employee())).isEqualTo(1);
		assertThat(jdbc.queryForObject(
				"select count(*) from audit_logs where action = 'COMMAND_RESULT' and entity_id = ?", Integer.class,
				s.commandId()))
			.isEqualTo(1);
	}

	@Test
	void resultAfterCancelIsIgnored() throws Exception {
		Setup s = sent();
		cancel(s.commandId()).andExpect(status().isOk());

		result(s.rakitan().key(), s.commandId(), "{\"status\":\"BERHASIL\"}").andExpect(status().isOk());

		assertThat(statusOf(s.commandId())).isEqualTo("DIBATALKAN");
		overview(s.employee()).andExpect(jsonPath("$.fingers").isEmpty());
	}

	@Test
	void failureCarriesMessage() throws Exception {
		Setup s = sent();
		result(s.rakitan().key(), s.commandId(), "{\"status\":\"GAGAL\",\"error\":\"KUALITAS_BURUK\"}")
			.andExpect(status().isOk());

		command(s.commandId()).andExpect(jsonPath("$.status").value("GAGAL"))
			.andExpect(jsonPath("$.errorCode").value("KUALITAS_BURUK"))
			.andExpect(jsonPath("$.message")
				.value("Sidik jari tidak terbaca jelas. Bersihkan jari atau sensor, lalu ulangi."));
	}

	@Test
	void duplicateFingerNamesOwner() throws Exception {
		Setup s = sent();
		long branch = jdbc.queryForObject("select branch_id from employees where id = ?", Long.class, s.employee());
		long owner = createEmployee(branch, unique("P"));
		result(s.rakitan().key(), s.commandId(),
				"{\"status\":\"GAGAL\",\"error\":\"JARI_SUDAH_TERDAFTAR\",\"pin\":\"" + pinOf(owner) + "\"}")
			.andExpect(status().isOk());
		command(s.commandId())
			.andExpect(jsonPath("$.message").value("Sidik jari ini sudah terdaftar atas nama " + nameOf(owner) + "."));

		Setup t = sent();
		result(t.rakitan().key(), t.commandId(),
				"{\"status\":\"GAGAL\",\"error\":\"JARI_SUDAH_TERDAFTAR\",\"pin\":\"7777\"}")
			.andExpect(status().isOk());
		command(t.commandId()).andExpect(jsonPath("$.message").value("Sidik jari ini sudah terdaftar atas nama PIN 7777."));
	}

	@Test
	void badResultStatusIs400() throws Exception {
		Setup s = sent();
		expectError(result(s.rakitan().key(), s.commandId(), "{\"status\":\"SELESAI\"}"), 400, "FORMAT_SALAH");
		expectError(result(s.rakitan().key(), s.commandId(), "{\"status\":\"GAGAL\"}"), 400, "FORMAT_SALAH");
		assertThat(statusOf(s.commandId())).isEqualTo("DIKIRIM");
	}

	@Test
	void templateInResultIsNeverStored() throws Exception {
		Setup s = sent();
		result(s.rakitan().key(), s.commandId(), "{\"status\":\"BERHASIL\",\"template\":\"" + TEMPLATE + "\"}")
			.andExpect(status().isOk());

		assertThat(jdbc.queryForObject("select count(*) from audit_logs a where a::text like ?", Integer.class,
				"%" + TEMPLATE + "%"))
			.isZero();
		assertThat(jdbc.queryForObject("select count(*) from device_commands c where c::text like ?", Integer.class,
				"%" + TEMPLATE + "%"))
			.isZero();
		assertThat(jdbc.queryForObject("select count(*) from employee_fingerprints f where f::text like ?",
				Integer.class, "%" + TEMPLATE + "%"))
			.isZero();
	}

	@Test
	void transitionsPublishChangedEvent() throws Exception {
		Setup s = setup();
		events.clear();

		heartbeat(s.rakitan().key(), "{}").andExpect(status().isOk());
		assertThat(changedEvents(s.commandId())).isEqualTo(1);
		heartbeat(s.rakitan().key(), "{}").andExpect(status().isOk());
		assertThat(changedEvents(s.commandId())).as("kirim ulang tidak mengubah status").isEqualTo(1);
		progress(s.rakitan().key(), s.commandId(), 1).andExpect(status().isOk());
		assertThat(changedEvents(s.commandId())).isEqualTo(2);
		result(s.rakitan().key(), s.commandId(), "{\"status\":\"BERHASIL\"}").andExpect(status().isOk());
		assertThat(changedEvents(s.commandId())).isEqualTo(3);
	}

	private long changedEvents(String commandId) {
		UUID id = UUID.fromString(commandId);
		return events.stream(DeviceCommandChangedEvent.class).filter(e -> e.commandId().equals(id)).count();
	}

	private ResultActions cancel(String id) throws Exception {
		return mockMvc.perform(post("/api/device-commands/" + id + "/cancel").with(HR).with(csrf()));
	}

	private ResultActions command(String id) throws Exception {
		return mockMvc.perform(get("/api/device-commands/" + id).with(HR)).andExpect(status().isOk());
	}

	private ResultActions overview(long employee) throws Exception {
		return mockMvc.perform(get("/api/employees/" + employee + "/fingerprints").with(HR))
			.andExpect(status().isOk());
	}

	private static void expectError(ResultActions result, int status, String code) throws Exception {
		result.andExpect(status().is(status)).andExpect(jsonPath("$.error").value(code));
	}

	private String statusOf(String commandId) {
		return jdbc.queryForObject("select status from device_commands where id = ?::uuid", String.class, commandId);
	}

	private String pinOf(long employee) {
		return jdbc.queryForObject("select device_pin from employees where id = ?", String.class, employee);
	}

	private String nameOf(long employee) {
		return jdbc.queryForObject("select full_name from employees where id = ?", String.class, employee);
	}

	private int fingerprintRows(long employee) {
		return jdbc.queryForObject("select count(*) from employee_fingerprints where employee_id = ?", Integer.class,
				employee);
	}

}
