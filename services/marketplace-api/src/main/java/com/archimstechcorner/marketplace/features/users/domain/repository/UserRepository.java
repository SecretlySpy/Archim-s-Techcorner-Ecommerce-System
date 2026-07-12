package com.archimstechcorner.marketplace.features.users.domain.repository;

import com.archimstechcorner.marketplace.features.users.domain.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/** Persistence for {@link User}. Private to the users feature. */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailNormalizedAndDeletedAtIsNull(String emailNormalized);

    boolean existsByEmailNormalized(String emailNormalized);

    Optional<User> findByPublicId(UUID publicId);
}
