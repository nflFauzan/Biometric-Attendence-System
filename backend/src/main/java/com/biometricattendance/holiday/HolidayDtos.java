package com.biometricattendance.holiday;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class HolidayDtos {

	private HolidayDtos() {
	}

	public record HolidayRequest(@NotNull(message = "Tanggal wajib diisi") LocalDate date,
			@NotBlank(message = "Nama wajib diisi") @Size(max = 100) String name) {
	}

	public record HolidayResponse(Long id, LocalDate date, String name) {

		public static HolidayResponse from(Holiday holiday) {
			return new HolidayResponse(holiday.getId(), holiday.getHolidayDate(), holiday.getName());
		}

	}

}
