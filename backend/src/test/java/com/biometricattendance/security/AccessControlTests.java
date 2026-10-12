package com.biometricattendance.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.biometricattendance.IntegrationTest;
import com.biometricattendance.common.ProblemResponses;

/** Matrix hak akses sesuai docs/kebutuhan.md: Owner, HR/Finance, Karyawan, dan pengguna anonim. */
class AccessControlTests extends IntegrationTest {

	private static final RequestPostProcessor OWNER = user("owner-test").roles("OWNER");
	private static final RequestPostProcessor HR_FINANCE = user("hr-test").roles("HR_FINANCE");
	private static final RequestPostProcessor EMPLOYEE = user("karyawan-test").roles("EMPLOYEE");

	@ParameterizedTest
	@ValueSource(strings = { "/api/branches", "/api/employee-categories", "/api/employees", "/api/devices",
			"/api/users" })
	void masterDataReadableByOwnerAndHrFinanceOnly(String path) throws Exception {
		mockMvc.perform(get(path).with(OWNER)).andExpect(status().isOk());
		mockMvc.perform(get(path).with(HR_FINANCE)).andExpect(status().isOk());
		mockMvc.perform(get(path).with(EMPLOYEE)).andExpect(status().isForbidden());
		mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
	}

	@ParameterizedTest
	@ValueSource(strings = { "/api/branches", "/api/employee-categories", "/api/employees", "/api/devices",
			"/api/users" })
	void employeeCannotWriteMasterData(String path) throws Exception {
		mockMvc.perform(post(path).with(EMPLOYEE).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isForbidden());
	}

	@ParameterizedTest
	@ValueSource(strings = { "/api/branches", "/api/employees", "/api/users" })
	void writeWithoutCsrfTokenIsRejected(String path) throws Exception {
		mockMvc.perform(post(path).with(OWNER).contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isForbidden());
	}

	@Test
	void auditLogIsOwnerOnly() throws Exception {
		mockMvc.perform(get("/api/audit-logs").with(OWNER)).andExpect(status().isOk());
		mockMvc.perform(get("/api/audit-logs").with(HR_FINANCE)).andExpect(status().isForbidden());
		mockMvc.perform(get("/api/audit-logs").with(EMPLOYEE)).andExpect(status().isForbidden());
		mockMvc.perform(get("/api/audit-logs")).andExpect(status().isUnauthorized());
	}

	/** ID yang tidak ada: role yang berhak mendapat 404 (lolos otorisasi), sisanya 401/403. */
	private static final long MISSING = 999_999_999L;

	private static final String BRANCH = "{\"code\":\"X1\",\"name\":\"X\",\"timezone\":\"Asia/Jakarta\",\"active\":true}";
	private static final String CATEGORY = "{\"code\":\"X1\",\"name\":\"X\",\"active\":true}";
	private static final String EMPLOYEE_BODY = "{\"employeeNumber\":\"X1\",\"fullName\":\"X\",\"branchId\":1,"
			+ "\"categoryId\":1,\"joinDate\":\"2026-01-05\",\"active\":true}";
	private static final String DEVICE = "{\"name\":\"X\",\"serialNumber\":\"X1\",\"branchId\":1,\"active\":true}";
	private static final String USER_UPDATE = "{\"role\":\"EMPLOYEE\",\"employeeId\":1,\"active\":true}";
	private static final String RESET = "{\"newPassword\":\"sementara-123\"}";

	/** Semua endpoint master data dan akun yang hanya untuk Owner dan HR/Finance (docs/kebutuhan.md §2.2). */
	static Stream<Arguments> managerEndpoints() {
		return Stream.of(
				Arguments.of(HttpMethod.GET, "/api/branches/" + MISSING, null),
				Arguments.of(HttpMethod.PUT, "/api/branches/" + MISSING, BRANCH),
				Arguments.of(HttpMethod.PUT, "/api/employee-categories/" + MISSING, CATEGORY),
				Arguments.of(HttpMethod.GET, "/api/employees/" + MISSING, null),
				Arguments.of(HttpMethod.PUT, "/api/employees/" + MISSING, EMPLOYEE_BODY),
				Arguments.of(HttpMethod.PUT, "/api/devices/" + MISSING, DEVICE),
				Arguments.of(HttpMethod.PUT, "/api/users/" + MISSING, USER_UPDATE),
				Arguments.of(HttpMethod.POST, "/api/users/" + MISSING + "/reset-password", RESET));
	}

	@ParameterizedTest(name = "{0} {1}")
	@MethodSource("managerEndpoints")
	void managerEndpointMatrix(HttpMethod method, String url, String body) throws Exception {
		mockMvc.perform(build(method, url, body).with(OWNER)).andExpect(status().isNotFound());
		mockMvc.perform(build(method, url, body).with(HR_FINANCE)).andExpect(status().isNotFound());
		mockMvc.perform(build(method, url, body).with(EMPLOYEE))
			.andExpect(status().isForbidden())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.detail").value(ProblemResponses.FORBIDDEN));
		mockMvc.perform(build(method, url, body))
			.andExpect(status().isUnauthorized())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.detail").value(ProblemResponses.UNAUTHORIZED));
	}

