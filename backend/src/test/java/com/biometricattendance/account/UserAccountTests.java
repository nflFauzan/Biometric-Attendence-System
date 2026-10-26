package com.biometricattendance.account;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.biometricattendance.IntegrationTest;
import com.biometricattendance.employee.EmployeeRepository;
import com.jayway.jsonpath.JsonPath;

class UserAccountTests extends IntegrationTest {

	private static final RequestPostProcessor OWNER = user("owner-akun").roles("OWNER");
	private static final RequestPostProcessor HR = user("hr-akun").roles("HR_FINANCE");

	@Autowired
	private UserAccountRepository accounts;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private EmployeeRepository employees;

	@Test
	void hrFinanceCanCreateEmployeeAccountButNotOwnerOrHrAccounts() throws Exception {
		long employeeId = createEmployee();
		createUser(HR, unique("kry"), "EMPLOYEE", employeeId).andExpect(status().isCreated())
			.andExpect(jsonPath("$.mustChangePassword").value(true))
			.andExpect(content().string(not(containsString("password"))));
		createUser(HR, unique("own"), "OWNER", null).andExpect(status().isForbidden());
		createUser(HR, unique("hr"), "HR_FINANCE", null).andExpect(status().isForbidden());
	}

	@Test
	void ownerCanCreateHrFinanceAccount() throws Exception {
		createUser(OWNER, unique("hr"), "HR_FINANCE", null).andExpect(status().isCreated())
			.andExpect(jsonPath("$.role").value("HR_FINANCE"));
	}

	@Test
	void employeeAccountMustBeLinkedToOneEmployeeOnly() throws Exception {
		createUser(OWNER, unique("kry"), "EMPLOYEE", null).andExpect(status().isBadRequest());
		long employeeId = createEmployee();
		createUser(OWNER, unique("kry"), "EMPLOYEE", employeeId).andExpect(status().isCreated());
		createUser(OWNER, unique("kry"), "EMPLOYEE", employeeId).andExpect(status().isConflict());
	}

	@Test
	void usernameIsCaseInsensitiveAndUnique() throws Exception {
		String username = unique("hr");
		createUser(OWNER, username.toUpperCase(), "HR_FINANCE", null).andExpect(status().isCreated())
			.andExpect(jsonPath("$.username").value(username.toLowerCase()));
		createUser(OWNER, username, "HR_FINANCE", null).andExpect(status().isConflict());
	}

	@Test
	void usernameMayContainAt() throws Exception {
		String username = unique("budi") + "@takka.id";
		createUser(OWNER, username, "HR_FINANCE", null).andExpect(status().isCreated())
			.andExpect(jsonPath("$.username").value(username));
	}

	@Test
	void hrFinanceOnlySeesEmployeeAccounts() throws Exception {
		createUser(OWNER, unique("hr"), "HR_FINANCE", null).andExpect(status().isCreated());
		mockMvc.perform(get("/api/users").with(HR))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[*].role", everyItem(is("EMPLOYEE"))));
	}

