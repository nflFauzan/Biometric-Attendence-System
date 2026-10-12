package com.biometricattendance.calculation;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.biometricattendance.calculation.CalculationDtos.DailyPreviewRequest;
import com.biometricattendance.calculation.CalculationDtos.DailyPreviewResponse;

import jakarta.validation.Valid;

/** Simulasi perhitungan untuk Owner dan HR/Finance. POST karena membawa daftar jam scan, bukan mengubah data. */
@RestController
@RequestMapping("/api/calculation")
public class CalculationController {

	private final CalculationService service;

	public CalculationController(CalculationService service) {
		this.service = service;
	}

	@PostMapping("/daily-preview")
	DailyPreviewResponse dailyPreview(@Valid @RequestBody DailyPreviewRequest request) {
		return service.preview(request);
	}

}
