package com.biometricattendance.signature;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.biometricattendance.signature.SignatureDtos.SignatureStatus;

/** Spesimen tanda tangan milik akun yang login. Tidak ada endpoint untuk membaca spesimen akun lain. */
@RestController
@RequestMapping("/api/me/signature")
public class SignatureController {

	private final SignatureService service;

	public SignatureController(SignatureService service) {
		this.service = service;
	}

	@GetMapping
	ResponseEntity<byte[]> get() {
		return ResponseEntity.ok()
			.contentType(MediaType.IMAGE_PNG)
			.cacheControl(CacheControl.noStore())
			.body(service.mine());
	}

	@GetMapping("/status")
	SignatureStatus status() {
		return service.status();
	}

	@PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	SignatureStatus put(@RequestPart("file") MultipartFile file) {
		return service.save(file);
	}

	@DeleteMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete() {
		service.deleteMine();
	}

}
