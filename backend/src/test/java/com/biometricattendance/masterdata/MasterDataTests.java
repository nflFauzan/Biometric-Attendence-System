package com.biometricattendance.masterdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.biometricattendance.IntegrationTest;
import com.biometricattendance.audit.AuditLog;
import com.biometricattendance.audit.AuditLogRepository;
import com.jayway.jsonpath.JsonPath;

class MasterDataTests extends IntegrationTest {

	private static final RequestPostProcessor HR = user("hr-master").roles("HR_FINANCE");

	@Autowired
	private AuditLogRepository auditLogs;

	@Test
	void createBranchIsAuditedWithActor() throws Exception {
		String code = unique("C");
		long id = idOf(createBranch(code, "Asia/Jakarta").andExpect(jsonPath("$.code").value(code.toUpperCase())));

		List<AuditLog> logs = auditLogs.findByEntityTypeAndEntityIdOrderByIdAsc("BRANCH", String.valueOf(id));
		assertThat(logs).hasSize(1);
		assertThat(logs.get(0).getAction()).isEqualTo("CREATE");
		assertThat(logs.get(0).getActorUsername()).isEqualTo("hr-master");
		assertThat(logs.get(0).getBeforeData()).isNull();
		assertThat(logs.get(0).getAfterData()).contains(code.toUpperCase());
	}

	@Test
	void updateBranchRecordsBeforeAndAfter() throws Exception {
		long id = idOf(createBranch(unique("C"), "Asia/Jakarta"));
		mockMvc.perform(put("/api/branches/" + id).with(HR).with(csrf()).contentType(MediaType.APPLICATION_JSON)
			.content(branchJson(unique("C"), "Cabang Baru", "Asia/Makassar")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.timezone").value("Asia/Makassar"));

		List<AuditLog> logs = auditLogs.findByEntityTypeAndEntityIdOrderByIdAsc("BRANCH", String.valueOf(id));
		AuditLog update = logs.get(logs.size() - 1);
		assertThat(update.getAction()).isEqualTo("UPDATE");
		assertThat(update.getBeforeData()).contains("Asia/Jakarta");
		assertThat(update.getAfterData()).contains("Asia/Makassar");
	}

	@Test
	void duplicateBranchCodeIsConflict() throws Exception {
		String code = unique("C");
		createBranch(code, "Asia/Jakarta").andExpect(status().isCreated());
		createBranch(code.toLowerCase(), "Asia/Jakarta").andExpect(status().isConflict());
	}

	@Test
	void unknownTimezoneIsRejected() throws Exception {
		createBranch(unique("C"), "Mars/Olympus").andExpect(status().isBadRequest());
	}

	@Test
	void missingRequiredFieldsReturnFieldErrors() throws Exception {
		mockMvc.perform(post("/api/branches").with(HR).with(csrf()).contentType(MediaType.APPLICATION_JSON)
			.content("{\"timezone\":\"Asia/Jakarta\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.code").value("Kode wajib diisi"))
			.andExpect(jsonPath("$.errors.name").value("Nama wajib diisi"));
	}

	@Test
	void createEmployeeWithBranchAndCategory() throws Exception {
		long branchId = idOf(createBranch(unique("C"), "Asia/Jakarta"));
		long categoryId = idOf(postJson("/api/employee-categories",
				String.format("{\"code\":\"%s\",\"name\":\"Kategori Uji\",\"active\":true}", unique("K"))));
		String number = unique("EMP");

		postJson("/api/employees", employeeJson(number, branchId, categoryId, "2026-01-05", null))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.employeeNumber").value(number))
			.andExpect(jsonPath("$.categoryName").value("Kategori Uji"));

		postJson("/api/employees", employeeJson(number, branchId, categoryId, "2026-01-05", null))
			.andExpect(status().isConflict());
	}

	@Test
	void employeeEndDateCannotBeBeforeJoinDate() throws Exception {
		long branchId = idOf(createBranch(unique("C"), "Asia/Jakarta"));
		long categoryId = idOf(postJson("/api/employee-categories",
				String.format("{\"code\":\"%s\",\"name\":\"K\",\"active\":true}", unique("K"))));
		postJson("/api/employees", employeeJson(unique("EMP"), branchId, categoryId, "2026-03-01", "\"2026-02-01\""))
			.andExpect(status().isBadRequest());
	}

	@Test
	void deviceSerialNumberMustBeUnique() throws Exception {
		long branchId = idOf(createBranch(unique("C"), "Asia/Jakarta"));
		String serial = unique("SN-");
		String body = String.format("{\"name\":\"Mesin Uji\",\"serialNumber\":\"%s\",\"branchId\":%d,\"active\":true}",
				serial, branchId);
		postJson("/api/devices", body).andExpect(status().isCreated()).andExpect(jsonPath("$.branchId").value(branchId));
		postJson("/api/devices", body).andExpect(status().isConflict());
	}

	private ResultActions createBranch(String code, String timezone) throws Exception {
		return postJson("/api/branches", branchJson(code, "Cabang " + code, timezone));
	}

	private ResultActions postJson(String path, String body) throws Exception {
		return mockMvc.perform(post(path).with(HR).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private static long idOf(ResultActions result) throws Exception {
		String body = result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(body, "$.id")).longValue();
	}

	private static String branchJson(String code, String name, String timezone) {
		return String.format("{\"code\":\"%s\",\"name\":\"%s\",\"timezone\":\"%s\",\"active\":true}", code, name,
				timezone);
	}

	private static String employeeJson(String number, long branchId, long categoryId, String joinDate,
			String endDateJson) {
		return String.format("{\"employeeNumber\":\"%s\",\"fullName\":\"Budi Uji\",\"branchId\":%d,\"categoryId\":%d,"
				+ "\"joinDate\":\"%s\",\"endDate\":%s,\"active\":true}", number, branchId, categoryId, joinDate,
				endDateJson == null ? "null" : endDateJson);
	}

}
