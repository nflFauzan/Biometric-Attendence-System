package com.biometricattendance.leave;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

	boolean existsByResubmittedFromId(Long resubmittedFromId);

	@Query("""
			select r from LeaveRequest r join fetch r.employee e join fetch e.branch left join fetch r.leaveType
			where r.id = :id
			""")
	Optional<LeaveRequest> findDetailed(@Param("id") Long id);

	/** Pencarian publik menurut token verifikasi surat; draft tidak pernah ditemukan. */
	@Query("""
			select r from LeaveRequest r join fetch r.employee e join fetch e.branch left join fetch r.leaveType
			where r.verificationToken = :token and r.status <> com.biometricattendance.leave.LeaveStatus.DRAFT
			""")
	Optional<LeaveRequest> findPublicByToken(@Param("token") String token);

	/** Daftar untuk Owner/HR. Rentang tanggal wajib diisi (diisi bawaan oleh service) agar tidak ada parameter tanggal null. */
	@Query("""
			select r from LeaveRequest r join fetch r.employee e join fetch e.branch join fetch r.leaveType
			where (:status is null or r.status = :status)
			  and (:employeeId is null or e.id = :employeeId)
			  and (:branchId is null or e.branch.id = :branchId)
			  and r.status <> com.biometricattendance.leave.LeaveStatus.DRAFT
			  and r.startDate <= :to and r.endDate >= :from
			order by coalesce(r.submittedAt, r.createdAt) desc, r.id desc
			""")
	List<LeaveRequest> search(@Param("status") LeaveStatus status, @Param("employeeId") Long employeeId,
			@Param("branchId") Long branchId, @Param("from") LocalDate from, @Param("to") LocalDate to);

	@Query("""
			select r from LeaveRequest r join fetch r.employee e join fetch e.branch left join fetch r.leaveType
			where e.id = :employeeId
			order by r.startDate desc nulls first, r.id desc
			""")
	List<LeaveRequest> findForEmployee(@Param("employeeId") Long employeeId);

	/** Pengajuan dengan status tertentu yang beririsan dengan rentang tanggal (untuk rekap dan pemakaian). */
	@Query("""
			select r from LeaveRequest r join fetch r.leaveType
			where r.employee.id in :employeeIds and r.status in :statuses
			  and r.startDate <= :to and r.endDate >= :from
			""")
	List<LeaveRequest> findOverlapping(@Param("employeeIds") Collection<Long> employeeIds,
			@Param("statuses") Collection<LeaveStatus> statuses, @Param("from") LocalDate from,
			@Param("to") LocalDate to);

	@Query("""
			select r from LeaveRequest r join fetch r.employee e join fetch e.branch join fetch r.leaveType
			where r.status in :statuses
			order by coalesce(r.submittedAt, r.createdAt) desc, r.id desc
			""")
	List<LeaveRequest> findByStatusDetailed(@Param("statuses") Collection<LeaveStatus> statuses);

	long countByStatusIn(Collection<LeaveStatus> statuses);

}
