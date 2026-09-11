package com.streamhub.user;

import com.streamhub.common.api.ApiResponse;
import com.streamhub.common.api.BusinessException;
import com.streamhub.common.api.ErrorCode;
import com.streamhub.common.api.PageResult;
import com.streamhub.common.api.RequestUserRole;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ops/users")
public class OpsUserController {
    private final UserRepository userRepository;

    public OpsUserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping
    public ApiResponse<PageResult<UserProfile>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "") String role,
            @RequestParam(defaultValue = "") String status,
            HttpServletRequest request) {
        RequestUserRole.requireOperator(request);
        return ApiResponse.success(userRepository.page(keyword, role, status, page, pageSize));
    }

    @PostMapping("/{userId}/ban")
    public ApiResponse<UserProfile> ban(
            @PathVariable long userId,
            @Valid @RequestBody ModerationRequest moderation,
            HttpServletRequest request) {
        long operatorId = RequestUserRole.requireOperator(request);
        UserProfile user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"));
        if ("ADMIN".equals(user.role())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "不能封禁运营管理员");
        }
        UserProfile updated = userRepository.changeStatus(userId, "BANNED");
        userRepository.appendAudit(operatorId, "BAN_USER", "USER", String.valueOf(userId), moderation.reason());
        return ApiResponse.success(updated);
    }

    @DeleteMapping("/{userId}/ban")
    public ApiResponse<UserProfile> unban(@PathVariable long userId, HttpServletRequest request) {
        long operatorId = RequestUserRole.requireOperator(request);
        userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"));
        UserProfile updated = userRepository.changeStatus(userId, "ACTIVE");
        userRepository.appendAudit(operatorId, "UNBAN_USER", "USER", String.valueOf(userId), "运营恢复账号");
        return ApiResponse.success(updated);
    }

    public record ModerationRequest(
            @NotBlank @Size(max = 512) String reason) {
    }
}
