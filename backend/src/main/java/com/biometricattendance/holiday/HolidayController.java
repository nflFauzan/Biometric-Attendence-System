package com.biometricattendance.holiday;

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

import com.biometricattendance.holiday.HolidayDtos.HolidayRequest;
import com.biometricattendance.holiday.HolidayDtos.HolidayResponse;

import jakarta.validation.Valid;

/** Kalender libur, dikelola Owner dan HR/Finance (K-049). */
@RestController
@RequestMapping("/api/holidays")
public class HolidayController {

	private final HolidayService service;

	public HolidayController(HolidayService service) {
		this.service = service;
	}

	@GetMapping
	List<HolidayResponse> list(@RequestParam int year) {
		return service.list(year);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	HolidayResponse create(@Valid @RequestBody HolidayRequest request) {
		return service.create(request);
	}

	@PutMapping("/{id}")
	HolidayResponse update(@PathVariable Long id, @Valid @RequestBody HolidayRequest request) {
		return service.update(id, request);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@PathVariable Long id) {
		service.delete(id);
	}

}
