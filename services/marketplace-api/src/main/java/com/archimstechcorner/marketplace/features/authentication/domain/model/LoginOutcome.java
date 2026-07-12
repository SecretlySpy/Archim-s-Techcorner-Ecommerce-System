package com.archimstechcorner.marketplace.features.authentication.domain.model;

/** Outcome recorded for each login attempt (mirrors the {@code ck_user_login_events__outcome} check). */
public enum LoginOutcome {
    SUCCESS,
    FAILED,
    LOCKED,
    MFA_REQUIRED
}
