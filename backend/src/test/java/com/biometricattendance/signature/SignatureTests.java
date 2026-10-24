package com.biometricattendance.signature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.biometricattendance.AttendanceFixtures;
import com.biometricattendance.leave.HrReviewerCounter;

/** Spesimen tanda tangan per akun dan pembekuannya saat tahap disetujui (spec 2026-10-24 bagian 9). Data sintetis. */
class SignatureTests extends AttendanceFixtures {

	record Acct(String name, RequestPostProcessor as) {
	}

	@MockitoBean
	HrReviewerCounter hrCounter;

	@Autowired
	SignatureService signatures;

	@Autowired
	com.biometricattendance.leave.AttachmentStorage storage;

	@BeforeEach
	void otherHrExists() {
		when(hrCounter.countOtherActiveHr(org.mockito.ArgumentMatchers.any())).thenReturn(1L);
	}

	@Test
	void uploadNormalizesToTransparentPngWithinBounds() throws Exception {
		Acct me = account("EMPLOYEE");
		mockMvc.perform(upload(me, png(1200, 600, true)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.exists").value(true));

		byte[] body = mockMvc.perform(get("/api/me/signature").with(me.as()))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsByteArray();
		BufferedImage img = ImageIO.read(new ByteArrayInputStream(body));
		assertThat(img.getWidth()).isLessThanOrEqualTo(600);
		assertThat(img.getHeight()).isLessThanOrEqualTo(200);
		assertThat(img.getRGB(0, 0) >>> 24).isZero();
		mockMvc.perform(get("/api/me/signature/status").with(me.as())).andExpect(jsonPath("$.exists").value(true));
	}

	@Test
	void rejectsNonImagesAndOversizedFiles() throws Exception {
		Acct me = account("EMPLOYEE");
		mockMvc.perform(upload(me, "bukan gambar".getBytes(StandardCharsets.UTF_8))).andExpect(status().isBadRequest());

		byte[] big = new byte[1024 * 1024 + 1];
		System.arraycopy(png(10, 10, true), 0, big, 0, 8);
		mockMvc.perform(upload(me, big)).andExpect(status().isBadRequest());
		mockMvc.perform(get("/api/me/signature").with(me.as())).andExpect(status().isNotFound());
	}

	@Test
	void deleteRemovesSignature() throws Exception {
		Acct me = account("HR_FINANCE");
		mockMvc.perform(delete("/api/me/signature").with(me.as()).with(csrf())).andExpect(status().isNoContent());
		mockMvc.perform(upload(me, png(100, 50, true))).andExpect(status().isOk());
		mockMvc.perform(delete("/api/me/signature").with(me.as()).with(csrf())).andExpect(status().isNoContent());
		mockMvc.perform(get("/api/me/signature").with(me.as())).andExpect(status().isNotFound());
		mockMvc.perform(get("/api/me/signature/status").with(me.as())).andExpect(jsonPath("$.exists").value(false));
		assertThat(jdbc.queryForList("select action from audit_logs where entity_type = 'USER_SIGNATURE' "
				+ "and actor_username = ? order by id", String.class, me.name())).containsExactly("UPLOAD", "DELETE");
	}

	@Test
	void nobodyCanReadAnotherAccountsSignature() throws Exception {
		Acct a = account("EMPLOYEE");
		Acct b = account("OWNER");
		mockMvc.perform(upload(a, png(100, 50, true))).andExpect(status().isOk());
		mockMvc.perform(get("/api/me/signature").with(b.as())).andExpect(status().isNotFound());
		mockMvc.perform(get("/api/me/signature").with(a.as())).andExpect(status().isOk());
	}

	@Test
	void approvalFreezesSignatureCopy() throws Exception {
		Acct hr = account("HR_FINANCE");
		mockMvc.perform(upload(hr, png(300, 100, true))).andExpect(status().isOk());

		long employee = createEmployee(createBranch(), null);
		long type = idOf(mockMvc.perform(post("/api/leave-types").with(OWNER)
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"" + unique("Jenis ") + "\",\"requiresDocument\":false,\"active\":true,"
					+ "\"requiresOwnerApproval\":false,\"letterTemplate\":\"UMUM\"}"))
			.andExpect(status().isCreated()));
		long id = idOf(mockMvc.perform(post("/api/leave-requests").with(HR)
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"employeeId\":" + employee + ",\"leaveTypeId\":" + type
					+ ",\"startDate\":\"2026-10-05\",\"endDate\":\"2026-10-05\",\"reason\":\"Dicatat HR\"}"))
			.andExpect(status().isCreated()));
		mockMvc.perform(post("/api/leave-requests/" + id + "/approve").with(hr.as())
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{}")).andExpect(status().isOk());

		String key = frozenKey(id);
		assertThat(key).isNotNull();
		String frozenHash = sha256(signatures.load(key));

		// HR mengganti spesimen; salinan beku di tahap lama tetap sama.
		mockMvc.perform(upload(hr, png(500, 400, false))).andExpect(status().isOk());
		String current = jdbc.queryForObject("select s.sha256 from user_signatures s join user_accounts u "
				+ "on u.id = s.user_account_id where u.username = ?", String.class, hr.name());
		assertThat(current).isNotEqualTo(frozenHash);
		assertThat(frozenKey(id)).isEqualTo(key);
		assertThat(sha256(signatures.load(key))).isEqualTo(frozenHash);
	}

	/** File spesimen hilang dari volume: persetujuan, pengiriman, penggantian, dan penghapusan tetap berjalan. */
	@Test
	void missingSpecimenFileIsTolerated() throws Exception {
		Acct hr = account("HR_FINANCE");
		mockMvc.perform(upload(hr, png(300, 100, true))).andExpect(status().isOk());
		storage.delete(specimenKey(hr));

		assertThat(signatures.freezeCopy(hr.name())).isNull();
		long id = submitRequest();
		mockMvc.perform(post("/api/leave-requests/" + id + "/approve").with(hr.as())
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{}")).andExpect(status().isOk());
		assertThat(frozenKey(id)).isNull();

		// Mengganti spesimen yang filenya hilang tetap berhasil dan diaudit.
		mockMvc.perform(upload(hr, png(200, 80, true))).andExpect(status().isOk());
		mockMvc.perform(get("/api/me/signature").with(hr.as())).andExpect(status().isOk());
		assertThat(signatures.freezeCopy(hr.name())).isNotNull();

		// Menghapus spesimen yang filenya hilang juga berhasil.
		storage.delete(specimenKey(hr));
		mockMvc.perform(delete("/api/me/signature").with(hr.as()).with(csrf())).andExpect(status().isNoContent());
		mockMvc.perform(get("/api/me/signature/status").with(hr.as())).andExpect(jsonPath("$.exists").value(false));
	}

	@Test
	void employeeSubmitSurvivesMissingSpecimenFile() throws Exception {
		Acct me = account("EMPLOYEE");
		mockMvc.perform(upload(me, png(300, 100, true))).andExpect(status().isOk());
		storage.delete(specimenKey(me));
		long type = idOf(mockMvc.perform(post("/api/leave-types").with(OWNER)
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"" + unique("Jenis ") + "\",\"requiresDocument\":false,\"active\":true,"
					+ "\"requiresOwnerApproval\":false,\"letterTemplate\":\"UMUM\"}"))
			.andExpect(status().isCreated()));

		long id = idOf(mockMvc.perform(post("/api/me/leave-requests").with(me.as())
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"leaveTypeId\":" + type + ",\"startDate\":\"2026-10-05\",\"endDate\":\"2026-10-05\","
					+ "\"reason\":\"Uji\"}"))
			.andExpect(status().isCreated()));
		assertThat(jdbc.queryForObject("select applicant_signature_key from leave_requests where id = ?", String.class,
				id)).isNull();
	}

