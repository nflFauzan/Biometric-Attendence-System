package com.biometricattendance.loan;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.biometricattendance.employee.Employee;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "employee_loans")
public class EmployeeLoan {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "employee_id", nullable = false)
	private Employee employee;

	@Column(name = "loan_number", nullable = false, unique = true, length = 64)
	private String loanNumber;

	@Column(name = "loan_date", nullable = false)
	private LocalDate loanDate;

	@Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
	private BigDecimal totalAmount;

	@Column(name = "installment_amount", nullable = false, precision = 14, scale = 2)
	private BigDecimal installmentAmount;

	@Column(name = "remaining_amount", nullable = false, precision = 14, scale = 2)
	private BigDecimal remainingAmount;

	@Column(nullable = false, length = 32)
	private String status;

	@Column(columnDefinition = "TEXT")
	private String notes;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected EmployeeLoan() {
	}

	public EmployeeLoan(Employee employee, String loanNumber, LocalDate loanDate, BigDecimal totalAmount,
			BigDecimal installmentAmount, BigDecimal remainingAmount, String status, String notes) {
		this.employee = employee;
		this.loanNumber = loanNumber;
		this.loanDate = loanDate;
		this.totalAmount = totalAmount;
		this.installmentAmount = installmentAmount;
		this.remainingAmount = remainingAmount;
		this.status = status;
		this.notes = notes;
	}

	public Long getId() {
		return id;
	}

	public Employee getEmployee() {
		return employee;
	}

	public String getLoanNumber() {
		return loanNumber;
	}

	public LocalDate getLoanDate() {
		return loanDate;
	}

	public BigDecimal getTotalAmount() {
		return totalAmount;
	}

	public BigDecimal getInstallmentAmount() {
		return installmentAmount;
	}

	public BigDecimal getRemainingAmount() {
		return remainingAmount;
	}

	public void setRemainingAmount(BigDecimal remainingAmount) {
		this.remainingAmount = remainingAmount;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public String getNotes() {
		return notes;
	}

	public void setNotes(String notes) {
		this.notes = notes;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}

