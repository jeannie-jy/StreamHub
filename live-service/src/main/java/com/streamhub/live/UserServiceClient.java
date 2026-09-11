package com.streamhub.live;

import com.streamhub.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "streamhub-user", path = "/api/v1/users")
public interface UserServiceClient {
    @GetMapping("/{userId}")
    ApiResponse<UserProfileSnapshot> getProfile(@PathVariable("userId") long userId);

    @GetMapping("/internal/banned-count")
    ApiResponse<Long> countBannedUsers();
}
