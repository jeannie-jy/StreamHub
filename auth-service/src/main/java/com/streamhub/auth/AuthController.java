package com.streamhub.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.streamhub.common.api.ApiResponse;
import com.streamhub.common.api.BusinessException;
import com.streamhub.common.api.ErrorCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private static final Duration SESSION_DURATION = Duration.ofDays(7);

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthController(UserAccountRepository userAccountRepository, PasswordEncoder passwordEncoder) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/register")
    public ApiResponse<AuthTokenResponse> register(@Valid @RequestBody RegisterRequest request) {
        try {
            long userId = userAccountRepository.createUser(
                    request.username().trim(),
                    request.nickname().trim(),
                    passwordEncoder.encode(request.password()));
            return ApiResponse.success(createSession(userId));
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "用户名已存在");
        }
    }

    @PostMapping("/login")
    public ApiResponse<AuthTokenResponse> login(@Valid @RequestBody LoginRequest request) {
        UserAccount user = userAccountRepository.findByUsername(request.username().trim())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "用户名或密码错误"));
        if (!passwordEncoder.matches(request.password(), user.passwordHash())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }
        if (!"ACTIVE".equals(user.status())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "用户已被禁用");
        }
        return ApiResponse.success(createSession(user.id()));
    }

    private AuthTokenResponse createSession(long userId) {
        String token = UUID.randomUUID().toString().replace("-", "");
        Instant expiresAt = Instant.now().plus(SESSION_DURATION);
        userAccountRepository.createSession(token, userId, expiresAt);
        return new AuthTokenResponse(userId, token, expiresAt);
    }

    public record RegisterRequest(
            @NotBlank @Size(min = 3, max = 64) String username,
            @NotBlank @Size(min = 1, max = 64) String nickname,
            @NotBlank @Size(min = 8, max = 128) String password) {
    }

    public record LoginRequest(
            @NotBlank @Size(max = 64) String username,
            @NotBlank @Size(max = 128) String password) {
    }

    public record AuthTokenResponse(long userId, String accessToken, Instant expiresAt) {
    }
}
