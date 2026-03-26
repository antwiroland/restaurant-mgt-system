package com.restaurantmanager.core.common;

import org.springframework.http.HttpHeaders;

import java.util.List;

public final class Pagination {
    private static final int DEFAULT_SIZE = 50;
    private static final int MAX_SIZE = 200;

    private Pagination() {
    }

    public static Params from(Integer page, Integer size) {
        boolean paged = page != null || size != null;
        int normalizedPage = page == null ? 0 : Math.max(page, 0);
        int normalizedSize = size == null ? DEFAULT_SIZE : Math.max(Math.min(size, MAX_SIZE), 1);
        return new Params(normalizedPage, normalizedSize, paged);
    }

    public static <T> List<T> slice(List<T> source, Params params) {
        if (!params.paged()) {
            return source;
        }
        int fromIndex = params.page() * params.size();
        if (fromIndex >= source.size()) {
            return List.of();
        }
        int toIndex = Math.min(fromIndex + params.size(), source.size());
        return source.subList(fromIndex, toIndex);
    }

    public static HttpHeaders headers(long totalElements, Params params) {
        HttpHeaders headers = new HttpHeaders();
        if (!params.paged()) {
            return headers;
        }
        long totalPages = params.size() == 0 ? 0 : (long) Math.ceil((double) totalElements / params.size());
        headers.add("X-Page", String.valueOf(params.page()));
        headers.add("X-Size", String.valueOf(params.size()));
        headers.add("X-Total-Elements", String.valueOf(totalElements));
        headers.add("X-Total-Pages", String.valueOf(totalPages));
        return headers;
    }

    public record Params(int page, int size, boolean paged) {
    }
}
