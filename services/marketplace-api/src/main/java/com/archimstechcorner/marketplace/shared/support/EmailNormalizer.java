package com.archimstechcorner.marketplace.shared.support;

import java.util.Locale;

/** Canonicalizes an email for uniqueness and lookup: trimmed and lowercased. */
public final class EmailNormalizer {

    private EmailNormalizer() {
    }

    public static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
