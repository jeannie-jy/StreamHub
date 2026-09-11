package com.streamhub.common.api;

import jakarta.servlet.http.HttpServletRequest;

public final class RequestUserRole {
    public static final String USER_ROLE_HEADER = "X-User-Role";

    private RequestUserRole() {
    }

    public static String required(HttpServletRequest request) {
        String role = request.getHeader(USER_ROLE_HEADER);
        if (role == null || role.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "请求缺少用户角色");
        }
        return role;
    }

    public static long requireOperator(HttpServletRequest request) {
        long userId = RequestUserId.required(request);
        String role = required(request);
        if (!"OPERATOR".equals(role) && !"ADMIN".equals(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只有运营人员可以执行此操作");
        }
        return userId;
    }
}
