package com.biometricattendance.calculation;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.biometricattendance.calculation.CalculationDtos.DailyPreviewRequest;
import com.biometricattendance.calculation.CalculationDtos.DailyPreviewResponse;
import com.biometricattendance.calculation.CalculationDtos.DailyResultResponse;
import com.biometricattendance.common.ApiException;
import com.biometricattendance.employee.Employee;
import com.biometricattendance.employee.EmployeeRepository;
import com.biometricattendance.holiday.HolidayRepository;
import com.biometricattendance.workrule.WorkRuleDtos.EffectiveRuleResponse;
import com.biometricattendance.workrule.WorkRuleService;

/**
 * Menyiapkan input dari database (aturan berlaku, kalender libur, zona waktu cabang) lalu memanggil
 * {@link DailyAttendanceCalculator}. Tidak menyimpan apa pun.
 */
@Service
public class CalculationService {

	private final EmployeeRepository employees;
	private final WorkRuleService workRules;
	private final HolidayRepository holidays;

	public CalculationService(EmployeeRepository employees, WorkRuleService workRules, HolidayRepository holidays) {
		this.employees = employees;
		this.workRules = workRules;
		this.holidays = holidays;
	}

	@Transactional(readOnly = true)
	public DailyPreviewResponse preview(DailyPreviewRequest request) {
		Employee employee = employees.findById(request.employeeId())
			.orElseThrow(() -> ApiException.notFound("Karyawan"));
		ZoneId zone = ZoneId.of(employee.getBranch().getTimezone());
		EffectiveRule rule = workRules.resolve(employee, request.date());
		boolean holiday = holidays.existsByHolidayDate(request.date());

		List<Instant> scans = request.scanTimes() == null ? List.of()
				: request.scanTimes().stream().map(time -> request.date().atTime(time).atZone(zone).toInstant()).toList();
		DailyInput.Correction correction = request.correctionCheckIn() == null && request.correctionCheckOut() == null
				? null : new DailyInput.Correction(request.correctionCheckIn(), request.correctionCheckOut());

		DailyResult result;
		try {
			result = DailyAttendanceCalculator
				.calculate(new DailyInput(request.date(), zone, scans, correction, rule, holiday));
		}
		catch (RuleNotConfiguredException ex) {
			throw ApiException.conflict(ex.getMessage());
		}

		EffectiveRuleResponse ruleResponse = EffectiveRuleResponse.of(employee.getId(), employee.getFullName(),
				employee.getCategory().getId(), employee.getCategory().getName(), request.date(), rule);
		return new DailyPreviewResponse(ruleResponse, zone.getId(), holiday, DailyResultResponse.from(result));
	}

}
