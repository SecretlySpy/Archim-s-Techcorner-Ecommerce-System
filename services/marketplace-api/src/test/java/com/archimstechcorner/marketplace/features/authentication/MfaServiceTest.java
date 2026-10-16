package com.archimstechcorner.marketplace.features.authentication;

import com.archimstechcorner.marketplace.features.authentication.application.MfaService;
import com.archimstechcorner.marketplace.features.users.domain.model.User;
import com.archimstechcorner.marketplace.features.users.domain.model.UserMfaMethod;
import com.archimstechcorner.marketplace.features.users.domain.model.UserRecoveryCode;
import com.archimstechcorner.marketplace.features.users.domain.model.UserStatus;
import com.archimstechcorner.marketplace.features.users.domain.repository.MfaMethodRepository;
import com.archimstechcorner.marketplace.features.users.domain.repository.UserRecoveryCodeRepository;
import com.archimstechcorner.marketplace.features.users.domain.repository.UserRepository;
import com.archimstechcorner.marketplace.shared.error.ApiException;
import org.jboss.aerogear.security.otp.Totp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MfaServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private MfaMethodRepository mfaMethodRepository;

    @Mock
    private UserRecoveryCodeRepository recoveryCodeRepository;

    private MfaService mfaService;
    private User user;

    @BeforeEach
    void setUp() {
        mfaService = new MfaService(userRepository, mfaMethodRepository, recoveryCodeRepository);
        user = User.create();
        user.setEmailDisplay("alice@example.com");
        user.setEmailNormalized("alice@example.com");
        user.setStatus(UserStatus.ACTIVE);
        lenient().when(userRepository.findById(1L)).thenReturn(Optional.of(user));
    }

    @Test
    void enrollTotpPreservesActiveVerifiedMethodUntilActivation() {
        UserMfaMethod active = UserMfaMethod.create(user, "TOTP", "ACTIVE_SECRET");
        active.verify();

        when(mfaMethodRepository.findByUserIdAndMethodTypeAndVerifiedFalse(1L, "TOTP"))
                .thenReturn(Optional.empty());

        String uri = mfaService.enrollTotp(1L);

        assertThat(uri).startsWith("otpauth://totp/ArchimsTechCorner:alice%40example.com?secret=");
        // Active verified method must NOT have been deleted on enrollment
        verify(mfaMethodRepository, never()).delete(active);
        verify(mfaMethodRepository).save(any(UserMfaMethod.class));
    }

    @Test
    void activateTotpRejectsInvalidCodeWithoutRetiringOldMethod() {
        UserMfaMethod pending = UserMfaMethod.create(user, "TOTP", "B342W2C35DFM3TUL");
        when(mfaMethodRepository.findByUserIdAndMethodTypeAndVerifiedFalse(1L, "TOTP"))
                .thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> mfaService.activateTotp(1L, "000000"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Invalid TOTP code");

        verify(mfaMethodRepository, never()).delete(any());
        assertThat(pending.isVerified()).isFalse();
    }

    @Test
    void activateTotpWithValidCodeRetiresOldMethodAndReturnsEightRecoveryCodes() {
        String secret = "B342W2C35DFM3TUL";
        UserMfaMethod pending = UserMfaMethod.create(user, "TOTP", secret);
        UserMfaMethod oldVerified = UserMfaMethod.create(user, "TOTP", "OLD_SECRET");
        oldVerified.verify();

        when(mfaMethodRepository.findByUserIdAndMethodTypeAndVerifiedFalse(1L, "TOTP"))
                .thenReturn(Optional.of(pending));
        when(mfaMethodRepository.findByUserIdAndMethodTypeAndVerifiedTrue(1L, "TOTP"))
                .thenReturn(Optional.of(oldVerified));

        Totp totp = new Totp(secret);
        String currentCode = totp.now();

        List<String> recoveryCodes = mfaService.activateTotp(1L, currentCode);

        // Verification succeeded
        verify(mfaMethodRepository).delete(oldVerified);
        assertThat(pending.isVerified()).isTrue();
        verify(mfaMethodRepository).save(pending);

        // Generates 8 recovery codes
        assertThat(recoveryCodes).hasSize(8);
        verify(recoveryCodeRepository, times(8)).save(any(UserRecoveryCode.class));
    }

    @Test
    void verifyTotpConsumesSingleUseRecoveryCode() {
        when(mfaMethodRepository.findByUserIdAndMethodTypeAndVerifiedTrue(1L, "TOTP"))
                .thenReturn(Optional.empty());

        String rawCode = "RECOVERY01";
        String codeHash = com.archimstechcorner.marketplace.shared.support.SecureTokens.sha256Hex(rawCode);
        UserRecoveryCode recoveryCode = UserRecoveryCode.create(user, codeHash);

        List<UserRecoveryCode> codesList = new ArrayList<>(List.of(recoveryCode));
        when(recoveryCodeRepository.findByUserIdAndUsedFalse(1L)).thenReturn(codesList);

        // First verification should succeed
        boolean firstAttempt = mfaService.verifyTotp(1L, "recovery-01");
        assertThat(firstAttempt).isTrue();
        assertThat(recoveryCode.isUsed()).isTrue();
        verify(recoveryCodeRepository).save(recoveryCode);

        // Second verification should fail as it's marked used
        when(recoveryCodeRepository.findByUserIdAndUsedFalse(1L)).thenReturn(List.of());
        boolean secondAttempt = mfaService.verifyTotp(1L, "recovery-01");
        assertThat(secondAttempt).isFalse();
    }
}
