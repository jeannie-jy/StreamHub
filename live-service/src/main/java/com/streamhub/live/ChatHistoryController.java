package com.streamhub.live;

import java.util.List;

import com.streamhub.common.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/live/rooms")
public class ChatHistoryController {
    private final LiveRoomRepository liveRoomRepository;
    private final ChatMessageRepository chatMessageRepository;

    public ChatHistoryController(
            LiveRoomRepository liveRoomRepository,
            ChatMessageRepository chatMessageRepository) {
        this.liveRoomRepository = liveRoomRepository;
        this.chatMessageRepository = chatMessageRepository;
    }

    @GetMapping("/{roomId}/messages")
    public ApiResponse<List<ChatMessage>> listMessages(
            @PathVariable long roomId,
            @RequestParam(defaultValue = "0") long afterId,
            @RequestParam(defaultValue = "50") int limit) {
        liveRoomRepository.findById(roomId).orElseThrow(
                () -> new com.streamhub.common.api.BusinessException(
                        com.streamhub.common.api.ErrorCode.NOT_FOUND, "直播间不存在"));
        return ApiResponse.success(chatMessageRepository.listAfter(roomId, afterId, limit));
    }
}
