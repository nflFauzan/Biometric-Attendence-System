package com.biometricattendance.leave;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LeaveApprovalRepository extends JpaRepository<LeaveApproval, Long> {

	java.util.Optional<LeaveApproval> findByLeaveRequestIdAndStage(Long leaveRequestId, ApprovalStage stage);

	List<LeaveApproval> findByLeaveRequestIdOrderByIdAsc(Long leaveRequestId);

	List<LeaveApproval> findByLeaveRequestIdIn(Collection<Long> leaveRequestIds);

}
