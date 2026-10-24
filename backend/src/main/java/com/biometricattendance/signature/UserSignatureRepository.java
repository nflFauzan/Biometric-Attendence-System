package com.biometricattendance.signature;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSignatureRepository extends JpaRepository<UserSignature, Long> {

	Optional<UserSignature> findByUserAccountId(Long userAccountId);

}
