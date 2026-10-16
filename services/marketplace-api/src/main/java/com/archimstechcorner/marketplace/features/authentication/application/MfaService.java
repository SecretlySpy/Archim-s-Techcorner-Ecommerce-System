package com.archimstechcorner.marketplace.features.authentication.application;

import com.archimstechcorner.marketplace.features.users.domain.model.User;
import com.archimstechcorner.marketplace.features.users.domain.model.UserMfaMethod;
import com.archimstechcorner.marketplace.features.users.domain.model.UserRecoveryCode;
import com.archimstechcorner.marketplace.features.users.domain.repository.MfaMethodRepository;
import com.archimstechcorner.marketplace.features.users.domain.repository.UserRecoveryCodeRepository;
import com.archimstechcorner.marketplace.features.users.domain.repository.UserRepository;
import com.archimstechcorner.marketplace.shared.error.ApiException;
import com.archimstechcorner.marketplace.shared.error.ErrorCode;
import com.archimstechcorner.marketplace.shared.support.SecureTokens;
import org.jboss.aerogear.security.otp.Totp;
import org.jboss.aerogear.security.otp.api.Base32;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
public class MfaService {

    private final UserRepository userRepository;
    private final MfaMethodRepository mfaMethodRepository;
    private final UserRecoveryCodeRepository recoveryCodeRepository;

    public MfaService(UserRepository userRepository,
                      MfaMethodRepository mfaMethodRepository,
                      UserRecoveryCodeRepository recoveryCodeRepository) {
        this.userRepository = userRepository;
        this.mfaMethodRepository = mfaMethodRepository;
        this.recoveryCodeRepository = recoveryCodeRepository;
    }

    @Transactional
    public String enrollTotp(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "User not found."));

        // Delete any unverified pending enrollment without touching the verified method (G07 safe re-enrollment)
        mfaMethodRepository.findByUserIdAndMethodTypeAndVerifiedFalse(userId, "TOTP")
                .ifPresent(mfaMethodRepository::delete);

        String secretKey = Base32.random();
        UserMfaMethod mfaMethod = UserMfaMethod.create(user, "TOTP", secretKey);
        mfaMethodRepository.save(mfaMethod);

        return "otpauth://totp/ArchimsTechCorner:%s?secret=%s&issuer=ArchimsTechCorner".formatted(
                URLEncoder.encode(user.getEmailDisplay(), StandardCharsets.UTF_8),
                secretKey
        );
    }

    @Transactional
    public List<String> activateTotp(Long userId, String code) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "User not found."));

        UserMfaMethod pendingMethod = mfaMethodRepository.findByUserIdAndMethodTypeAndVerifiedFalse(userId, "TOTP")
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "No pending TOTP enrollment found."));

        Totp totp = new Totp(pendingMethod.getSecretKey());
        if (!totp.verify(code)) {
            throw new ApiException(ErrorCode.MFA_INVALID, "Invalid TOTP code.");
        }

        // Verification succeeded: retire old verified method now that new one is proven
        mfaMethodRepository.findByUserIdAndMethodTypeAndVerifiedTrue(userId, "TOTP")
                .ifPresent(mfaMethodRepository::delete);

        pendingMethod.verify();
        mfaMethodRepository.save(pendingMethod);

        // Generate 8 single-use backup recovery codes
        recoveryCodeRepository.deleteByUserId(userId);
        List<String> rawRecoveryCodes = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            String rawCode = SecureTokens.generate().replace("-", "").substring(0, 10).toUpperCase();
            rawRecoveryCodes.add(rawCode);
            String codeHash = SecureTokens.sha256Hex(rawCode);
            recoveryCodeRepository.save(UserRecoveryCode.create(user, codeHash));
        }

        return rawRecoveryCodes;
    }

    @Transactional
    public boolean verifyTotp(Long userId, String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        String trimmed = code.trim().replace("-", "").toUpperCase();

        // 1. Check TOTP verification against active verified method
        boolean totpValid = mfaMethodRepository.findByUserIdAndMethodTypeAndVerifiedTrue(userId, "TOTP")
                .map(method -> new Totp(method.getSecretKey()).verify(trimmed))
                .orElse(false);

        if (totpValid) {
            return true;
        }

        // 2. Check single-use recovery code
        String hash = SecureTokens.sha256Hex(trimmed);
        for (UserRecoveryCode recoveryCode : recoveryCodeRepository.findByUserIdAndUsedFalse(userId)) {
            if (recoveryCode.getCodeHash().equals(hash)) {
                recoveryCode.markUsed();
                recoveryCodeRepository.save(recoveryCode);
                return true;
            }
        }

        return false;
    }

    @Transactional(readOnly = true)
    public boolean isMfaEnabled(Long userId) {
        return mfaMethodRepository.findByUserIdAndMethodTypeAndVerifiedTrue(userId, "TOTP").isPresent();
    }
}
