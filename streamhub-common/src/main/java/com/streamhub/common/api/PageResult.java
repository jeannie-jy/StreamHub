package com.streamhub.common.api;

import java.util.List;

public record PageResult<T>(List<T> items, int page, int pageSize, long total, boolean hasNext) {
    public static <T> PageResult<T> of(List<T> items, int page, int pageSize, long total) {
        int safePage = Math.max(1, page);
        int safePageSize = Math.max(1, pageSize);
        return new PageResult<>(items, safePage, safePageSize, total, (long) safePage * safePageSize < total);
    }
}
