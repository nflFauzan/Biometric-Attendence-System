package com.biometricattendance.report;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

import com.biometricattendance.calculation.DailyResult.AttendanceStatus;
import com.biometricattendance.report.ReportDtos.ReportDay;
import com.biometricattendance.report.ReportDtos.ReportEmployee;
import com.biometricattendance.report.ReportDtos.ReportModel;

/**
 * Satu sumber warna, tata letak, dan aturan sel format klien AS-5 dan AS-6 untuk Excel dan PDF (K-142). Warna
 * diperkirakan dari foto; ganti di sini saat file Excel asli diterima (Q-050).
 */
final class TakkaFormat {

	static final String BAND = "8EA9DB";

	static final String MONTH_FILL = "FFE699";

	static final String SEPARATOR = "E2B96F";

	static final String RED = "E59191";

	static final String TOTAL_DAYS_FILL = "FFD966";

	static final int DATE_COL = 3;

	static final int DATE_COLS = 31;

	static final int HEADER_ROW = 4;

	static final int FIRST_DATA_ROW = 5;

	static final int SALARY_MONTH_ROW = 1;

	static final int OVERTIME_MONTH_ROW = 2;

	static final String[] SALARY_TOTALS = { "Total Days", "Wages", "UPAH LEMBUR", "INPUT PIUTANG", "ONSITE",
			"GAJI POKOK", "Total Wages" };

	static final String[] OVERTIME_TOTALS = { "TOTAL LEMBUR", "UANG LEMBUR", "JUMLAH UPAH" };

	static final String NO_DATA = "Tidak ada data pada rentang dan filter ini.";

	/** Merah muda sel tidak masuk di AS-1, dinormalkan dari foto terhadap warna kertas. */
	static final String WEEKLY_RED = "E17D98";

	/** Merah tua angka jam lembur di AS-2. */
	static final String WEEKLY_HOURS = "B0405E";

	static final String WEEKLY_JOB = "Upah Supir dan Kenek";

	static final String WEEKLY_LOCATION = "TOKO";

	static final String WEEKLY_PERIOD_LABEL = "PERIODE           :";

	static final String WEEKLY_WEEK_LABEL = "MINGGU            :";

	private static final String[] MONTHS = { "JANUARI", "FEBRUARI", "MARET", "APRIL", "MEI", "JUNI", "JULI", "AGUSTUS",
			"SEPTEMBER", "OKTOBER", "NOVEMBER", "DESEMBER" };

	private TakkaFormat() {
	}

	/** Huruf kolom hari AS-1/AS-2: S M S S R K J untuk Sabtu sampai Jumat. */
	static String dayLetter(LocalDate date) {
		return switch (date.getDayOfWeek()) {
			case SATURDAY, MONDAY, TUESDAY -> "S";
			case SUNDAY -> "M";
			case WEDNESDAY -> "R";
			case THURSDAY -> "K";
			case FRIDAY -> "J";
		};
	}

	/** "26 SEPTEMBER - 02 OKTOBER 2026"; tahun ditulis dua kali bila rentang melewati pergantian tahun. */
	static String weeklyPeriod(List<LocalDate> dates) {
		LocalDate from = dates.get(0);
		LocalDate to = dates.get(dates.size() - 1);
		String start = String.format(Locale.ROOT, "%02d %s", from.getDayOfMonth(), MONTHS[from.getMonthValue() - 1]);
		if (from.getYear() != to.getYear()) {
			start += " " + from.getYear();
		}
		return start + String.format(Locale.ROOT, " - %02d %s %d", to.getDayOfMonth(), MONTHS[to.getMonthValue() - 1],
				to.getYear());
	}

	/** Minggu keberapa di bulan tanggal terakhir periode, seperti foto: berakhir awal Oktober menjadi "KE - 1". */
	static String weeklyNumber(List<LocalDate> dates) {
		return "KE - " + ((dates.get(dates.size() - 1).getDayOfMonth() - 1) / 7 + 1);
	}

