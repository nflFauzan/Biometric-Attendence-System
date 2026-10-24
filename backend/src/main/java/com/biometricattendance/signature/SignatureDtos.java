package com.biometricattendance.signature;

import java.time.Instant;

public final class SignatureDtos {

	private SignatureDtos() {
	}

	/** Status spesimen milik sendiri; {@code updatedAt} null bila belum ada. */
	public record SignatureStatus(boolean exists, Instant updatedAt) {
	}

}
