package com.biometricattendance.audit;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

	Page<AuditLog> findAllByOrderByOccurredAtDescIdDesc(Pageable pageable);

	Page<AuditLog> findByEntityTypeOrderByOccurredAtDescIdDesc(String entityType, Pageable pageable);

	List<AuditLog> findByEntityTypeAndEntityIdOrderByIdAsc(String entityType, String entityId);

}
