package com.archimstechcorner.marketplace.features.users.domain.model;

/** Lifecycle status of a login principal (mirrors the {@code ck_users__status} check constraint). */
public enum UserStatus {
    PENDING_VERIFICATION,
    ACTIVE,
    LOCKED,
    SUSPENDED,
    DISABLED,
    DELETED
}
