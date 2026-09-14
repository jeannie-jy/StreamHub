package com.streamhub.user;

import java.util.List;
import java.util.Map;

import com.streamhub.common.api.ApiResponse;
import com.streamhub.common.api.BusinessException;
import com.streamhub.common.api.ErrorCode;
import com.streamhub.common.api.RequestUserId;
import com.streamhub.common.api.PageResult;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/{userId}")
    public ApiResponse<UserProfile> getProfile(@PathVariable long userId) {
        UserProfile profile = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"));
        return ApiResponse.success(profile);
    }

    @GetMapping("/internal/batch")
    public ApiResponse<List<UserProfile>> getProfiles(@RequestParam List<Long> ids) {
        return ApiResponse.success(userRepository.findByIds(ids));
    }

    @GetMapping("/internal/banned-count")
    public ApiResponse<Long> countBannedUsers() {
        return ApiResponse.success(userRepository.countByStatus("BANNED"));
    }

    @GetMapping("/me/following")
    public ApiResponse<PageResult<UserProfile>> following(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        return ApiResponse.success(userRepository.following(RequestUserId.required(request), page, pageSize));
    }

    @PostMapping("/{anchorId}/follow")
    public ApiResponse<Map<String, Object>> follow(
            @PathVariable long anchorId,
            HttpServletRequest request) {
        long userId = RequestUserId.required(request);
        if (userId == anchorId) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "不能关注自己");
        }
        userRepository.findById(anchorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "主播不存在"));
        userRepository.follow(userId, anchorId);
        return ApiResponse.success(Map.of("following", true, "anchorId", anchorId));
    }

    @DeleteMapping("/{anchorId}/follow")
    public ApiResponse<Map<String, Object>> unfollow(
            @PathVariable long anchorId,
            HttpServletRequest request) {
        userRepository.unfollow(RequestUserId.required(request), anchorId);
        return ApiResponse.success(Map.of("following", false, "anchorId", anchorId));
    }

    @GetMapping("/{anchorId}/follow-status")
    public ApiResponse<Map<String, Object>> followStatus(
            @PathVariable long anchorId,
            HttpServletRequest request) {
        long userId = RequestUserId.required(request);
        return ApiResponse.success(Map.of(
                "following", userRepository.isFollowing(userId, anchorId),
                "anchorId", anchorId));
    }

    @PutMapping("/me")
    public ApiResponse<UserProfile> updateMe(
            @jakarta.validation.Valid @org.springframework.web.bind.annotation.RequestBody UpdateProfileRequest request,
            HttpServletRequest httpRequest) {
        return ApiResponse.success(userRepository.updateProfile(
                RequestUserId.required(httpRequest),
                request.nickname(),
                request.avatarUrl()));
    }

    public record UpdateProfileRequest(
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 64) String nickname,
            @jakarta.validation.constraints.Size(max = 512) String avatarUrl) {
    }
}
