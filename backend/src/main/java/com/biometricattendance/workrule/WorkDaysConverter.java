package com.biometricattendance.workrule;

import java.time.DayOfWeek;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Menyimpan hari kerja sebagai teks singkat terurut, mis. "MON,TUE,WED,THU,FRI,SAT". */
@Converter
class WorkDaysConverter implements AttributeConverter<Set<DayOfWeek>, String> {

	@Override
	public String convertToDatabaseColumn(Set<DayOfWeek> days) {
		if (days == null) {
			return null;
		}
		return days.stream().sorted().map(day -> day.name().substring(0, 3)).collect(Collectors.joining(","));
	}

	@Override
	public Set<DayOfWeek> convertToEntityAttribute(String value) {
		if (value == null) {
			return null;
		}
		Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
		Arrays.stream(value.split(","))
			.map(String::trim)
			.filter(code -> !code.isEmpty())
			.forEach(code -> days.add(Arrays.stream(DayOfWeek.values())
				.filter(day -> day.name().startsWith(code))
				.findFirst()
				.orElseThrow(() -> new IllegalStateException("Kode hari tidak dikenal: " + code))));
		return days;
	}

}
