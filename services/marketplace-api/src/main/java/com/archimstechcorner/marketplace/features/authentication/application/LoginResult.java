package com.archimstechcorner.marketplace.features.authentication.application;

import java.util.UUID;

/** Outcome of a successful login: the token pair plus a small principal summary for the response body. 
 *  If MFA is required, tokens is null and mfaChallengeToken is provided.
 */
public record LoginResult(IssuedTokens tokens, UUID publicId, String email, boolean emailVerified, String mfaChallengeToken) {
}
