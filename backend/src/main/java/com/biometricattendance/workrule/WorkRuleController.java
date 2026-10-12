package com.biometricattendance.workrule;

import java.time.LocalDate;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.biometricattendance.calculation.RuleLevel;
import com.biometricattendance.workrule.WorkRuleDtos.EffectiveRuleResponse;
import com.biometricattendance.workrule.WorkRuleDtos.WorkRuleRequest;
import com.biometricattendance.workrule.WorkRuleDtos.WorkRuleResponse;

import jakarta.validation.Valid;

/** Aturan kerja: dibaca Owner dan HR/Finance, diubah hanya oleh Owner (K-049, lihat SecurityConfig). */
@RestController
@RequestMapping("/api/work-rules")
public class WorkRuleController {

	private final WorkRuleService service;

	public WorkRuleController(WorkRuleService service) {
		this.service = service;
	}

	@GetMapping
	List<WorkRuleResponse> list(@RequestParam RuleLevel scope, @RequestParam(required = false) Long categoryId,
			@RequestParam(required = false) Long employeeId) {
		return service.list(scope, categoryId, employeeId);
	}

	@GetMapping("/effective")
	EffectiveRuleResponse effective(@RequestParam Long employeeId, @RequestParam LocalDate date) {
		return service.effective(employeeId, date);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	WorkRuleResponse create(@Valid @RequestBody WorkRuleRequest request) {
		return service.create(request);
	}

	@PutMapping("/{id}")
	WorkRuleResponse update(@PathVariable Long id, @Valid @RequestBody WorkRuleRequest request) {
		return service.update(id, request);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@PathVariable Long id) {
		service.delete(id);
	}

}
