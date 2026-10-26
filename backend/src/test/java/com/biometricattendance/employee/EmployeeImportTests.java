package com.biometricattendance.employee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.biometricattendance.IntegrationTest;
import com.biometricattendance.account.Role;
import com.biometricattendance.account.UserAccount;
import com.biometricattendance.account.UserAccountRepository;
import com.biometricattendance.audit.AuditLogRepository;
import com.jayway.jsonpath.JsonPath;

import tools.jackson.databind.json.JsonMapper;

/**
 * Impor karyawan dari template client (K-130, K-131). Data sintetis; kata pertama nama dibuat unik per test agar
 * usulan username tidak bentrok antar-test.
 */
class EmployeeImportTests extends IntegrationTest {

	private static final RequestPostProcessor OWNER = user("owner-impor").roles("OWNER");

	@Autowired
	private EmployeeRepository employees;

	@Autowired
	private UserAccountRepository accounts;

	@Autowired
	private AuditLogRepository auditLogs;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private JsonMapper jsonMapper;

	@Test
	void previewNormalizesAndSuggestsUsername() throws Exception {
		String branch = createBranch();
		String word = word();
		Object[] row = EmployeeImportParserTests.row(digits(), "Muhammad " + word + " Contoh");
		row[4] = " " + branch.toUpperCase() + " ";
		row[5] = 81234567890.0;
		preview(row).andExpect(status().isOk())
			.andExpect(jsonPath("$.errorCount").value(0))
			.andExpect(jsonPath("$.startDate").exists())
			.andExpect(jsonPath("$.rows[0].username").value(word + "@takka.id"))
			.andExpect(jsonPath("$.rows[0].role").value("EMPLOYEE"))
			.andExpect(jsonPath("$.rows[0].categoryName").value("Kantor"))
			.andExpect(jsonPath("$.rows[0].branchName").value(branch))
			.andExpect(jsonPath("$.rows[0].phone").value("081234567890"))
			.andExpect(jsonPath("$.rows[0].errors").isEmpty());
	}

	@Test
	void previewFlagsExistingEmployeeNumber() throws Exception {
		String branch = createBranch();
		String number = digits();
		List<Map<String, Object>> first = rowsOf(preview(templateRow(number, word() + " Budi", branch)));
		commit("2026-11-01", first).andExpect(status().isOk());

		preview(templateRow(number, word() + " Sari", branch)).andExpect(jsonPath("$.errorCount").value(1))
			.andExpect(jsonPath("$.rows[0].errors[0]").value("ID karyawan " + number + " sudah dipakai"));
	}

	@Test
	void previewFlagsShortInitialPassword() throws Exception {
		String branch = createBranch();
		preview(templateRow("006", "Eni Contoh", branch)).andExpect(jsonPath("$.errorCount").value(1))
			.andExpect(jsonPath("$.rows[0].errors[0]")
				.value("Password awal eni-006 kurang dari 8 karakter; perpanjang username"));
	}

