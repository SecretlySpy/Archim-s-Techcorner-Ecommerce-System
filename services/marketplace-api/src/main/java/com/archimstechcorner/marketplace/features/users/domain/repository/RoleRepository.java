package com.archimstechcorner.marketplace.features.users.domain.repository;

import com.archimstechcorner.marketplace.features.users.domain.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Persistence for {@link Role}. */
public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByCode(String code);

    List<Role> findByIdIn(List<Long> ids);
}
