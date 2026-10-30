package com.biometricattendance.loan;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import org.hibernate.annotations.CreationTimestamp;

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
@Table(name = "loan_repayments")
public class LoanRepayment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "loan_id", nullable = false)
	private EmployeeLoan loan;

	@Column(name = "repayment_date", nullable = false)
	private LocalDate repaymentDate;

	@Column(nullable = false, precision = 14, scale = 2)
	private BigDecimal amount;

	@Column(name = "period_label", nullable = false, length = 128)
	private String periodLabel;

	@Column(name = "report_type", nullable = false, length = 64)
	private String reportType;

	@Column(columnDefinition = "TEXT")
	private String notes;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected LoanRepayment() {
	}

	public LoanRepayment(EmployeeLoan loan, LocalDate repaymentDate, BigDecimal amount, String periodLabel,
			String reportType, String notes) {
		this.loan = loan;
		this.repaymentDate = repaymentDate;
		this.amount = amount;
		this.periodLabel = periodLabel;
		this.reportType = reportType;
		this.notes = notes;
	}

	public Long getId() {
		return id;
	}

	public EmployeeLoan getLoan() {
		return loan;
	}

	public LocalDate getRepaymentDate() {
		return repaymentDate;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public String getPeriodLabel() {
		return periodLabel;
	}

	public String getReportType() {
		return reportType;
	}

	public String getNotes() {
		return notes;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}

