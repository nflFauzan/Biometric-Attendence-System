package com.biometricattendance.devicecommand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.ResultActions;

import com.biometricattendance.ingest.RakitanFixtures;

/**
 * Admin menekan Batal tepat saat mesin melapor: konflik @Version pertama dicoba ulang sekali, dan yang tersimpan lebih
 * dulu yang berlaku (M1, K-101).
 */
class DeviceCommandCancelRaceTests extends RakitanFixtures {

	@MockitoSpyBean
	private DeviceCommandService service;

	private record Sent(Rakitan rakitan, String commandId) {
	}

	private Sent sent() throws Exception {
		long branch = createBranch();
		Rakitan rakitan = onlineRakitan(branch);
		String id = enrollOk(readyEmployee(branch), rakitan.id(), 1);
		heartbeat(rakitan.key(), "{}").andExpect(status().isOk());
		return new Sent(rakitan, id);
	}

	private static ObjectOptimisticLockingFailureException conflict(String id) {
		return new ObjectOptimisticLockingFailureException(DeviceCommand.class, id);
	}

	@Test
	void cancelReportsFinishedWhenResultWins() throws Exception {
		Sent s = sent();
		result(s.rakitan().key(), s.commandId(), "{\"status\":\"BERHASIL\"}").andExpect(status().isOk());
		doThrow(conflict(s.commandId())).doCallRealMethod().when(service).cancel(s.commandId());

		cancel(s.commandId()).andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value("Perintah sudah selesai."));
		assertThat(statusOf(s.commandId())).isEqualTo("BERHASIL");
	}

	@Test
	void cancelRetriesWhenProgressWins() throws Exception {
		Sent s = sent();
		doThrow(conflict(s.commandId())).doCallRealMethod().when(service).cancel(s.commandId());

		cancel(s.commandId()).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DIBATALKAN"));
	}

	@Test
	void cancelGivesUpAfterOneRetry() throws Exception {
		Sent s = sent();
		doThrow(conflict(s.commandId())).doThrow(conflict(s.commandId())).when(service).cancel(s.commandId());

		cancel(s.commandId()).andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value("Data baru saja diubah oleh pengguna lain. Muat ulang lalu coba lagi"));
		verify(service, times(2)).cancel(s.commandId());
	}

	private ResultActions cancel(String id) throws Exception {
		return mockMvc.perform(post("/api/device-commands/" + id + "/cancel").with(HR).with(csrf()));
	}

	private String statusOf(String commandId) {
		return jdbc.queryForObject("select status from device_commands where id = ?::uuid", String.class, commandId);
	}

}
