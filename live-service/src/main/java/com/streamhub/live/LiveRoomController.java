package com.streamhub.live;

import com.streamhub.common.api.ApiResponse;
import com.streamhub.common.api.RequestUserId;
import com.streamhub.common.api.PageResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/v1/live/rooms")
public class LiveRoomController {
    private final LiveRoomService liveRoomService;
    private final RoomDashboardService roomDashboardService;
    private final ActivityService activityService;

    public LiveRoomController(
            LiveRoomService liveRoomService,
            RoomDashboardService roomDashboardService,
            ActivityService activityService) {
        this.liveRoomService = liveRoomService;
        this.roomDashboardService = roomDashboardService;
        this.activityService = activityService;
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

    @GetMapping("/following")
    public ApiResponse<PageResult<LiveRoomView>> following(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        return ApiResponse.success(liveRoomService.following(RequestUserId.required(request), page, pageSize));
    }

    @GetMapping("/favorites")
    public ApiResponse<PageResult<LiveRoomView>> favorites(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        return ApiResponse.success(liveRoomService.favorites(RequestUserId.required(request), page, pageSize));
    }

    @GetMapping("/{roomId}/favorite-status")
    public ApiResponse<java.util.Map<String, Boolean>> favoriteStatus(
            @PathVariable long roomId,
            HttpServletRequest request) {
        return ApiResponse.success(liveRoomService.favoriteStatus(RequestUserId.required(request), roomId));
    }

    @PostMapping("/{roomId}/favorite")
    public ApiResponse<java.util.Map<String, Boolean>> favorite(
            @PathVariable long roomId,
            HttpServletRequest request) {
        return ApiResponse.success(liveRoomService.favorite(RequestUserId.required(request), roomId, true));
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/{roomId}/favorite")
    public ApiResponse<java.util.Map<String, Boolean>> unfavorite(
            @PathVariable long roomId,
            HttpServletRequest request) {
        return ApiResponse.success(liveRoomService.favorite(RequestUserId.required(request), roomId, false));
    }

    @GetMapping
    public ApiResponse<PageResult<LiveRoomView>> page(
            @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "") String category,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.success(liveRoomService.page(status, category, keyword, page, pageSize, null));
    }

    @GetMapping("/mine")
    public ApiResponse<PageResult<LiveRoomView>> mine(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        return ApiResponse.success(liveRoomService.page("", "", "", page, pageSize, RequestUserId.required(request)));
    }

    @PutMapping("/{roomId}")
    public ApiResponse<LiveRoomView> update(
            @PathVariable long roomId,
            @Valid @RequestBody CreateRoomRequest request,
            HttpServletRequest httpRequest) {
        return ApiResponse.success(liveRoomService.update(roomId, RequestUserId.required(httpRequest), request));
    }

    @GetMapping("/{roomId}/activities")
    public ApiResponse<java.util.List<ActivityView>> activities(@PathVariable long roomId) {
        return ApiResponse.success(activityService.listByRoom(roomId));
    }

    @GetMapping("/{roomId}/dashboard")
    public ApiResponse<RoomDashboardView> dashboard(
            @PathVariable long roomId) {
        return ApiResponse.success(roomDashboardService.get(roomId));
    }
}
