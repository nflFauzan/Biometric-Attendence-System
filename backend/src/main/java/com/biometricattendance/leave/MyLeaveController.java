package com.biometricattendance.leave;

import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.biometricattendance.leave.LeaveDtos.AttachmentResponse;
import com.biometricattendance.leave.LeaveDtos.DecisionRequest;
import com.biometricattendance.leave.LeaveDtos.LeaveRequestResponse;
import com.biometricattendance.leave.LeaveDtos.LeaveDraftRequest;

import jakarta.validation.Valid;

/**
 * Pengajuan izin/cuti milik pengguna yang login (K-073). Karyawan ditentukan dari akun, bukan dari input, dan
 * pengajuan karyawan lain dibalas 404.
 */
@RestController
@RequestMapping("/api/me/leave-requests")
public class MyLeaveController {

	private final LeaveService service;
	private final LeaveLetterService letters;

	public MyLeaveController(LeaveService service, LeaveLetterService letters) {
		this.service = service;
		this.letters = letters;
	}

	@GetMapping
	List<LeaveRequestResponse> list() {
		return service.mine();
	}

	@GetMapping("/{id}")
	LeaveRequestResponse get(@PathVariable Long id) {
		return service.getMine(id);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	LeaveRequestResponse create(@Valid @RequestBody LeaveDraftRequest request) {
		return service.createMine(request);
	}

	@PutMapping("/{id}")
	LeaveRequestResponse update(@PathVariable Long id, @Valid @RequestBody LeaveDraftRequest request) {
		return service.updateMyDraft(id, request);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@PathVariable Long id) {
		service.deleteMyDraft(id);
	}

	@PostMapping("/{id}/submit")
	LeaveRequestResponse submit(@PathVariable Long id) {
		return service.submitMyDraft(id);
	}

	/** Draft baru dari pengajuan yang ditolak (K-126). */
	@PostMapping("/{id}/resubmit")
	@ResponseStatus(HttpStatus.CREATED)
	LeaveRequestResponse resubmit(@PathVariable Long id) {
		return service.resubmitMine(id);
	}

	@PostMapping("/{id}/cancel")
	LeaveRequestResponse cancel(@PathVariable Long id, @Valid @RequestBody(required = false) DecisionRequest body) {
		return service.cancelMine(id, body == null ? null : body.note());
	}

	@PostMapping(path = "/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	AttachmentResponse upload(@PathVariable Long id, @RequestPart("file") MultipartFile file) {
		return service.addMyAttachment(id, file);
	}

	/** Surat PDF milik sendiri (draft pun boleh, bercap DRAFT); tidak diaudit. */
	@GetMapping("/{id}/letter.pdf")
	ResponseEntity<byte[]> letter(@PathVariable Long id) {
		return LeaveController.pdfResponse(letters.mine(id));
	}

	@GetMapping("/{id}/attachments/{attachmentId}")
	ResponseEntity<Resource> download(@PathVariable Long id, @PathVariable Long attachmentId) {
		return LeaveController.fileResponse(service.downloadMine(id, attachmentId));
	}

}
