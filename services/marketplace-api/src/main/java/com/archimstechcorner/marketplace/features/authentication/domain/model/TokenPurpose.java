package com.archimstechcorner.marketplace.features.authentication.domain.model;

/** Discriminator for single-use email tokens (mirrors the {@code ck_email_verification_tokens__purpose} check). */
public enum TokenPurpose {
    EMAIL_VERIFY,
    PASSWORD_RESET
}