	@Test
	void previewRejectsNonXlsx() throws Exception {
		mockMvc
			.perform(multipart("/api/employee-imports/preview")
				.file(new MockMultipartFile("file", "data.xlsx", "application/octet-stream",
						"bukan excel".getBytes(StandardCharsets.UTF_8)))
				.with(OWNER)
				.with(csrf()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("File bukan Excel .xlsx yang valid"));
	}

	@Test
	void commitCreatesEmployeesAndAccountsAtomically() throws Exception {
		String branch = createBranch();
		String hrWord = word();
		Object[] hr = templateRow(digits(), hrWord + " Harfianti", branch);
		Object[] driver = templateRow(digits(), word() + " Budi", branch);
		driver[2] = "Supir";
		Object[] noAccount = templateRow(digits(), word() + " Tono", branch);
		noAccount[10] = "Belum dibuat";
		List<Map<String, Object>> rows = rowsOf(preview(hr, driver, noAccount));
		rows.get(0).put("role", "HR_FINANCE");

		String body = commit("2026-11-01", rows).andExpect(status().isOk())
			.andExpect(jsonPath("$.employeesCreated").value(3))
			.andExpect(jsonPath("$.accountsCreated").value(2))
			.andReturn()
			.getResponse()
			.getContentAsString();

		String hrPassword = hrWord + "-" + hr[0];
		assertThat(JsonPath.<List<String>>read(body, "$.accounts[*].initialPassword")).contains(hrPassword);
		UserAccount account = accounts.findByUsername(hrWord + "@takka.id").orElseThrow();
		assertThat(account.getRole()).isEqualTo(Role.HR_FINANCE);
		assertThat(account.isMustChangePassword()).isTrue();
		assertThat(passwordEncoder.matches(hrPassword, account.getPasswordHash())).isTrue();
		Employee employee = employees.findById(account.getEmployee().getId()).orElseThrow();
		assertThat(employee.getJoinDate()).hasToString("2026-11-01");
		assertThat(employee.getDevicePin()).isEqualTo(hr[0]);
		assertThat(accounts.findAllWithEmployee()).noneMatch(
				a -> a.getEmployee() != null && a.getEmployee().getEmployeeNumber().equals(noAccount[0]));
		assertThat(auditLogs.findAll()).noneMatch(log -> String.valueOf(log.getAfterData()).contains(hrPassword));
		assertThat(auditLogs.findAll()).anyMatch(log -> "EMPLOYEE_IMPORT".equals(log.getEntityType()));
	}

	@Test
	void importRejectsDuplicateUsernameAndSavesNothing() throws Exception {
		String branch = createBranch();
		String shared = word();
		long before = employees.count();
		List<Map<String, Object>> rows = rowsOf(
				preview(templateRow(digits(), shared + " Satu", branch), templateRow(digits(), shared + " Dua", branch)));

		commit("2026-11-01", rows).andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value("Ada 2 baris bermasalah; periksa ulang di pratinjau"))
			.andExpect(jsonPath("$.rows[0].errors[0]").value("Username " + shared + "@takka.id kembar di file ini"));
		assertThat(employees.count()).isEqualTo(before);
	}

	@Test
	void commitRevalidatesEditedRows() throws Exception {
		String branch = createBranch();
		List<Map<String, Object>> rows = rowsOf(preview(templateRow(digits(), word() + " Budi", branch)));
		rows.get(0).put("branchName", "Cabang Fiktif");
		commit("2026-11-01", rows).andExpect(status().isConflict())
			.andExpect(jsonPath("$.rows[0].errors[0]").value("Cabang 'Cabang Fiktif' tidak ditemukan"));
	}

	private static Object[] templateRow(String number, String name, String branch) {
		Object[] row = EmployeeImportParserTests.row(number, name);
		row[4] = branch;
		return row;
	}

	private ResultActions preview(Object[]... rows) throws Exception {
		byte[] xlsx = EmployeeImportParserTests.xlsx(EmployeeImportParserTests.template(rows));
		return mockMvc.perform(multipart("/api/employee-imports/preview")
			.file(new MockMultipartFile("file", "data.xlsx",
					"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", xlsx))
			.with(OWNER)
			.with(csrf()));
	}

	private ResultActions commit(String startDate, List<Map<String, Object>> rows) throws Exception {
		String body = jsonMapper.writeValueAsString(Map.of("startDate", startDate, "rows", rows));
		return mockMvc.perform(
				post("/api/employee-imports").with(OWNER).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body));
	}

	@SuppressWarnings("unchecked")
	private List<Map<String, Object>> rowsOf(ResultActions preview) throws Exception {
		String body = preview.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		return (List<Map<String, Object>>) jsonMapper.readValue(body, Map.class).get("rows");
	}

	private String createBranch() throws Exception {
		String code = unique("C");
		String name = "Cabang " + code;
		mockMvc
			.perform(post("/api/branches").with(OWNER)
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(String.format(
						"{\"code\":\"%s\",\"name\":\"%s\",\"timezone\":\"Asia/Jakarta\",\"active\":true}", code, name)))
			.andExpect(status().isCreated());
		return name;
	}

	/** Kata huruf kecil unik (diawali huruf), dipakai sebagai kata pertama nama. */
	private static String word() {
		return "u" + unique("").replaceAll("[^a-z0-9]", "");
	}

	/** Nomor karyawan unik yang juga lolos pola PIN mesin (huruf/angka). */
	private static String digits() {
		return unique("").replaceAll("[^a-z0-9]", "");
	}

}
