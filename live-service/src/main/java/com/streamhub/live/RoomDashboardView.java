package com.streamhub.live;

public record RoomDashboardView(
        long roomId,
        long onlineCount,
        long giftAmount,
        long giftOrderCount,
        long chatMessageCount,
        long activityOrderCount) {
}
