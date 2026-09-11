package com.streamhub.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import com.streamhub.common.api.ApiResponse;
import com.streamhub.common.api.BusinessException;
import com.streamhub.common.api.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private static final String WS_TICKET_PREFIX = "streamhub:auth:ws-ticket:";
    private static final DefaultRedisScript<String> CONSUME_TICKET = new DefaultRedisScript<>(
            "local value = redis.call('GET', KEYS[1]); "
                    + "if value then redis.call('DEL', KEYS[1]); end; return value",
            String.class);

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redisTemplate;
    private final AuthProperties properties;

    public AuthController(
            UserAccountRepository userAccountRepository,
            PasswordEncoder passwordEncoder,
            StringRedisTemplate redisTemplate,
            AuthProperties properties) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    @PostMapping("/register")
    public ApiResponse<AuthTokenResponse> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletResponse response) {
        try {
            long userId = userAccountRepository.createUser(
                    request.username().trim(),
                    request.nickname().trim(),
                    passwordEncoder.encode(request.password()));
            AuthTokenResponse session = createSession(userId, "USER");
            setRefreshCookie(response, session.refreshToken());
            return ApiResponse.success(session.withoutRefreshToken());
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "用户名已存在");
        }
    }

    @PostMapping("/login")
    public ApiResponse<AuthTokenResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response) {
        UserAccount user = userAccountRepository.findByUsername(request.username().trim())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "用户名或密码错误"));
        if (!passwordEncoder.matches(request.password(), user.passwordHash())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }
        if (!"ACTIVE".equals(user.status())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "用户已被禁用");
        }
        AuthTokenResponse session = createSession(user.id(), user.role());
        setRefreshCookie(response, session.refreshToken());
        return ApiResponse.success(session.withoutRefreshToken());
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthTokenResponse> refresh(
            HttpServletRequest request,
            HttpServletResponse response) {
        String refreshToken = cookieValue(request, properties.getRefreshCookieName());
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Refresh Token 缺失");
        }
        String tokenHash = sha256(refreshToken);
        RefreshSession current = userAccountRepository.findActiveRefreshSession(tokenHash, Instant.now())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "Refresh Token 无效或已过期"));
        userAccountRepository.revokeRefreshSession(tokenHash);
        AuthTokenResponse next = createSession(current.userId(), current.role());
        setRefreshCookie(response, next.refreshToken());
        return ApiResponse.success(next.withoutRefreshToken());
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            HttpServletRequest request,
            HttpServletResponse response) {
        String refreshToken = cookieValue(request, properties.getRefreshCookieName());
        if (refreshToken != null && !refreshToken.isBlank()) {
            userAccountRepository.revokeRefreshSession(sha256(refreshToken));
        }
        clearRefreshCookie(response);
        return ApiResponse.successVoid();
    }

    @GetMapping("/me")
    public ApiResponse<AuthSession> me(
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return ApiResponse.success(currentSession(authorization));
    }

    @PostMapping("/ws-ticket")
    public ApiResponse<WsTicket> wsTicket(
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        AuthSession session = currentSession(authorization);
        String ticket = randomToken();
        redisTemplate.opsForValue().set(
                WS_TICKET_PREFIX + sha256(ticket),
                String.valueOf(session.userId()),
                properties.getWsTicketTtl());
        return ApiResponse.success(new WsTicket(ticket, Instant.now().plus(properties.getWsTicketTtl())));
    }

    @PostMapping("/ws-ticket/introspect")
    public ApiResponse<AuthSession> introspectTicket(@Valid @RequestBody TicketRequest request) {
        String value = redisTemplate.execute(
                CONSUME_TICKET,
                List.of(WS_TICKET_PREFIX + sha256(request.ticket())));
        if (value == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "WebSocket Ticket 无效或已消费");
        }
        long userId;
        try {
            userId = Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "WebSocket Ticket 无效");
        }
        return ApiResponse.success(userAccountRepository.findActiveUserSession(userId, Instant.now())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "用户已被禁用")));
    }

    @PostMapping("/introspect")
    public ApiResponse<AuthSession> introspect(
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return ApiResponse.success(currentSession(authorization));
    }

    private AuthSession currentSession(String authorization) {
        String token = bearerToken(authorization);
        return userAccountRepository.findActiveSession(token, Instant.now())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "Token 无效或已过期"));
    }

    private AuthTokenResponse createSession(long userId, String role) {
        String token = randomToken();
        Instant expiresAt = Instant.now().plus(properties.getAccessTtl());
        userAccountRepository.createSession(token, userId, expiresAt);
        String refreshToken = randomToken();
        userAccountRepository.createRefreshSession(
                sha256(refreshToken),
                userId,
                Instant.now().plus(properties.getRefreshTtl()));
        return new AuthTokenResponse(userId, token, expiresAt, role, refreshToken);
    }

    private void setRefreshCookie(HttpServletResponse response, String token) {
        ResponseCookie cookie = ResponseCookie.from(properties.getRefreshCookieName(), token)
                .httpOnly(true)
                .secure(properties.isCookieSecure())
                .sameSite(properties.getCookieSameSite())
                .path("/api/v1/auth")
                .maxAge(properties.getRefreshTtl())
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(properties.getRefreshCookieName(), "")
                .httpOnly(true)
                .secure(properties.isCookieSecure())
                .sameSite(properties.getCookieSameSite())
                .path("/api/v1/auth")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private String cookieValue(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        for (var cookie : request.getCookies()) {
            if (name.equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }

    private String bearerToken(String authorization) {
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "缺少 Bearer Token");
        }
        String token = authorization.substring(7).trim();
        if (token.isEmpty()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "缺少 Bearer Token");
        }
        return token;
    }

    private String randomToken() {
        return UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK 缺少 SHA-256", exception);
        }
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

    public record TicketRequest(@NotBlank @Size(max = 128) String ticket) {
    }

    public record WsTicket(String ticket, Instant expiresAt) {
    }

    public record AuthTokenResponse(
            long userId,
            String accessToken,
            Instant expiresAt,
            String role,
            String refreshToken) {
        public AuthTokenResponse withoutRefreshToken() {
            return new AuthTokenResponse(userId, accessToken, expiresAt, role, null);
        }
    }
}
