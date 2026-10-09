package com.biometricattendance.audit;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "audit_logs")
public class AuditLog {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Instant occurredAt;

	private String actorUsername;

	@Column(nullable = false)
	private String action;

	@Column(nullable = false)
	private String entityType;

	private String entityId;

	@Column(columnDefinition = "text")
	private String beforeData;

	@Column(columnDefinition = "text")
	private String afterData;

	@Column(columnDefinition = "text")
	private String reason;

	protected AuditLog() {
	}

	public AuditLog(String actorUsername, String action, String entityType, String entityId,
			String beforeData, String afterData, String reason) {
		this.occurredAt = Instant.now();
		this.actorUsername = actorUsername;
		this.action = action;
		this.entityType = entityType;
		this.entityId = entityId;
		this.beforeData = beforeData;
		this.afterData = afterData;
		this.reason = reason;
	}

	public Long getId() {
		return id;
	}

	public Instant getOccurredAt() {
		return occurredAt;
	}

	public String getActorUsername() {
		return actorUsername;
	}

	public String getAction() {
		return action;
	}

	public String getEntityType() {
		return entityType;
	}

	public String getEntityId() {
		return entityId;
	}

	public String getBeforeData() {
		return beforeData;
	}

	public String getAfterData() {
		return afterData;
	}

	public String getReason() {
		return reason;
	}

}
