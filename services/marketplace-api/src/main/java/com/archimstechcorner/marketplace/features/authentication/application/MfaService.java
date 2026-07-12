package com.archimstechcorner.marketplace.features.authentication.application;

import com.archimstechcorner.marketplace.features.users.domain.model.User;
import com.archimstechcorner.marketplace.features.users.domain.model.UserMfaMethod;
import com.archimstechcorner.marketplace.features.users.domain.repository.MfaMethodRepository;
import com.archimstechcorner.marketplace.features.users.domain.repository.UserRepository;
import com.archimstechcorner.marketplace.shared.error.ApiException;
import com.archimstechcorner.marketplace.shared.error.ErrorCode;
import org.jboss.aerogear.security.otp.Totp;
import org.jboss.aerogear.security.otp.api.Base32;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Service
public class MfaService {

    private final UserRepository userRepository;
    private final MfaMethodRepository mfaMethodRepository;

    public MfaService(UserRepository userRepository, MfaMethodRepository mfaMethodRepository) {
        this.userRepository = userRepository;
        this.mfaMethodRepository = mfaMethodRepository;
    }

    @Transactional
    public String enrollTotp(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "User not found."));
        
        // Disable existing methods
        mfaMethodRepository.findByUserIdAndMethodType(userId, "TOTP")
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
    public void activateTotp(Long userId, String code) {
        UserMfaMethod method = mfaMethodRepository.findByUserIdAndMethodType(userId, "TOTP")
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "No TOTP enrollment found."));

        Totp totp = new Totp(method.getSecretKey());
        if (!totp.verify(code)) {
            throw new ApiException(ErrorCode.MFA_INVALID, "Invalid TOTP code.");
        }

        method.verify();
        mfaMethodRepository.save(method);
    }

    @Transactional(readOnly = true)
    public boolean verifyTotp(Long userId, String code) {
        return mfaMethodRepository.findByUserIdAndMethodType(userId, "TOTP")
                .filter(UserMfaMethod::isVerified)
                .map(method -> new Totp(method.getSecretKey()).verify(code))
                .orElse(false);
    }
    
    @Transactional(readOnly = true)
    public boolean isMfaEnabled(Long userId) {
        return mfaMethodRepository.findByUserIdAndMethodType(userId, "TOTP")
                .map(UserMfaMethod::isVerified)
                .orElse(false);
    }
}
