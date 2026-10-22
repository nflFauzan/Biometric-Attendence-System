package com.biometricattendance.masterdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.biometricattendance.AttendanceFixtures;
import com.biometricattendance.audit.AuditLog;
import com.biometricattendance.audit.AuditLogRepository;
import com.jayway.jsonpath.JsonPath;

/** K-103: cabang absen tambahan per karyawan. */
class EmployeeBranchAccessTests extends AttendanceFixtures {

	@Autowired
	private AuditLogRepository auditLogs;

	private ResultActions putBranches(long employeeId, String json) throws Exception {
		return mockMvc.perform(put("/api/employees/" + employeeId + "/branches").with(HR)
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(json));
	}

	private ResultActions getBranches(long employeeId) throws Exception {
		return mockMvc.perform(get("/api/employees/" + employeeId + "/branches").with(HR));
	}

	@Test
	void extraBranchesAreSavedAndPlacementIgnored() throws Exception {
		long b1 = createBranch();
		long b2 = createBranch();
		long employee = createEmployee(b1, null);

		putBranches(employee, "{\"extraBranchIds\":[" + b2 + "," + b1 + "," + b2 + "]}").andExpect(status().isOk());

		getBranches(employee).andExpect(status().isOk())
			.andExpect(jsonPath("$.placementBranchId").value(b1))
			.andExpect(jsonPath("$.extraBranchIds.length()").value(1))
			.andExpect(jsonPath("$.extraBranchIds[0]").value(b2))
			.andExpect(jsonPath("$.removedFingerprints").value(0));
	}

	@Test
	void unknownBranchIs404() throws Exception {
		long employee = createEmployee(createBranch(), null);
		putBranches(employee, "{\"extraBranchIds\":[999999999]}").andExpect(status().isNotFound());
	}

	@Test
	void placementChangeKeepsOldPlacementAsExtra() throws Exception {
		long b1 = createBranch();
		long b2 = createBranch();
		long employee = createEmployee(b1, null);

		updateEmployeePin(employee, b2, unique("E"), null).andExpect(status().isOk());
		getBranches(employee).andExpect(jsonPath("$.placementBranchId").value(b2))
			.andExpect(jsonPath("$.extraBranchIds.length()").value(1))
			.andExpect(jsonPath("$.extraBranchIds[0]").value(b1));

		// kembali ke b1: b1 (penempatan baru) hilang dari tambahan, b2 menjadi tambahan
		updateEmployeePin(employee, b1, unique("E"), null).andExpect(status().isOk());
		getBranches(employee).andExpect(jsonPath("$.placementBranchId").value(b1))
			.andExpect(jsonPath("$.extraBranchIds.length()").value(1))
			.andExpect(jsonPath("$.extraBranchIds[0]").value(b2));
	}

	private List<AuditLog> updateLogs(long employee) {
		return auditLogs.findByEntityTypeAndEntityIdOrderByIdAsc("EMPLOYEE", String.valueOf(employee))
			.stream()
			.filter(l -> "UPDATE".equals(l.getAction()))
			.toList();
	}

	private static List<Integer> extraIds(String json) {
		return JsonPath.read(json, "$.extraBranchIds");
	}

	@Test
	void branchChangeIsAudited() throws Exception {
		long b1 = createBranch();
		long b2 = createBranch();
		long employee = createEmployee(b1, null);

		updateEmployeePin(employee, b2, unique("E"), null).andExpect(status().isOk());

		List<AuditLog> logs = updateLogs(employee);
		assertThat(logs).hasSize(1);
		assertThat(extraIds(logs.get(0).getBeforeData())).isEmpty();
		assertThat(extraIds(logs.get(0).getAfterData())).containsExactly((int) b1);
		assertThat(JsonPath.<Integer>read(logs.get(0).getAfterData(), "$.branchId")).isEqualTo((int) b2);
		assertThat(auditLogs.findByEntityTypeAndEntityIdOrderByIdAsc("EMPLOYEE", String.valueOf(employee))
			.stream()
			.filter(l -> "UPDATE_BRANCH_ACCESS".equals(l.getAction()))).isEmpty();
	}

	@Test
	void branchAccessPutIsAudited() throws Exception {
		long b1 = createBranch();
		long b2 = createBranch();
		long employee = createEmployee(b1, null);

		putBranches(employee, "{\"extraBranchIds\":[" + b2 + "]}").andExpect(status().isOk());

		List<AuditLog> logs = updateLogs(employee);
		assertThat(logs).hasSize(1);
		assertThat(extraIds(logs.get(0).getBeforeData())).isEmpty();
		assertThat(extraIds(logs.get(0).getAfterData())).containsExactly((int) b2);
	}

}
