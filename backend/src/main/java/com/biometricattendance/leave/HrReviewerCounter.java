package com.biometricattendance.leave;

import org.springframework.stereotype.Component;

import com.biometricattendance.account.Role;
import com.biometricattendance.account.UserAccountRepository;

/** Menghitung akun HR/Finance aktif yang bisa meninjau pengajuan seseorang (K-079 tidak boleh memutus sendiri). */
@Component
public class HrReviewerCounter {

	private final UserAccountRepository accounts;

	public HrReviewerCounter(UserAccountRepository accounts) {
		this.accounts = accounts;
	}

	/** Akun HR/Finance aktif selain akun yang terhubung ke karyawan pemohon. */
	public long countOtherActiveHr(Long requesterEmployeeId) {
		return accounts.countActiveByRoleExcludingEmployee(Role.HR_FINANCE, requesterEmployeeId);
	}

}
