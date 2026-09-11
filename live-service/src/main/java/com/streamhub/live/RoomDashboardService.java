package com.streamhub.live;

import com.streamhub.common.api.BusinessException;
import com.streamhub.common.api.ErrorCode;
import org.springframework.stereotype.Service;

@Service
public class RoomDashboardService {
    private final LiveRoomRepository liveRoomRepository;
    private final OnlinePresenceService onlinePresenceService;
    private final GiftOrderRepository giftOrderRepository;
    private final ActivityOrderRepository activityOrderRepository;
    private final ChatMessageRepository chatMessageRepository;

    public RoomDashboardService(
            LiveRoomRepository liveRoomRepository,
            OnlinePresenceService onlinePresenceService,
            GiftOrderRepository giftOrderRepository,
            ActivityOrderRepository activityOrderRepository,
            ChatMessageRepository chatMessageRepository) {
        this.liveRoomRepository = liveRoomRepository;
        this.onlinePresenceService = onlinePresenceService;
        this.giftOrderRepository = giftOrderRepository;
        this.activityOrderRepository = activityOrderRepository;
        this.chatMessageRepository = chatMessageRepository;
    }

    public RoomDashboardView get(long roomId) {
        liveRoomRepository.findById(roomId).orElseThrow(
                () -> new BusinessException(ErrorCode.NOT_FOUND, "直播间不存在"));
        return new RoomDashboardView(
                roomId,
                onlinePresenceService.onlineCount(roomId),
                giftOrderRepository.sumByRoom(roomId),
                giftOrderRepository.countByRoom(roomId),
                chatMessageRepository.countByRoom(roomId),
                activityOrderRepository.countByRoom(roomId));
    }
}
