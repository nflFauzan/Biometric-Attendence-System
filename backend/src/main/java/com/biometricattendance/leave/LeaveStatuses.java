package com.biometricattendance.leave;

import java.util.EnumSet;
import java.util.Set;

/** Kumpulan status yang dipakai berulang (dipisah dari enum agar tidak ada static init melingkar). */
public final class LeaveStatuses {

	/** Menunggu keputusan. */
	public static final Set<LeaveStatus> PENDING = Set.copyOf(
			EnumSet.of(LeaveStatus.PERLU_TINJAU_HR, LeaveStatus.PERLU_TINJAU_OWNER));

	/** Dihitung untuk cek tumpang tindih (K-075). */
	public static final Set<LeaveStatus> ACTIVE = Set.copyOf(
			EnumSet.of(LeaveStatus.PERLU_TINJAU_HR, LeaveStatus.PERLU_TINJAU_OWNER, LeaveStatus.DISETUJUI));

	private LeaveStatuses() {
	}

}
