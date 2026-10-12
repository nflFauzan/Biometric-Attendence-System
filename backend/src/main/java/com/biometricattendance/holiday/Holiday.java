package com.biometricattendance.holiday;

import java.time.Instant;
import java.time.LocalDate;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Hari libur yang berlaku untuk semua cabang (K-051). */
@Entity
@Table(name = "holidays")
public class Holiday {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private LocalDate holidayDate;

	@Column(nullable = false)
	private String name;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(nullable = false)
	private Instant updatedAt;

	public Long getId() {
		return id;
	}

	public LocalDate getHolidayDate() {
		return holidayDate;
	}

	public void setHolidayDate(LocalDate holidayDate) {
		this.holidayDate = holidayDate;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

}