	private static final String WORK_RULE = "{\"scope\":\"GLOBAL\",\"effectiveFrom\":\"2099-01-01\","
			+ "\"workStart\":\"08:00\"}";
	private static final String HOLIDAY = "{\"date\":\"2099-01-01\",\"name\":\"X\"}";
	private static final String PREVIEW = "{\"employeeId\":" + MISSING + ",\"date\":\"2026-10-12\","
			+ "\"scanTimes\":[\"08:00\",\"17:00\"]}";

	/** Fase 2: kalender libur, simulasi perhitungan, dan baca aturan untuk Owner dan HR/Finance (K-049). */
	static Stream<Arguments> phase2ManagerEndpoints() {
		return Stream.of(
				Arguments.of(HttpMethod.GET, "/api/work-rules/effective?employeeId=" + MISSING + "&date=2026-10-12",
						null),
				Arguments.of(HttpMethod.PUT, "/api/holidays/" + MISSING, HOLIDAY),
				Arguments.of(HttpMethod.DELETE, "/api/holidays/" + MISSING, null),
				Arguments.of(HttpMethod.POST, "/api/calculation/daily-preview", PREVIEW));
	}

	@ParameterizedTest(name = "{0} {1}")
	@MethodSource("phase2ManagerEndpoints")
	void phase2ManagerEndpointMatrix(HttpMethod method, String url, String body) throws Exception {
		managerEndpointMatrix(method, url, body);
	}

	@ParameterizedTest
	@ValueSource(strings = { "/api/work-rules?scope=GLOBAL", "/api/holidays?year=2026" })
	void phase2ListsReadableByOwnerAndHrFinanceOnly(String url) throws Exception {
		mockMvc.perform(get(url).with(OWNER)).andExpect(status().isOk());
		mockMvc.perform(get(url).with(HR_FINANCE)).andExpect(status().isOk());
		mockMvc.perform(get(url).with(EMPLOYEE)).andExpect(status().isForbidden());
		mockMvc.perform(get(url)).andExpect(status().isUnauthorized());
	}

	private static final String CORRECTION = "{\"employeeId\":" + MISSING + ",\"date\":\"2026-10-05\","
			+ "\"checkIn\":\"08:00\",\"reason\":\"Uji hak akses\"}";

	/** Fase 3: rekap, koreksi (K-059), dan dashboard hanya untuk Owner dan HR/Finance. */
	static Stream<Arguments> phase3ManagerEndpoints() {
		return Stream.of(Arguments.of(HttpMethod.GET, "/api/attendance/employees/" + MISSING, null),
				Arguments.of(HttpMethod.POST, "/api/attendance/corrections", CORRECTION));
	}

	@ParameterizedTest(name = "{0} {1}")
	@MethodSource("phase3ManagerEndpoints")
	void phase3ManagerEndpointMatrix(HttpMethod method, String url, String body) throws Exception {
		managerEndpointMatrix(method, url, body);
	}

	@ParameterizedTest
	@ValueSource(strings = { "/api/attendance/daily?date=2026-10-05", "/api/attendance/unmatched-scans",
			"/api/dashboard/summary?date=2026-10-05", "/api/devices/unregistered-attempts" })
	void phase3ReadsForOwnerAndHrFinanceOnly(String url) throws Exception {
		mockMvc.perform(get(url).with(OWNER)).andExpect(status().isOk());
		mockMvc.perform(get(url).with(HR_FINANCE)).andExpect(status().isOk());
		mockMvc.perform(get(url).with(EMPLOYEE)).andExpect(status().isForbidden());
		mockMvc.perform(get(url)).andExpect(status().isUnauthorized());
	}

	@Test
	void dashboardStreamRequiresManagerRole() throws Exception {
		mockMvc.perform(get("/api/dashboard/stream").with(EMPLOYEE)).andExpect(status().isForbidden());
		mockMvc.perform(get("/api/dashboard/stream")).andExpect(status().isUnauthorized());
	}

	/**
	 * Endpoint mesin tanpa login dan tanpa CSRF (K-055): yang memutuskan adalah nomor seri terdaftar, jadi mesin
	 * yang tidak dikenal mendapat 401 dari adapter, bukan 401/403 dari login atau CSRF.
	 */
	@Test
	void deviceEndpointsAreOpenButRejectUnknownSerialNumbers() throws Exception {
		mockMvc.perform(post("/iclock/cdata").param("SN", "TIDAK-TERDAFTAR").param("table", "ATTLOG").content("1"))
			.andExpect(status().isUnauthorized())
			.andExpect(content().string("UNKNOWN DEVICE"));
		mockMvc.perform(get("/iclock/cdata").param("SN", "TIDAK-TERDAFTAR"))
			.andExpect(content().string("UNKNOWN DEVICE"));
		mockMvc.perform(get("/iclock/getrequest").param("SN", "TIDAK-TERDAFTAR"))
			.andExpect(content().string("UNKNOWN DEVICE"));
		mockMvc.perform(post("/iclock/devicecmd").param("SN", "TIDAK-TERDAFTAR"))
			.andExpect(content().string("UNKNOWN DEVICE"));
	}

