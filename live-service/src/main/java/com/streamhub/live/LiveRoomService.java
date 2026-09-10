package com.streamhub.live;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

import com.streamhub.common.api.BusinessException;
import com.streamhub.common.api.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LiveRoomService {
    private final LiveRoomRepository liveRoomRepository;
    private final OnlinePresenceService onlinePresenceService;
    private final MediaProperties mediaProperties;

    public LiveRoomService(
            LiveRoomRepository liveRoomRepository,
            OnlinePresenceService onlinePresenceService,
            MediaProperties mediaProperties) {
        this.liveRoomRepository = liveRoomRepository;
        this.onlinePresenceService = onlinePresenceService;
        this.mediaProperties = mediaProperties;
    }

    public LiveRoomView create(long anchorId, CreateRoomRequest request) {
        long roomId = liveRoomRepository.create(anchorId, request);
        return toView(findRoom(roomId), null);
    }

    @Transactional
    public LiveRoomView start(long roomId, long anchorId) {
        LiveRoom room = findRoom(roomId);
        checkAnchor(room, anchorId);
        if (!"OFFLINE".equals(room.status())) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "直播间当前不是未开播状态");
        }

        String streamKey = UUID.randomUUID().toString().replace("-", "");
        String playbackUrl = playbackUrl(roomId);
        if (!liveRoomRepository.start(roomId, anchorId, sha256(streamKey))) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "直播间状态已发生变化，请重试");
        }
        liveRoomRepository.createSession(roomId, playbackUrl);
        return toView(findRoom(roomId), streamKey);
    }

    @Transactional
    public LiveRoomView end(long roomId, long anchorId) {
        LiveRoom room = findRoom(roomId);
        checkAnchor(room, anchorId);
        if (!liveRoomRepository.end(roomId, anchorId)) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "直播间当前不是直播中状态");
        }
        liveRoomRepository.endCurrentSession(roomId);
        return toView(findRoom(roomId), null);
    }

    public LiveRoomView get(long roomId) {
        return toView(findRoom(roomId), null);
    }

    private LiveRoom findRoom(long roomId) {
        return liveRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "直播间不存在"));
    }

    private void checkAnchor(LiveRoom room, long anchorId) {
        if (room.anchorId() != anchorId) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只有主播本人可以操作直播间");
        }
    }

    private LiveRoomView toView(LiveRoom room, String streamKey) {
        String pushUrl = streamKey == null || !"LIVE".equals(room.status())
                ? null
                : mediaProperties.getRtmpPublishBase() + room.id() + "?streamKey=" + streamKey;
        return new LiveRoomView(
                room.id(),
                room.anchorId(),
                room.title(),
                room.coverUrl(),
                room.category(),
                room.status(),
                onlinePresenceService.onlineCount(room.id()),
                pushUrl,
                playbackUrl(room.id()),
                room.createdAt(),
                room.updatedAt());
    }

    private String playbackUrl(long roomId) {
        return mediaProperties.getHttpFlvPlayBase() + roomId + ".flv";
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK 缺少 SHA-256", exception);
        }
    }
}
