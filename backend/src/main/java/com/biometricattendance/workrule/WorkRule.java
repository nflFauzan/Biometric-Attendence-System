package com.biometricattendance.workrule;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.biometricattendance.calculation.RuleLevel;
import com.biometricattendance.calculation.RuleValues;
import com.biometricattendance.calculation.RuleVersion;
import com.biometricattendance.category.EmployeeCategory;
import com.biometricattendance.employee.Employee;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Satu versi aturan kerja pada level Global, Kategori, atau Karyawan. Parameter yang null diwarisi dari
 * level di atasnya (K-046). Versi yang sudah berlaku tidak boleh diubah (K-050).
 */
@Entity
@Table(name = "work_rules")
public class WorkRule {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private RuleLevel scope;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "category_id")
	private EmployeeCategory category;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "employee_id")
	private Employee employee;

	@Column(nullable = false)
	private LocalDate effectiveFrom;

	private LocalTime workStart;

	private LocalTime workEnd;

	private LocalTime breakStart;

	private LocalTime breakEnd;

	private Integer lateToleranceMinutes;

	private LocalTime overtimeStart;

	@Convert(converter = WorkDaysConverter.class)
	private Set<DayOfWeek> workDays;

	private BigDecimal dailyWage;

	private BigDecimal overtimeRatePerHour;

	private BigDecimal baseSalary;

	private String note;

	@Column(nullable = false, updatable = false)
	private String createdBy;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(nullable = false)
	private Instant updatedAt;

	public Long getId() {
		return id;
	}

	public RuleLevel getScope() {
		return scope;
	}

	public void setScope(RuleLevel scope) {
		this.scope = scope;
	}

	public EmployeeCategory getCategory() {
		return category;
	}

	public void setCategory(EmployeeCategory category) {
		this.category = category;
	}

	public Employee getEmployee() {
		return employee;
	}

	public void setEmployee(Employee employee) {
		this.employee = employee;
	}

	public LocalDate getEffectiveFrom() {
		return effectiveFrom;
	}

	public void setEffectiveFrom(LocalDate effectiveFrom) {
		this.effectiveFrom = effectiveFrom;
	}

	public String getNote() {
		return note;
	}

	public void setNote(String note) {
		this.note = note;
	}

	public String getCreatedBy() {
		return createdBy;
	}

	public void setCreatedBy(String createdBy) {
		this.createdBy = createdBy;
	}

	public RuleValues getValues() {
		return new RuleValues(workStart, workEnd, breakStart, breakEnd, lateToleranceMinutes, overtimeStart, workDays,
				dailyWage, overtimeRatePerHour, baseSalary);
	}

	public void setValues(RuleValues values) {
		workStart = values.workStart();
		workEnd = values.workEnd();
		breakStart = values.breakStart();
		breakEnd = values.breakEnd();
		lateToleranceMinutes = values.lateToleranceMinutes();
		overtimeStart = values.overtimeStart();
		workDays = values.workDays();
		dailyWage = values.dailyWage();
		overtimeRatePerHour = values.overtimeRatePerHour();
		baseSalary = values.baseSalary();
	}

	public RuleVersion toVersion() {
		return new RuleVersion(id, scope, effectiveFrom, getValues());
	}

}
