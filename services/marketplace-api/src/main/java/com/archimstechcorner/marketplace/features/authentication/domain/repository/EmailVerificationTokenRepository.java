package com.archimstechcorner.marketplace.features.authentication.domain.repository;

import com.archimstechcorner.marketplace.features.authentication.domain.model.EmailVerificationToken;
import com.archimstechcorner.marketplace.features.authentication.domain.model.TokenPurpose;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Persistence for single-use email tokens. Private to the authentication feature. */
public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {

    Optional<EmailVerificationToken> findByTokenHashAndPurpose(String tokenHash, TokenPurpose purpose);
}
