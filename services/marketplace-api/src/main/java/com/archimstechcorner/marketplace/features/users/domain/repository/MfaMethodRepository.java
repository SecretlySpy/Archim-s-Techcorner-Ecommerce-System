package com.archimstechcorner.marketplace.features.users.domain.repository;

import com.archimstechcorner.marketplace.features.users.domain.model.UserMfaMethod;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MfaMethodRepository extends JpaRepository<UserMfaMethod, Long> {
    Optional<UserMfaMethod> findByUserIdAndMethodType(Long userId, String methodType);
}
