package com.archimstechcorner.marketplace.features.users.domain.repository;

import com.archimstechcorner.marketplace.features.users.domain.model.UserRecoveryCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserRecoveryCodeRepository extends JpaRepository<UserRecoveryCode, Long> {
    List<UserRecoveryCode> findByUserIdAndUsedFalse(Long userId);
    void deleteByUserId(Long userId);
}
