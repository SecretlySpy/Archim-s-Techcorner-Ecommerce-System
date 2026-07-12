package com.archimstechcorner.marketplace.features.users.domain.repository;

import com.archimstechcorner.marketplace.features.users.domain.model.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Persistence for {@link UserRole} assignments. */
public interface UserRoleRepository extends JpaRepository<UserRole, Long> {

    /** Role ids currently active (not revoked, not expired) for a user. */
    @Query("""
            SELECT ur.roleId FROM UserRole ur
            WHERE ur.userId = :userId
              AND ur.activeMarker IS NOT NULL
              AND (ur.expiresAt IS NULL OR ur.expiresAt > :now)
            """)
    List<Long> findActiveRoleIds(@Param("userId") Long userId, @Param("now") Instant now);

    /** The current active assignment of a specific role to a user, if any. */
    Optional<UserRole> findByUserIdAndRoleIdAndActiveMarker(Long userId, Long roleId, Byte activeMarker);
}
