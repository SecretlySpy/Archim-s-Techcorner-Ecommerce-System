package com.archimstechcorner.marketplace.features.authentication.application;

/** Privacy-safe device context captured at login/refresh. IP and user-agent are stored hashed only. */
public record DeviceInfo(String label, String ipHash, String userAgentHash) {
}