	@Test
	void freezeCopyIsNullWithoutSpecimen() throws Exception {
		Acct hr = account("HR_FINANCE");
		assertThat(signatures.freezeCopy(hr.name())).isNull();
		assertThat(signatures.freezeCopy("tidak-ada-akun")).isNull();
	}

	// ---- Helper ----

	private String specimenKey(Acct a) {
		return jdbc.queryForObject("select s.storage_key from user_signatures s join user_accounts u "
				+ "on u.id = s.user_account_id where u.username = ?", String.class, a.name());
	}

	private long submitRequest() throws Exception {
		long employee = createEmployee(createBranch(), null);
		long type = idOf(mockMvc.perform(post("/api/leave-types").with(OWNER)
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"" + unique("Jenis ") + "\",\"requiresDocument\":false,\"active\":true,"
					+ "\"requiresOwnerApproval\":false,\"letterTemplate\":\"UMUM\"}"))
			.andExpect(status().isCreated()));
		return idOf(mockMvc.perform(post("/api/leave-requests").with(HR)
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"employeeId\":" + employee + ",\"leaveTypeId\":" + type
					+ ",\"startDate\":\"2026-10-05\",\"endDate\":\"2026-10-05\",\"reason\":\"Dicatat HR\"}"))
			.andExpect(status().isCreated()));
	}

	private String frozenKey(long requestId) {
		return jdbc.queryForObject("select signature_storage_key from leave_approvals where leave_request_id = ?",
				String.class, requestId);
	}

	private Acct account(String role) throws Exception {
		String username = unique("ttd-");
		String employee = "";
		if (role.equals("EMPLOYEE")) {
			employee = ",\"employeeId\":" + createEmployee(createBranch(), null);
		}
		mockMvc.perform(post("/api/users").with(OWNER)
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"username\":\"" + username + "\",\"password\":\"rahasia-uji-123\",\"role\":\"" + role + "\""
					+ employee + "}"))
			.andExpect(status().isCreated());
		return new Acct(username, user(username).roles(role));
	}

	private RequestBuilder upload(Acct me, byte[] bytes) {
		return multipart(HttpMethod.PUT, "/api/me/signature")
			.file(new MockMultipartFile("file", "ttd.png", "image/png", bytes))
			.with(me.as())
			.with(csrf());
	}

	private static byte[] png(int w, int h, boolean pureWhite) throws Exception {
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = img.createGraphics();
		g.setColor(pureWhite ? Color.WHITE : new Color(250, 250, 250));
		g.fillRect(0, 0, w, h);
		g.setColor(Color.BLACK);
		g.setStroke(new BasicStroke(Math.max(2, w / 100f)));
		g.drawLine(w / 4, h / 2, 3 * w / 4, h / 4);
		g.dispose();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(img, "png", out);
		return out.toByteArray();
	}

	private static String sha256(byte[] bytes) throws Exception {
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
	}

}
