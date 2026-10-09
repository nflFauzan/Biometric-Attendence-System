package com.biometricattendance.audit;

import java.time.Instant;

import org.springframework.data.domain.Page;
import com.biometricattendance.common.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Audit log hanya bisa dibaca Owner (diatur di SecurityConfig). */
@RestController
@RequestMapping("/api/audit-logs")
public class AuditLogController {

	private final AuditLogRepository repository;

	public AuditLogController(AuditLogRepository repository) {
		this.repository = repository;
	}

	@GetMapping
	PageResponse<AuditLogResponse> list(@RequestParam(required = false) String entityType,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
		PageRequest pageable = PageRequest.of(page, Math.min(size, 200));
		Page<AuditLog> logs = entityType == null || entityType.isBlank()
				? repository.findAllByOrderByOccurredAtDescIdDesc(pageable)
				: repository.findByEntityTypeOrderByOccurredAtDescIdDesc(entityType, pageable);
		return PageResponse.of(logs.map(AuditLogResponse::from));
	}

	record AuditLogResponse(Long id, Instant occurredAt, String actorUsername, String action, String entityType,
			String entityId, String beforeData, String afterData, String reason) {

		static AuditLogResponse from(AuditLog log) {
			return new AuditLogResponse(log.getId(), log.getOccurredAt(), log.getActorUsername(), log.getAction(),
					log.getEntityType(), log.getEntityId(), log.getBeforeData(), log.getAfterData(),
					log.getReason());
		}

	}

}
