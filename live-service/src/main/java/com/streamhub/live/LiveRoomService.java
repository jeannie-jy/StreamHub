package com.streamhub.live;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import java.util.List;

import com.streamhub.common.api.BusinessException;
import com.streamhub.common.api.ErrorCode;
import com.streamhub.common.api.PageResult;
import feign.FeignException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LiveRoomService {
    private final LiveRoomRepository liveRoomRepository;
    private final OnlinePresenceService onlinePresenceService;
    private final MediaProperties mediaProperties;
    private final UserServiceClient userServiceClient;

    public LiveRoomService(
            LiveRoomRepository liveRoomRepository,
            OnlinePresenceService onlinePresenceService,
            MediaProperties mediaProperties,
            UserServiceClient userServiceClient) {
        this.liveRoomRepository = liveRoomRepository;
        this.onlinePresenceService = onlinePresenceService;
        this.mediaProperties = mediaProperties;
        this.userServiceClient = userServiceClient;
    }

    public LiveRoomView create(long anchorId, CreateRoomRequest request) {
        ensureUserExists(anchorId);
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

    public PageResult<LiveRoomView> page(String status, String category, String keyword, int page, int pageSize, Long anchorId) {
        PageResult<LiveRoom> result = liveRoomRepository.page(status, category, keyword, page, pageSize, anchorId);
        List<LiveRoomView> items = result.items().stream().map(room -> toView(room, null)).toList();
        return new PageResult<>(items, result.page(), result.pageSize(), result.total(), result.hasNext());
    }

    public LiveRoomView update(long roomId, long anchorId, CreateRoomRequest request) {
        LiveRoom room = findRoom(roomId);
        checkAnchor(room, anchorId);
        if (!liveRoomRepository.update(roomId, anchorId, request)) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "直播间资料更新失败");
        }
        return toView(findRoom(roomId), null);
    }

    public LiveRoomView adminStop(long roomId) {
        findRoom(roomId);
        liveRoomRepository.adminStop(roomId);
        liveRoomRepository.endCurrentSession(roomId);
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

    private void ensureUserExists(long userId) {
        try {
            var response = userServiceClient.getProfile(userId);
            if (response == null || !response.success() || response.data() == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "主播用户不存在");
            }
        } catch (FeignException.NotFound exception) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "主播用户不存在");
        } catch (FeignException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "用户服务暂时不可用");
        }
    }

    private LiveRoomView toView(LiveRoom room, String streamKey) {
        String pushUrl = streamKey == null || !"LIVE".equals(room.status())
                ? null
                : mediaProperties.getRtmpPublishBase() + room.id() + "?streamKey=" + streamKey;
        UserProfileSnapshot anchor = anchorProfile(room.anchorId());
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
                webrtcPlaybackUrl(room.id()),
                anchor == null ? null : anchor.nickname(),
                anchor == null ? null : anchor.avatarUrl(),
                room.createdAt(),
                room.updatedAt());
    }

    private String playbackUrl(long roomId) {
        return mediaProperties.getHttpFlvPlayBase() + roomId + ".flv";
    }

    private String webrtcPlaybackUrl(long roomId) {
        return mediaProperties.getWebrtcPlayBase() + roomId;
    }

    private UserProfileSnapshot anchorProfile(long userId) {
        try {
            var response = userServiceClient.getProfile(userId);
            return response != null && response.success() ? response.data() : null;
        } catch (FeignException exception) {
            return null;
        }
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