	@Test
	void ownerCannotDeactivateOwnAccount() throws Exception {
		UserAccount self = new UserAccount();
		self.setUsername(unique("owner-"));
		self.setPasswordHash(passwordEncoder.encode("password-awal"));
		self.setRole(Role.OWNER);
		accounts.save(self);

		mockMvc.perform(put("/api/users/" + self.getId()).with(user(self.getUsername()).roles("OWNER")).with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"role\":\"OWNER\",\"active\":false}"))
			.andExpect(status().isBadRequest());
	}

	@Test
	void resetPasswordForcesPasswordChange() throws Exception {
		long employeeId = createEmployee();
		String body = createUser(HR, unique("kry"), "EMPLOYEE", employeeId).andReturn().getResponse()
			.getContentAsString();
		long id = ((Number) JsonPath.read(body, "$.id")).longValue();
		mockMvc.perform(post("/api/users/" + id + "/reset-password").with(HR).with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"newPassword\":\"sementara-123\"}"))
			.andExpect(status().isNoContent());

		UserAccount reset = accounts.findById(id).orElseThrow();
		assertThat(reset.isMustChangePassword()).isTrue();
		assertThat(passwordEncoder.matches("sementara-123", reset.getPasswordHash())).isTrue();
	}

	@Test
	void hrFinanceCannotUpdateOrResetOwnerAndHrAccounts() throws Exception {
		for (Role role : new Role[] { Role.OWNER, Role.HR_FINANCE }) {
			UserAccount target = saveAccount(role, null);
			mockMvc.perform(put("/api/users/" + target.getId()).with(HR).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(String.format("{\"role\":\"%s\",\"active\":false}", role.name())))
				.andExpect(status().isForbidden());
			mockMvc.perform(post("/api/users/" + target.getId() + "/reset-password").with(HR).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"newPassword\":\"sementara-123\"}"))
				.andExpect(status().isForbidden());

			UserAccount unchanged = accounts.findById(target.getId()).orElseThrow();
			assertThat(unchanged.isActive()).isTrue();
			assertThat(passwordEncoder.matches("password-awal", unchanged.getPasswordHash())).isTrue();
		}
	}

	@Test
	void hrFinanceCannotPromoteEmployeeAccount() throws Exception {
		UserAccount target = saveAccount(Role.EMPLOYEE, createEmployee());
		for (String role : new String[] { "HR_FINANCE", "OWNER" }) {
			mockMvc.perform(put("/api/users/" + target.getId()).with(HR).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(String.format("{\"role\":\"%s\",\"active\":true}", role)))
				.andExpect(status().isForbidden());
		}
		assertThat(accounts.findById(target.getId()).orElseThrow().getRole()).isEqualTo(Role.EMPLOYEE);
	}

	@Test
	void hrFinanceCanDeactivateEmployeeAccount() throws Exception {
		long employeeId = createEmployee();
		UserAccount target = saveAccount(Role.EMPLOYEE, employeeId);
		mockMvc.perform(put("/api/users/" + target.getId()).with(HR).with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(String.format("{\"role\":\"EMPLOYEE\",\"employeeId\":%d,\"active\":false}", employeeId)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.active").value(false));
	}

	@Test
	void ownerCannotChangeOwnRole() throws Exception {
		UserAccount self = saveAccount(Role.OWNER, null);
		mockMvc.perform(put("/api/users/" + self.getId()).with(user(self.getUsername()).roles("OWNER")).with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"role\":\"HR_FINANCE\",\"active\":true}"))
			.andExpect(status().isBadRequest());
	}

	@Test
	void ownerCanChangeHrAccountToEmployee() throws Exception {
		long employeeId = createEmployee();
		UserAccount target = saveAccount(Role.HR_FINANCE, null);
		mockMvc.perform(put("/api/users/" + target.getId()).with(OWNER).with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(String.format("{\"role\":\"EMPLOYEE\",\"employeeId\":%d,\"active\":true}", employeeId)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.role").value("EMPLOYEE"))
			.andExpect(jsonPath("$.employeeId").value(employeeId));
	}

	private UserAccount saveAccount(Role role, Long employeeId) {
		UserAccount account = new UserAccount();
		account.setUsername(unique("akun-"));
		account.setPasswordHash(passwordEncoder.encode("password-awal"));
		account.setRole(role);
		if (employeeId != null) {
			account.setEmployee(employees.getReferenceById(employeeId));
		}
		return accounts.save(account);
	}

	private ResultActions createUser(RequestPostProcessor actor, String username, String role, Long employeeId)
			throws Exception {
		String body = String.format("{\"username\":\"%s\",\"password\":\"password-awal\",\"role\":\"%s\",\"employeeId\":%s}",
				username, role, employeeId == null ? "null" : employeeId.toString());
		return mockMvc.perform(post("/api/users").with(actor).with(csrf()).contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	private long createEmployee() throws Exception {
		long branchId = idOf(post("/api/branches"),
				String.format("{\"code\":\"%s\",\"name\":\"C\",\"timezone\":\"Asia/Jakarta\",\"active\":true}",
						unique("C")));
		long categoryId = idOf(post("/api/employee-categories"),
				String.format("{\"code\":\"%s\",\"name\":\"K\",\"active\":true}", unique("K")));
		return idOf(post("/api/employees"), String.format(
				"{\"employeeNumber\":\"%s\",\"fullName\":\"Siti\",\"branchId\":%d,\"categoryId\":%d,\"joinDate\":\"2026-01-05\",\"active\":true}",
				unique("EMP"), branchId, categoryId));
	}

	private long idOf(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, String body)
			throws Exception {
		String response = mockMvc.perform(request.with(OWNER).with(csrf()).contentType(MediaType.APPLICATION_JSON)
			.content(body)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(response, "$.id")).longValue();
	}

}
