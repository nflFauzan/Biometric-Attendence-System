package com.biometricattendance.dashboard;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.biometricattendance.dashboard.DashboardDtos.DashboardSummary;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

	private final DashboardService service;
	private final DashboardStream stream;

	public DashboardController(DashboardService service, DashboardStream stream) {
		this.service = service;
		this.stream = stream;
	}

	@GetMapping("/summary")
	DashboardSummary summary(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
		return service.summary(date);
	}

	/** SSE: memberi tahu dashboard bahwa ada scan, koreksi, atau status mesin yang berubah. */
	@GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	SseEmitter stream() {
		return stream.subscribe();
	}

}
