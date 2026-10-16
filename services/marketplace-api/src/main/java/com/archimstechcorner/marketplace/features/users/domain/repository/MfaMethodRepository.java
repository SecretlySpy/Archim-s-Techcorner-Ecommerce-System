package com.archimstechcorner.marketplace.features.users.domain.repository;

import com.archimstechcorner.marketplace.features.users.domain.model.UserMfaMethod;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MfaMethodRepository extends JpaRepository<UserMfaMethod, Long> {
    Optional<UserMfaMethod> findByUserIdAndMethodType(Long userId, String methodType);
    Optional<UserMfaMethod> findByUserIdAndMethodTypeAndVerifiedTrue(Long userId, String methodType);
    Optional<UserMfaMethod> findByUserIdAndMethodTypeAndVerifiedFalse(Long userId, String methodType);
    List<UserMfaMethod> findAllByUserIdAndMethodType(Long userId, String methodType);
}
