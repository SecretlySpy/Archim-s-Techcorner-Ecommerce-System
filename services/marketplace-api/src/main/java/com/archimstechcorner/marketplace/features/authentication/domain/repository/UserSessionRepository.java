package com.archimstechcorner.marketplace.features.authentication.domain.repository;

import com.archimstechcorner.marketplace.features.authentication.domain.model.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence for refresh sessions. Private to the authentication feature. */
public interface UserSessionRepository extends JpaRepository<UserSession, Long> {

    Optional<UserSession> findByTokenHash(String tokenHash);

    /** All sessions in a rotation family — used to revoke the family on reuse detection. */
    List<UserSession> findByRotationFamilyId(UUID rotationFamilyId);

    /** All still-active sessions for a user — used to revoke on password reset / revoke-all. */
    List<UserSession> findByUserIdAndRevokedAtIsNull(Long userId);
}
