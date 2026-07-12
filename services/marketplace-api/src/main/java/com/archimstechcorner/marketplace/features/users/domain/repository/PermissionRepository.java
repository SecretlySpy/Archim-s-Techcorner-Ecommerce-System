package com.archimstechcorner.marketplace.features.users.domain.repository;

import com.archimstechcorner.marketplace.features.users.domain.model.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/** Persistence for {@link Permission}, including the role -> permission resolution used for authorities. */
public interface PermissionRepository extends JpaRepository<Permission, Long> {

    /** Active permission codes granted to any of the given roles (joins role_permissions). */
    @Query(value = """
            SELECT DISTINCT p.code
            FROM permissions p
            JOIN role_permissions rp ON rp.permission_id = p.id
            WHERE rp.role_id IN (:roleIds) AND p.is_active = 1
            """, nativeQuery = true)
    List<String> findActiveCodesByRoleIds(@Param("roleIds") Collection<Long> roleIds);
}
