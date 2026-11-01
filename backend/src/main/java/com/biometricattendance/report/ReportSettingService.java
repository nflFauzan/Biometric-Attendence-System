package com.biometricattendance.report;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.biometricattendance.audit.AuditService;
import com.biometricattendance.common.ApiException;
import com.biometricattendance.report.ReportDtos.ReportSettingRequest;
import com.biometricattendance.report.ReportDtos.ReportSettingResponse;

/** Membaca dan menyimpan isi kop laporan; setiap perubahan dicatat di audit log (K-142). */
@Service
public class ReportSettingService {

	private final ReportSettingRepository repository;

	private final AuditService audit;

	public ReportSettingService(ReportSettingRepository repository, AuditService audit) {
		this.repository = repository;
		this.audit = audit;
	}

	@Transactional(readOnly = true)
	public ReportSettingResponse get(String slug) {
		requireKnown(slug);
		return repository.findById(slug)
			.map(ReportSettingService::toResponse)
			.orElse(new ReportSettingResponse(null, null));
	}

	@Transactional
	public ReportSettingResponse save(String slug, ReportSettingRequest request) {
		requireKnown(slug);
		ReportSettingResponse before = get(slug);
		ReportSetting setting = repository.findById(slug).orElseGet(() -> new ReportSetting(slug));
		setting.update(clean(request.managerName()), clean(request.departmentName()));
		ReportSettingResponse after = toResponse(repository.save(setting));
		audit.record("UPDATE", "REPORT_SETTING", slug, before, after);
		return after;
	}

	private static void requireKnown(String slug) {
		ReportType.fromSlug(slug)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Jenis laporan tidak dikenal"));
	}

	private static String clean(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private static ReportSettingResponse toResponse(ReportSetting setting) {
		return new ReportSettingResponse(setting.getManagerName(), setting.getDepartmentName());
	}

}
