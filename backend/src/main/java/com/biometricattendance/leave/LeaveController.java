package com.biometricattendance.leave;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.biometricattendance.leave.LeaveDtos.AttachmentResponse;
import com.biometricattendance.leave.LeaveDtos.DecisionRequest;
import com.biometricattendance.leave.LeaveDtos.LeaveRequestResponse;
import com.biometricattendance.leave.LeaveDtos.PendingSummary;
import com.biometricattendance.leave.LeaveDtos.SubmitLeaveRequest;
import com.biometricattendance.leave.LeaveDtos.UsageResponse;
import com.biometricattendance.leave.LeaveService.AttachmentFile;

import jakarta.validation.Valid;

/** Pengelolaan izin/cuti oleh Owner dan HR/Finance: daftar, catat atas nama karyawan, setujui/tolak/batalkan. */
@RestController
@RequestMapping("/api/leave-requests")
public class LeaveController {

	private final LeaveService service;

	public LeaveController(LeaveService service) {
		this.service = service;
	}

	@GetMapping
	List<LeaveRequestResponse> list(@RequestParam(required = false) LeaveStatus status,
			@RequestParam(required = false) Long employeeId, @RequestParam(required = false) Long branchId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		return service.list(status, employeeId, branchId, from, to);
	}

	@GetMapping("/pending")
	PendingSummary pending() {
		return service.pending();
	}

	@GetMapping("/usage")
	UsageResponse usage(@RequestParam Long employeeId, @RequestParam int year) {
		return service.usage(employeeId, year);
	}

	@GetMapping("/{id}")
	LeaveRequestResponse get(@PathVariable Long id) {
		return service.get(id);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	LeaveRequestResponse submit(@Valid @RequestBody SubmitLeaveRequest request) {
		return service.submitFor(request);
	}

	@PostMapping("/{id}/approve")
	LeaveRequestResponse approve(@PathVariable Long id, @Valid @RequestBody(required = false) DecisionRequest body) {
		return service.approve(id, body == null ? null : body.note());
	}

	@PostMapping("/{id}/reject")
	LeaveRequestResponse reject(@PathVariable Long id, @Valid @RequestBody(required = false) DecisionRequest body) {
		return service.reject(id, body == null ? null : body.note());
	}

	@PostMapping("/{id}/cancel")
	LeaveRequestResponse cancel(@PathVariable Long id, @Valid @RequestBody(required = false) DecisionRequest body) {
		return service.cancel(id, body == null ? null : body.note());
	}

	@PostMapping(path = "/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	AttachmentResponse upload(@PathVariable Long id, @RequestPart("file") MultipartFile file) {
		return service.addAttachment(id, file);
	}

	@GetMapping("/{id}/attachments/{attachmentId}")
	ResponseEntity<Resource> download(@PathVariable Long id, @PathVariable Long attachmentId) {
		return fileResponse(service.download(id, attachmentId));
	}

	/**
	 * Selalu sebagai unduhan dengan jenis hasil deteksi isi file, agar browser tidak menjalankan isinya
	 * (nosniff) dan tidak menyimpannya di cache bersama.
	 */
	static ResponseEntity<Resource> fileResponse(AttachmentFile file) {
		return ResponseEntity.ok()
			.contentType(MediaType.parseMediaType(file.contentType()))
			.header(HttpHeaders.CONTENT_DISPOSITION,
					ContentDisposition.attachment().filename(file.originalName(), StandardCharsets.UTF_8).build().toString())
			.header("X-Content-Type-Options", "nosniff")
			.cacheControl(CacheControl.noStore().cachePrivate())
			.body(file.resource());
	}

}
