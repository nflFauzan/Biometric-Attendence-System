package com.biometricattendance.leave;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LeaveAttachmentRepository extends JpaRepository<LeaveAttachment, Long> {

	List<LeaveAttachment> findByLeaveRequestIdOrderByIdAsc(Long leaveRequestId);

	List<LeaveAttachment> findByLeaveRequestIdIn(Collection<Long> leaveRequestIds);

	Optional<LeaveAttachment> findByIdAndLeaveRequestId(Long id, Long leaveRequestId);

	long countByLeaveRequestId(Long leaveRequestId);

}
