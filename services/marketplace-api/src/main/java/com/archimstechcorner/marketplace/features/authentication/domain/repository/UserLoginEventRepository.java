package com.archimstechcorner.marketplace.features.authentication.domain.repository;

import com.archimstechcorner.marketplace.features.authentication.domain.model.UserLoginEvent;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for immutable login/device history events. */
public interface UserLoginEventRepository extends JpaRepository<UserLoginEvent, Long> {
}
