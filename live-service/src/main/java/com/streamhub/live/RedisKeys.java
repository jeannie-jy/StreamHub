package com.streamhub.live;

final class RedisKeys {
    private RedisKeys() {
    }

    static String roomOnline(long roomId) {
        return "live:room:{" + roomId + "}:online";
    }

    static String roomUserConnections(long roomId, long userId) {
        return "live:room:{" + roomId + "}:user:" + userId + ":connections";
    }

    static String activityStock(long activityId) {
        return "activity:{" + activityId + "}:stock";
    }

    static String activityUsers(long activityId) {
        return "activity:{" + activityId + "}:users";
    }
}
