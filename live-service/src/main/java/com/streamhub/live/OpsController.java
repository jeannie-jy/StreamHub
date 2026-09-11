package com.streamhub.live;

import java.util.List;
import java.time.Instant;

import com.streamhub.common.api.ApiResponse;
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
@RequestMapping("/api/v1/ops")
public class OpsController {
    private final LiveRoomService liveRoomService;
    private final LiveRoomRepository liveRoomRepository;
    private final GiftOrderRepository giftOrderRepository;
    private final ActivityOrderRepository activityOrderRepository;
    private final OnlinePresenceService onlinePresenceService;
    private final UserServiceClient userServiceClient;
    private final SensitiveWordService sensitiveWordService;
    private final OpsAuditRepository opsAuditRepository;
    private final ContentModerationRepository contentModerationRepository;
    private final RoomMuteRepository roomMuteRepository;

    public OpsController(
            LiveRoomService liveRoomService,
            LiveRoomRepository liveRoomRepository,
            GiftOrderRepository giftOrderRepository,
            ActivityOrderRepository activityOrderRepository,
            OnlinePresenceService onlinePresenceService,
            UserServiceClient userServiceClient,
            SensitiveWordService sensitiveWordService,
            OpsAuditRepository opsAuditRepository,
            ContentModerationRepository contentModerationRepository,
            RoomMuteRepository roomMuteRepository) {
        this.liveRoomService = liveRoomService;
        this.liveRoomRepository = liveRoomRepository;
        this.giftOrderRepository = giftOrderRepository;
        this.activityOrderRepository = activityOrderRepository;
        this.onlinePresenceService = onlinePresenceService;
        this.userServiceClient = userServiceClient;
        this.sensitiveWordService = sensitiveWordService;
        this.opsAuditRepository = opsAuditRepository;
        this.contentModerationRepository = contentModerationRepository;
        this.roomMuteRepository = roomMuteRepository;
    }

    @GetMapping("/dashboard/summary")
    public ApiResponse<OpsSummaryView> summary(HttpServletRequest request) {
        RequestUserRole.requireOperator(request);
        long online = liveRoomService.page("LIVE", "", "", 1, 100, null).items().stream()
                .mapToLong(room -> onlinePresenceService.onlineCount(room.id()))
                .sum();
        long banned = 0;
        try {
            var response = userServiceClient.countBannedUsers();
            if (response != null && response.success() && response.data() != null) banned = response.data();
        } catch (RuntimeException ignored) {
            // User governance remains available when the overview's optional aggregate is unavailable.
        }
        return ApiResponse.success(new OpsSummaryView(
                liveRoomRepository.countAll(),
                online,
                giftOrderRepository.countAll(),
                activityOrderRepository.countAll(),
                giftOrderRepository.countPending() + activityOrderRepository.countPending(),
                banned));
    }

    @GetMapping("/rooms")
    public ApiResponse<PageResult<LiveRoomView>> rooms(
            @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "") String category,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        RequestUserRole.requireOperator(request);
        return ApiResponse.success(liveRoomService.page(status, category, keyword, page, pageSize, null));
    }

    @PostMapping("/rooms/{roomId}/stop")
    public ApiResponse<LiveRoomView> stopRoom(
            @PathVariable long roomId,
            @Valid @RequestBody ReasonRequest body,
            HttpServletRequest request) {
        long operatorId = RequestUserRole.requireOperator(request);
        LiveRoomView result = liveRoomService.adminStop(roomId);
        opsAuditRepository.append(operatorId, "STOP_ROOM", "ROOM", String.valueOf(roomId), body.reason());
        return ApiResponse.success(result);
    }

    @GetMapping("/sensitive-words")
    public ApiResponse<List<SensitiveWord>> sensitiveWords(HttpServletRequest request) {
        RequestUserRole.requireOperator(request);
        return ApiResponse.success(sensitiveWordService.list());
    }

    @PostMapping("/sensitive-words")
    public ApiResponse<SensitiveWord> addSensitiveWord(
            @Valid @RequestBody SensitiveWordRequest body,
            HttpServletRequest request) {
        long operatorId = RequestUserRole.requireOperator(request);
        SensitiveWord result = sensitiveWordService.add(body.word());
        opsAuditRepository.append(operatorId, "ADD_SENSITIVE_WORD", "SENSITIVE_WORD", String.valueOf(result.id()), body.word());
        return ApiResponse.success(result);
    }

    @DeleteMapping("/sensitive-words/{id}")
    public ApiResponse<Void> removeSensitiveWord(@PathVariable long id, HttpServletRequest request) {
        long operatorId = RequestUserRole.requireOperator(request);
        sensitiveWordService.remove(id);
        opsAuditRepository.append(operatorId, "REMOVE_SENSITIVE_WORD", "SENSITIVE_WORD", String.valueOf(id), "运营删除敏感词");
        return ApiResponse.successVoid();
    }

    @GetMapping("/audit-logs")
    public ApiResponse<PageResult<OpsAuditRepository.AuditLogView>> auditLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        RequestUserRole.requireOperator(request);
        List<OpsAuditRepository.AuditLogView> items = opsAuditRepository.page(page, pageSize);
        return ApiResponse.success(PageResult.of(items, page, pageSize, opsAuditRepository.count()));
    }

    @GetMapping("/moderation-logs")
    public ApiResponse<PageResult<ContentModerationRepository.ModerationLogView>> moderationLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        RequestUserRole.requireOperator(request);
        return ApiResponse.success(contentModerationRepository.page(page, pageSize));
    }

    @PostMapping("/rooms/{roomId}/mutes")
    public ApiResponse<Void> mute(
            @PathVariable long roomId,
            @Valid @RequestBody MuteRequest body,
            HttpServletRequest request) {
        long operatorId = RequestUserRole.requireOperator(request);
        Instant expiresAt = body.expiresMinutes() == null
                ? null
                : Instant.now().plusSeconds(body.expiresMinutes() * 60);
        roomMuteRepository.mute(roomId, body.userId(), operatorId, body.reason(), expiresAt);
        opsAuditRepository.append(operatorId, "MUTE_ROOM_USER", "ROOM_USER", roomId + ":" + body.userId(), body.reason());
        return ApiResponse.successVoid();
    }

    @DeleteMapping("/rooms/{roomId}/mutes/{userId}")
    public ApiResponse<Void> unmute(
            @PathVariable long roomId,
            @PathVariable long userId,
            HttpServletRequest request) {
        long operatorId = RequestUserRole.requireOperator(request);
        roomMuteRepository.revoke(roomId, userId);
        opsAuditRepository.append(operatorId, "UNMUTE_ROOM_USER", "ROOM_USER", roomId + ":" + userId, "运营解除房间禁言");
        return ApiResponse.successVoid();
    }

    public record ReasonRequest(@NotBlank @Size(max = 512) String reason) {
    }

    public record SensitiveWordRequest(@NotBlank @Size(max = 128) String word) {
    }

    public record MuteRequest(
            @jakarta.validation.constraints.Positive long userId,
            @NotBlank @Size(max = 512) String reason,
            @jakarta.validation.constraints.Positive Long expiresMinutes) {
    }
}
