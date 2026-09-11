package com.streamhub.live;

import java.time.Instant;

import com.streamhub.common.api.ApiResponse;
import com.streamhub.common.api.RequestUserId;
import com.streamhub.common.api.RequestUserRole;
import com.streamhub.common.api.PageResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ActivityController {
    private final ActivityService activityService;

    public ActivityController(ActivityService activityService) {
        this.activityService = activityService;
    }

    @PostMapping("/live/rooms/{roomId}/activities")
    public ApiResponse<ActivityView> create(
            @PathVariable long roomId,
            @Valid @RequestBody CreateActivityRequest request,
            HttpServletRequest httpRequest) {
        return ApiResponse.success(activityService.create(
                roomId,
                RequestUserId.required(httpRequest),
                request.name(),
                request.stock(),
                request.unitPrice(),
                request.startsAt(),
                request.endsAt()));
    }

    @PostMapping("/activities/{activityId}/start")
    public ApiResponse<ActivityView> start(
            @PathVariable long activityId,
            HttpServletRequest httpRequest) {
        return ApiResponse.success(activityService.start(activityId, RequestUserId.required(httpRequest)));
    }

    @GetMapping("/activities/{activityId}")
    public ApiResponse<ActivityView> get(@PathVariable long activityId) {
        return ApiResponse.success(activityService.get(activityId));
    }

    @PostMapping("/ops/activities/{activityId}/stop")
    public ApiResponse<ActivityView> stopByOperator(
            @PathVariable long activityId,
            @Valid @RequestBody StopActivityRequest request,
            HttpServletRequest httpRequest) {
        long operatorId = RequestUserRole.requireOperator(httpRequest);
        ActivityView result = activityService.stop(activityId);
        activityService.appendAudit(operatorId, "STOP_ACTIVITY", "ACTIVITY", String.valueOf(activityId), request.reason());
        return ApiResponse.success(result);
    }

    @PostMapping("/activities/{activityId}/seckill")
    public ApiResponse<ActivityOrderView> seckill(
            @PathVariable long activityId,
            @Valid @RequestBody SeckillRequest request,
            HttpServletRequest httpRequest) {
        return ApiResponse.success(activityService.seckill(
                activityId,
                RequestUserId.required(httpRequest),
                request.clientOrderNo()));
    }

    @GetMapping("/activity-orders/{orderNo}")
    public ApiResponse<ActivityOrderView> getOrder(@PathVariable String orderNo) {
        return ApiResponse.success(activityService.findOrder(orderNo));
    }

    @GetMapping("/activity-orders/mine")
    public ApiResponse<PageResult<ActivityOrderView>> myOrders(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        return ApiResponse.success(activityService.ordersForUser(RequestUserId.required(request), page, pageSize));
    }

    @GetMapping("/ops/activities")
    public ApiResponse<PageResult<ActivityView>> opsPage(
            @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        RequestUserRole.requireOperator(request);
        return ApiResponse.success(activityService.page(status, page, pageSize));
    }

    public record CreateActivityRequest(
            @NotBlank @Size(max = 128) String name,
            @Min(1) int stock,
            @Min(0) long unitPrice,
            @NotNull Instant startsAt,
            @NotNull Instant endsAt) {
    }

    public record SeckillRequest(
            @NotBlank @Size(max = 128) String clientOrderNo) {
    }

    public record StopActivityRequest(
            @NotBlank @Size(max = 512) String reason) {
    }
}
