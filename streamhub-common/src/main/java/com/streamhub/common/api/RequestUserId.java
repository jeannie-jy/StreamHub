package com.streamhub.common.api;

import jakarta.servlet.http.HttpServletRequest;

public final class RequestUserId {
    public static final String USER_ID_HEADER = "X-User-Id";

    private RequestUserId() {
    }

    public static long required(HttpServletRequest request) {
        String rawUserId = request.getHeader(USER_ID_HEADER);
        if (rawUserId == null || rawUserId.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "请求缺少 X-User-Id");
        }
        try {
            long userId = Long.parseLong(rawUserId);
            if (userId <= 0) {
                throw new NumberFormatException("userId must be positive");
            }
            return userId;
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "X-User-Id 不合法");
        }
    }
}
