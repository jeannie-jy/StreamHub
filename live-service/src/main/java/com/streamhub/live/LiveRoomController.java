package com.streamhub.live;

import com.streamhub.common.api.ApiResponse;
import com.streamhub.common.api.RequestUserId;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/live/rooms")
public class LiveRoomController {
    private final LiveRoomService liveRoomService;

    public LiveRoomController(LiveRoomService liveRoomService) {
        this.liveRoomService = liveRoomService;
    }

    @PostMapping
    public ApiResponse<LiveRoomView> create(
            @Valid @RequestBody CreateRoomRequest request,
            HttpServletRequest httpRequest) {
        return ApiResponse.success(liveRoomService.create(RequestUserId.required(httpRequest), request));
    }

    @PostMapping("/{roomId}/start")
    public ApiResponse<LiveRoomView> start(
            @PathVariable long roomId,
            HttpServletRequest httpRequest) {
        return ApiResponse.success(liveRoomService.start(roomId, RequestUserId.required(httpRequest)));
    }

    @PostMapping("/{roomId}/end")
    public ApiResponse<LiveRoomView> end(
            @PathVariable long roomId,
            HttpServletRequest httpRequest) {
        return ApiResponse.success(liveRoomService.end(roomId, RequestUserId.required(httpRequest)));
    }

    @GetMapping("/{roomId}")
    public ApiResponse<LiveRoomView> get(@PathVariable long roomId) {
        return ApiResponse.success(liveRoomService.get(roomId));
    }
}