	/** Tanda hari AS-1 menurut tanggal, karena periode mingguan bisa melewati dua bulan. */
	static String mark(ReportEmployee employee, LocalDate date) {
		ReportDay day = dayOn(employee, date);
		if (day == null || day.status() == null) {
			return "";
		}
		if (day.status() == AttendanceStatus.LENGKAP) {
			return "V";
		}
		return day.status() == AttendanceStatus.TIDAK_LENGKAP ? "?" : "";
	}

	/** Jam lembur pada tanggal itu, null bila 0 atau tidak ada. */
	static BigDecimal overtime(ReportEmployee employee, LocalDate date) {
		ReportDay day = dayOn(employee, date);
		BigDecimal hours = day == null ? null : day.validatedOvertimeHours();
		return hours == null || hours.signum() == 0 ? null : hours;
	}

	private static ReportDay dayOn(ReportEmployee employee, LocalDate date) {
		return employee.days().stream().filter(d -> d.date().equals(date)).findFirst().orElse(null);
	}

	/** Kolom merah: tanggal yang tidak ada di bulan itu atau hari libur. */
	static boolean redColumn(ReportModel model, int dayOfMonth) {
		LocalDate date = dateOf(model, dayOfMonth);
		return date == null || model.offDates().contains(date);
	}

	/** Sel kisi AS-5 merah kecuali hari berisi V atau ?. */
	static boolean redSalaryCell(ReportEmployee employee, int dayOfMonth) {
		return mark(employee, dayOfMonth).isEmpty();
	}

	static String mark(ReportEmployee employee, int dayOfMonth) {
		ReportDay day = dayOf(employee, dayOfMonth);
		if (day == null || day.status() == null) {
			return "";
		}
		if (day.status() == AttendanceStatus.LENGKAP) {
			return "V";
		}
		return day.status() == AttendanceStatus.TIDAK_LENGKAP ? "?" : "";
	}

	/** Jam lembur tanggal itu, null bila 0 atau tidak ada. */
	static BigDecimal overtime(ReportEmployee employee, int dayOfMonth) {
		ReportDay day = dayOf(employee, dayOfMonth);
		BigDecimal hours = day == null ? null : day.validatedOvertimeHours();
		return hours == null || hours.signum() == 0 ? null : hours;
	}

	static List<ReportEmployee> overtimeRows(List<ReportEmployee> employees) {
		return employees.stream()
			.filter(e -> e.financial() != null && e.financial().overtimeRate() != null)
			.toList();
	}

	/** Jam tanpa nol berlebih, dengan koma: 4, 1,5, 1,33. */
	static String hoursText(BigDecimal hours) {
		if (hours == null || hours.signum() == 0) {
			return "";
		}
		return hours.stripTrailingZeros().toPlainString().replace('.', ',');
	}

	/** Rupiah dengan titik ribuan, tanpa awalan: 40.000. */
	static String amountText(BigDecimal value) {
		BigDecimal v = value == null ? BigDecimal.ZERO : value;
		return String.format(Locale.GERMANY, "%,d", v.setScale(0, RoundingMode.HALF_UP).longValueExact());
	}

	static String upper(String name) {
		return name == null ? "" : name.toUpperCase(Locale.ROOT);
	}

	static String nz(String value) {
		return value == null ? "" : value;
	}

	static BigDecimal nz(BigDecimal value) {
		return value == null ? BigDecimal.ZERO : value;
	}

	private static LocalDate dateOf(ReportModel model, int dayOfMonth) {
		return model.dates().stream().filter(d -> d.getDayOfMonth() == dayOfMonth).findFirst().orElse(null);
	}

	private static ReportDay dayOf(ReportEmployee employee, int dayOfMonth) {
		return employee.days().stream().filter(d -> d.date().getDayOfMonth() == dayOfMonth).findFirst().orElse(null);
	}

}
