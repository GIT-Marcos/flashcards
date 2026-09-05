package com.cards.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Schema(description = "Cursor-based pagination request. Use the factory methods (forDecks, forCards, forSessions, forUsers) for type-safe creation.")
public record CursorPaginationRequest(
    @Schema(description = "ID of the last item from the previous page (cursor)", example = "42")
    Long lastId,

    @Schema(description = "Cursor value of the sort field from the last item (ISO-8601)", example = "2026-05-19T10:30:00Z")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant cursorValue,

    @Schema(description = "Number of items per page (1-100)", example = "15")
    Integer pageSize,

    @Schema(description = "Sort direction", example = "ASC")
    Sort.Direction direction,

    @Schema(hidden = true) String sortField,

    @Schema(hidden = true) Sort.Direction defaultDirection
) {
    public CursorPaginationRequest {
        pageSize = (pageSize == null || pageSize < 1 || pageSize > 100) ? 15 : pageSize;
        direction = (direction == null) ? defaultDirection : direction;

        if ((lastId == null) != (cursorValue == null)) {
            lastId = null;
            cursorValue = null;
        }
    }

    public ScrollPosition toScrollPosition() {
        if (lastId == null && cursorValue == null) {
            return ScrollPosition.keyset();
        }

        Map<String, Object> keys = new HashMap<>();
        keys.put(sortField, cursorValue);
        keys.put("id", lastId);

        ScrollPosition.Direction scrollDirection = direction == Sort.Direction.DESC
            ? ScrollPosition.Direction.BACKWARD
            : ScrollPosition.Direction.FORWARD;

        return ScrollPosition.of(keys, scrollDirection);
    }

    public Sort toSort() {
        return Sort.by(direction, sortField).and(Sort.by(direction, "id"));
    }

    public static CursorPaginationRequest forDecks(Long lastId, Instant cursorValue, Integer pageSize, Sort.Direction direction) {
        return new CursorPaginationRequest(lastId, cursorValue, pageSize, direction, "createdAt", Sort.Direction.ASC);
    }

    public static CursorPaginationRequest forCards(Long lastId, Instant cursorValue, Integer pageSize, Sort.Direction direction) {
        return new CursorPaginationRequest(lastId, cursorValue, pageSize, direction, "nextReviewDate", Sort.Direction.ASC);
    }

    public static CursorPaginationRequest forSessions(Long lastId, Instant cursorValue, Integer pageSize, Sort.Direction direction) {
        return new CursorPaginationRequest(lastId, cursorValue, pageSize, direction, "startTime", Sort.Direction.DESC);
    }

    public static CursorPaginationRequest forUsers(Long lastId, Instant cursorValue, Integer pageSize, Sort.Direction direction) {
        return new CursorPaginationRequest(lastId, cursorValue, pageSize, direction, "createdAt", Sort.Direction.ASC);
    }
}