	/** Mengubah aturan kerja dan tarif hanya Owner (K-004, K-049). */
	static Stream<Arguments> ownerOnlyRuleEndpoints() {
		return Stream.of(Arguments.of(HttpMethod.POST, "/api/work-rules", "{}"),
				Arguments.of(HttpMethod.PUT, "/api/work-rules/" + MISSING, WORK_RULE),
				Arguments.of(HttpMethod.DELETE, "/api/work-rules/" + MISSING, null));
	}

	@ParameterizedTest(name = "{0} {1}")
	@MethodSource("ownerOnlyRuleEndpoints")
	void workRulesCanOnlyBeChangedByOwner(HttpMethod method, String url, String body) throws Exception {
		// Owner lolos otorisasi: 400 (body kosong) atau 404 (ID tidak ada).
		mockMvc.perform(build(method, url, body).with(OWNER))
			.andExpect(result -> assertThat(result.getResponse().getStatus()).isIn(400, 404));
		mockMvc.perform(build(method, url, body).with(HR_FINANCE)).andExpect(status().isForbidden());
		mockMvc.perform(build(method, url, body).with(EMPLOYEE)).andExpect(status().isForbidden());
		mockMvc.perform(build(method, url, body)).andExpect(status().isUnauthorized());
	}

	@ParameterizedTest
	@ValueSource(strings = { "/api/branches", "/api/employee-categories", "/api/employees", "/api/devices",
			"/api/users" })
	void anonymousCannotWriteMasterData(String path) throws Exception {
		mockMvc.perform(post(path).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void ownProfileEndpointsRequireLoginOnly() throws Exception {
		// Pengguna uji tidak punya baris akun, jadi 404 = lolos otorisasi tetapi datanya tidak ada.
		for (RequestPostProcessor role : new RequestPostProcessor[] { OWNER, HR_FINANCE, EMPLOYEE }) {
			mockMvc.perform(get("/api/me/employee").with(role)).andExpect(status().isNotFound());
			mockMvc.perform(get("/api/auth/me").with(role)).andExpect(status().isNotFound());
		}
		mockMvc.perform(get("/api/me/employee")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
	}

	@Test
	void csrfFailureHasIndonesianProblemDetail() throws Exception {
		mockMvc.perform(post("/api/branches").with(OWNER).contentType(MediaType.APPLICATION_JSON).content(BRANCH))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.detail").value(ProblemResponses.CSRF_INVALID));
	}

	/** AUD-008: tidak ada route untuk mengubah atau menghapus satu baris audit, bahkan untuk Owner. */
	@Test
	void auditLogCannotBeModifiedOrDeleted() throws Exception {
		mockMvc.perform(put("/api/audit-logs/1").with(OWNER).with(csrf()).contentType(MediaType.APPLICATION_JSON)
			.content("{}")).andExpect(status().isNotFound());
		mockMvc.perform(delete("/api/audit-logs/1").with(OWNER).with(csrf())).andExpect(status().isNotFound());
		mockMvc.perform(delete("/api/audit-logs").with(OWNER).with(csrf())).andExpect(status().isMethodNotAllowed());
	}

	@ParameterizedTest
	@ValueSource(strings = { "/api/branches/1", "/api/employees/1", "/api/devices/1", "/api/users/1" })
	void masterDataCannotBeDeleted(String path) throws Exception {
		mockMvc.perform(delete(path).with(OWNER).with(csrf())).andExpect(status().isMethodNotAllowed());
	}

	@ParameterizedTest
	@ValueSource(strings = { "/actuator/env", "/actuator/info", "/actuator/beans" })
	void actuatorEndpointsOtherThanHealthAreClosed(String path) throws Exception {
		mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
		mockMvc.perform(get(path).with(OWNER)).andExpect(status().isForbidden());
	}

	/** Default production: dokumentasi API mati (OPENAPI_ENABLED tidak diisi). */
	@ParameterizedTest
	@ValueSource(strings = { "/v3/api-docs", "/swagger-ui/index.html" })
	void apiDocsAreDisabledByDefault(String path) throws Exception {
		mockMvc.perform(get(path).with(OWNER)).andExpect(status().isNotFound());
		mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
	}

	@Test
	void wrongParameterTypeIsBadRequest() throws Exception {
		mockMvc.perform(get("/api/employees").param("branchId", "abc").with(OWNER))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("Parameter tidak valid: branchId"));
	}

	/** Builder baru setiap kali: builder MockMvc menumpuk post-processor jika dipakai ulang. */
	private static MockHttpServletRequestBuilder build(HttpMethod method, String url, String body) {
		MockHttpServletRequestBuilder request = request(method, url).with(csrf());
		return body == null ? request : request.contentType(MediaType.APPLICATION_JSON).content(body);
	}

}
