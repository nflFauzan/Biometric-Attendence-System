package com.biometricattendance.holiday;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.biometricattendance.audit.AuditService;
import com.biometricattendance.common.ApiException;
import com.biometricattendance.holiday.HolidayDtos.HolidayRequest;
import com.biometricattendance.holiday.HolidayDtos.HolidayResponse;

@Service
public class HolidayService {

	static final String ENTITY = "HOLIDAY";

	private final HolidayRepository repository;
	private final AuditService audit;

	public HolidayService(HolidayRepository repository, AuditService audit) {
		this.repository = repository;
		this.audit = audit;
	}

	@Transactional(readOnly = true)
	public List<HolidayResponse> list(int year) {
		return repository
			.findByHolidayDateBetweenOrderByHolidayDateAsc(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31))
			.stream()
			.map(HolidayResponse::from)
			.toList();
	}

	@Transactional
	public HolidayResponse create(HolidayRequest request) {
		if (repository.existsByHolidayDate(request.date())) {
			throw ApiException.conflict("Tanggal tersebut sudah tercatat sebagai hari libur");
		}
		Holiday holiday = new Holiday();
		apply(holiday, request);
		HolidayResponse created = HolidayResponse.from(repository.save(holiday));
		audit.record("CREATE", ENTITY, created.id(), null, created);
		return created;
	}

	@Transactional
	public HolidayResponse update(Long id, HolidayRequest request) {
		Holiday holiday = find(id);
		if (repository.existsByHolidayDateAndIdNot(request.date(), id)) {
			throw ApiException.conflict("Tanggal tersebut sudah tercatat sebagai hari libur");
		}
		HolidayResponse before = HolidayResponse.from(holiday);
		apply(holiday, request);
		HolidayResponse after = HolidayResponse.from(repository.save(holiday));
		audit.record("UPDATE", ENTITY, id, before, after);
		return after;
	}

	@Transactional
	public void delete(Long id) {
		Holiday holiday = find(id);
		HolidayResponse before = HolidayResponse.from(holiday);
		repository.delete(holiday);
		audit.record("DELETE", ENTITY, id, before, null);
	}

	private Holiday find(Long id) {
		return repository.findById(id).orElseThrow(() -> ApiException.notFound("Hari libur"));
	}

	private void apply(Holiday holiday, HolidayRequest request) {
		holiday.setHolidayDate(request.date());
		holiday.setName(request.name().trim());
	}

}
