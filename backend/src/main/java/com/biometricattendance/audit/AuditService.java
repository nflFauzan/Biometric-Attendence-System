package com.biometricattendance.audit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.biometricattendance.common.CurrentUser;

import tools.jackson.databind.json.JsonMapper;

/**
 * Mencatat setiap perubahan data penting. Harus dipanggil di dalam transaksi yang sama
 * dengan perubahannya, sehingga perubahan tanpa jejak audit ikut dibatalkan.
 */
@Service
public class AuditService {

	private final AuditLogRepository repository;
	private final JsonMapper jsonMapper;

	public AuditService(AuditLogRepository repository, JsonMapper jsonMapper) {
		this.repository = repository;
		this.jsonMapper = jsonMapper;
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void record(String action, String entityType, Object entityId, Object before, Object after) {
		record(CurrentUser.username(), action, entityType, entityId, before, after, null);
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void record(String actor, String action, String entityType, Object entityId, Object before,
			Object after, String reason) {
		String id = entityId == null ? null : entityId.toString();
		repository.save(new AuditLog(actor, action, entityType, id, toJson(before), toJson(after), reason));
	}

	private String toJson(Object value) {
		return value == null ? null : jsonMapper.writeValueAsString(value);
	}

}
