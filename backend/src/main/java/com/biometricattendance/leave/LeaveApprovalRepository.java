package com.biometricattendance.leave;

import java.util.Collection;
import java.util.List;

import java.time.Instant;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LeaveApprovalRepository extends JpaRepository<LeaveApproval, Long> {

	java.util.Optional<LeaveApproval> findByLeaveRequestIdAndStage(Long leaveRequestId, ApprovalStage stage);

	List<LeaveApproval> findByLeaveRequestIdOrderByIdAsc(Long leaveRequestId);

	List<LeaveApproval> findByLeaveRequestIdIn(Collection<Long> leaveRequestIds);

	/** Keputusan tahap Owner yang dicatat orang lain (bukan lewat web) sejak waktu tertentu, terbaru dulu. */
	@Query("""
			select a from LeaveApproval a
			where a.stage = com.biometricattendance.leave.ApprovalStage.OWNER
			  and a.method <> com.biometricattendance.leave.DecisionMethod.WEB
			  and a.recordedAt >= :since
			order by a.recordedAt desc, a.id desc
			""")
	List<LeaveApproval> findRecordedForOwnerSince(@Param("since") Instant since, Pageable page);

}
