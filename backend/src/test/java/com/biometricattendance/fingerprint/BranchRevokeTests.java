package com.biometricattendance.fingerprint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.biometricattendance.ingest.RakitanFixtures;
import com.jayway.jsonpath.JsonPath;

/** K-103: pendaftaran dibatasi cabang yang diizinkan; mencabut cabang tambahan melupakan jari di mesin cabang itu. */
class BranchRevokeTests extends RakitanFixtures {

	private ResultActions setExtras(long employeeId, Long... branchIds) throws Exception {
		String ids = String.join(",", java.util.Arrays.stream(branchIds).map(String::valueOf).toList());
		return mockMvc.perform(put("/api/employees/" + employeeId + "/branches").with(HR)
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"extraBranchIds\":[" + ids + "]}"));
	}

	private int fingerRows(long employeeId) {
		return jdbc.queryForObject("select count(*) from employee_fingerprints where employee_id = ?", Integer.class,
				employeeId);
	}

	private List<String> deleteTemplateDevices(long employeeId) {
		return jdbc.queryForList("select device_id::text from device_commands where employee_id = ?"
				+ " and type = 'DELETE_TEMPLATE' order by device_id", String.class, employeeId);
	}

	/** CB-08 */
	@Test
	void enrollOnDisallowedBranchIs400() throws Exception {
		long b1 = createBranch();
		long b2 = createBranch();
		Rakitan other = onlineRakitan(b2);
		long employee = readyEmployee(b1);

		enroll(employee, other.id(), 1).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("Karyawan tidak diizinkan absen di cabang mesin ini."));
	}

	@Test
	void enrollOnExtraBranchIsAllowed() throws Exception {
		long b1 = createBranch();
		long b2 = createBranch();
		Rakitan other = onlineRakitan(b2);
		long employee = readyEmployee(b1);
		setExtras(employee, b2).andExpect(status().isOk());

		enroll(employee, other.id(), 1).andExpect(status().isCreated());
	}

	/** CB-09 */
	@Test
	void revokingBranchForgetsFingersOnItsDevicesOnly() throws Exception {
		long b1 = createBranch();
		long b2 = createBranch();
		Rakitan home = onlineRakitan(b1);
		Rakitan away = onlineRakitan(b2);
		long employee = readyEmployee(b1);
		setExtras(employee, b2).andExpect(status().isOk());
		registerFinger(employee, away, 1);
		registerFinger(employee, home, 2);

		setExtras(employee).andExpect(status().isOk())
			.andExpect(jsonPath("$.removedFingerprints").value(1))
			.andExpect(jsonPath("$.extraBranchIds").isEmpty());

		assertThat(jdbc.queryForList("select finger from employee_fingerprints where employee_id = ?", Integer.class,
				employee)).containsExactly(2);
		assertThat(deleteTemplateDevices(employee)).containsExactly(String.valueOf(away.id()));
		assertThat(jdbc.queryForObject("select finger from device_commands where employee_id = ?"
				+ " and type = 'DELETE_TEMPLATE'", Integer.class, employee)).isEqualTo(1);
		assertThat(deleteCommandFor(employee, home.id())).isNull();
		Map<String, Object> audit = jdbc.queryForMap("select before_data, after_data from audit_logs"
				+ " where action = 'UPDATE' and entity_type = 'EMPLOYEE' and entity_id = ?"
				+ " order by id desc limit 1", String.valueOf(employee));
		String beforeData = audit.get("before_data").toString();
		String afterData = audit.get("after_data").toString();
		assertThat(JsonPath.<List<Integer>>read(beforeData, "$.extraBranchIds")).containsExactly((int) b2);
		assertThat(JsonPath.<List<Integer>>read(afterData, "$.extraBranchIds")).isEmpty();
		assertThat(JsonPath.<Integer>read(afterData, "$.removedFingerprints")).isEqualTo(1);
		assertThat(beforeData).doesNotContain("removedFingerprints");
		assertThat(jdbc.queryForObject("select count(*) from audit_logs where action = 'FINGERPRINT_DELETED'"
				+ " and before_data like ?", Integer.class, "%\"finger\":1%")).isGreaterThanOrEqualTo(1);
	}

	@Test
	void keepingBranchForgetsNothing() throws Exception {
		long b1 = createBranch();
		long b2 = createBranch();
		Rakitan away = onlineRakitan(b2);
		long employee = readyEmployee(b1);
		setExtras(employee, b2).andExpect(status().isOk());
		registerFinger(employee, away, 1);

		setExtras(employee, b2).andExpect(status().isOk()).andExpect(jsonPath("$.removedFingerprints").value(0));

		assertThat(fingerRows(employee)).isEqualTo(1);
		assertThat(deleteTemplateDevices(employee)).isEmpty();
	}

	/** Review Focus 4 */
	@Test
	void revokingBranchCancelsEnrollOnlyThere() throws Exception {
		long b1 = createBranch();
		long b2 = createBranch();
		Rakitan home = onlineRakitan(b1);
		Rakitan away = onlineRakitan(b2);
		long employee = readyEmployee(b1);
		setExtras(employee, b2).andExpect(status().isOk());
		String awayEnroll = enrollOk(employee, away.id(), 1);
		String homeEnroll = enrollOk(employee, home.id(), 2);

		setExtras(employee).andExpect(status().isOk());

		assertThat(commandStatus(awayEnroll)).isEqualTo("DIBATALKAN");
		assertThat(commandStatus(homeEnroll)).isEqualTo("MENUNGGU");
	}

	/** CB-10 (sisi sidik jari) */
	@Test
	void placementChangeDeletesNothing() throws Exception {
		long b1 = createBranch();
		long b2 = createBranch();
		Rakitan home = onlineRakitan(b1);
		long employee = readyEmployee(b1);
		registerFinger(employee, home, 1);
		String enrollId = enrollOk(employee, home.id(), 2);

		String number = jdbc.queryForObject("select employee_number from employees where id = ?", String.class,
				employee);
		String pin = jdbc.queryForObject("select device_pin from employees where id = ?", String.class, employee);
		mockMvc.perform(put("/api/employees/" + employee).with(HR)
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(employeeJson(b2, number, pin)));

		assertThat(jdbc.queryForObject("select branch_id from employees where id = ?", Long.class, employee))
			.isEqualTo(b2);
		assertThat(fingerRows(employee)).isEqualTo(1);
		assertThat(deleteTemplateDevices(employee)).isEmpty();
		assertThat(commandStatus(enrollId)).isEqualTo("MENUNGGU");
	}

}
