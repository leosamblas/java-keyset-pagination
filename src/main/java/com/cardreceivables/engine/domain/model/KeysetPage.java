package com.cardreceivables.engine.domain.model;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

public record KeysetPage<T>(
        List<T> items,
        int limit,
        UUID nextCursor,
        boolean hasNext
) {
    public KeysetPage {
        items = (items != null) ? Collections.unmodifiableList(items) : Collections.emptyList();
    }

    public static <T> KeysetPage<T> of(List<T> itemsWithExtra, int limit, Function<T, UUID> idExtractor) {
        Objects.requireNonNull(itemsWithExtra, "itemsWithExtra must not be null");
        Objects.requireNonNull(idExtractor, "idExtractor must not be null");

        if (itemsWithExtra.size() > limit) {
            List<T> pageItems = itemsWithExtra.subList(0, limit);
            UUID nextCursor = idExtractor.apply(pageItems.get(limit - 1));
            return new KeysetPage<>(pageItems, limit, nextCursor, true);
        } else {
            return new KeysetPage<>(itemsWithExtra, limit, null, false);
        }
    }
}

