package com.archimstechcorner.marketplace.features.authentication.application;

/** The pair returned when a session is issued or rotated: a signed access token and a raw refresh token. */
public record IssuedTokens(String accessToken, String refreshToken) {
}
