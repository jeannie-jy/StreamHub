package com.streamhub.live;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.streamhub.common.api.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/live/rooms")
public class ChatHistoryController {
    private static final Logger log = LoggerFactory.getLogger(ChatHistoryController.class);

    private final LiveRoomRepository liveRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserServiceClient userServiceClient;

    public ChatHistoryController(
            LiveRoomRepository liveRoomRepository,
            ChatMessageRepository chatMessageRepository,
            UserServiceClient userServiceClient) {
        this.liveRoomRepository = liveRoomRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.userServiceClient = userServiceClient;
    }

    @GetMapping("/{roomId}/messages")
    public ApiResponse<List<ChatMessageView>> listMessages(
            @PathVariable long roomId,
            @RequestParam(defaultValue = "0") long afterId,
            @RequestParam(defaultValue = "50") int limit) {
        liveRoomRepository.findById(roomId).orElseThrow(
                () -> new com.streamhub.common.api.BusinessException(
                        com.streamhub.common.api.ErrorCode.NOT_FOUND, "直播间不存在"));
        List<ChatMessage> messages = chatMessageRepository.listAfter(roomId, afterId, limit);
        Map<Long, UserProfileSnapshot> profiles = profilesByUserId(messages);
        return ApiResponse.success(messages.stream()
                .map(message -> ChatMessageView.from(message, profiles.get(message.userId())))
                .toList());
    }

    private Map<Long, UserProfileSnapshot> profilesByUserId(List<ChatMessage> messages) {
        if (messages.isEmpty()) {
            return Map.of();
        }
        List<Long> userIds = messages.stream().map(ChatMessage::userId).distinct().toList();
        try {
            ApiResponse<List<UserProfileSnapshot>> response = userServiceClient.getProfiles(userIds);
            if (response != null && response.success() && response.data() != null) {
                return response.data().stream().collect(Collectors.toMap(
                        UserProfileSnapshot::id,
                        Function.identity(),
                        (first, ignored) -> first));
            }
        } catch (RuntimeException exception) {
            log.warn("批量加载弹幕用户资料失败，回退为用户 ID roomUserIds={}", userIds, exception);
        }
        return Map.of();
    }
}
