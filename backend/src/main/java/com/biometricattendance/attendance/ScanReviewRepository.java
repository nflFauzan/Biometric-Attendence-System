package com.biometricattendance.attendance;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ScanReviewRepository extends JpaRepository<ScanReview, Long> {

	List<ScanReview> findByScanIdIn(Collection<Long> scanIds);

}
