package com.biometricattendance.report;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.biometricattendance.audit.AuditService;
import com.biometricattendance.common.ApiException;
import com.biometricattendance.report.ReportDtos.ReportFilter;
import com.biometricattendance.report.ReportDtos.ReportModel;

/**
 * Laporan absensi: pratinjau JSON serta unduhan Excel/PDF untuk Owner dan Finance & Accounting. Hanya unduhan yang
 * dicatat di audit log (K-105, AUD-009).
 */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

	private static final MediaType XLSX = MediaType
		.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

	private final ReportService reports;

	private final ExcelReportWriter excel;

	private final PdfReportWriter pdf;

	private final AuditService audit;

	public ReportController(ReportService reports, ExcelReportWriter excel, PdfReportWriter pdf, AuditService audit) {
		this.reports = reports;
		this.excel = excel;
		this.pdf = pdf;
		this.audit = audit;
	}

	@GetMapping("/{slug}")
	public ReportModel preview(@PathVariable String slug,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) Long branchId, @RequestParam(required = false) Long categoryId,
			@RequestParam(required = false) Long employeeId) {
		return reports.build(type(slug), filter(from, to, branchId, categoryId, employeeId));
	}

	@GetMapping("/{slug}.xlsx")
	@Transactional
	public ResponseEntity<byte[]> xlsx(@PathVariable String slug,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) Long branchId, @RequestParam(required = false) Long categoryId,
			@RequestParam(required = false) Long employeeId) {
		ReportType type = type(slug);
		ReportFilter filter = filter(from, to, branchId, categoryId, employeeId);
		byte[] body = excel.write(reports.build(type, filter));
		audit.record("EXPORT", "REPORT", type.slug(), null, filter);
		return download(type, filter, "xlsx", XLSX, body);
	}

	@GetMapping("/{slug}.pdf")
	@Transactional
	public ResponseEntity<byte[]> pdf(@PathVariable String slug,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) Long branchId, @RequestParam(required = false) Long categoryId,
			@RequestParam(required = false) Long employeeId) {
		ReportType type = type(slug);
		ReportFilter filter = filter(from, to, branchId, categoryId, employeeId);
		byte[] body = pdf.write(reports.build(type, filter));
		audit.record("EXPORT", "REPORT", type.slug(), null, filter);
		return download(type, filter, "pdf", MediaType.APPLICATION_PDF, body);
	}

	private static ReportType type(String slug) {
		return ReportType.fromSlug(slug)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Jenis laporan tidak dikenal"));
	}

	private static ReportFilter filter(LocalDate from, LocalDate to, Long branchId, Long categoryId, Long employeeId) {
		if (from == null || to == null) {
			throw ApiException.badRequest("Periode wajib diisi");
		}
		return new ReportFilter(from, to, branchId, categoryId, employeeId);
	}

	/** Nama berkas hanya slug dan periode; tidak memuat nama cabang atau karyawan. */
	private static ResponseEntity<byte[]> download(ReportType type, ReportFilter filter, String ext, MediaType mediaType,
			byte[] body) {
		String filename = type.slug() + "_" + filter.from() + "_" + filter.to() + "." + ext;
		return ResponseEntity.ok()
			.contentType(mediaType)
			.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
			.header("X-Content-Type-Options", "nosniff")
			.cacheControl(CacheControl.noStore().cachePrivate())
			.body(body);
	}

}
