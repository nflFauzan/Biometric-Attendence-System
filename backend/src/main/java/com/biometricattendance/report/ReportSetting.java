package com.biometricattendance.report;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Isi kop laporan format klien untuk satu jenis laporan (K-142). */
@Entity
@Table(name = "report_settings")
public class ReportSetting {

	@Id
	@Column(name = "report_slug")
	private String reportSlug;

	@Column(name = "manager_name")
	private String managerName;

	@Column(name = "department_name")
	private String departmentName;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected ReportSetting() {
	}

	public ReportSetting(String reportSlug) {
		this.reportSlug = reportSlug;
	}

	public void update(String managerName, String departmentName) {
		this.managerName = managerName;
		this.departmentName = departmentName;
		this.updatedAt = Instant.now();
	}

	public String getManagerName() {
		return managerName;
	}

	public String getDepartmentName() {
		return departmentName;
	}

}
