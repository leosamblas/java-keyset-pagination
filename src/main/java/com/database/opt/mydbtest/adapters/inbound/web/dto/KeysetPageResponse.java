package com.database.opt.mydbtest.adapters.inbound.web.dto;

import java.util.List;
import java.util.UUID;

public record KeysetPageResponse<T>(
        List<T> items,
        int limit,
        UUID nextCursor,
        boolean hasNext
) {}
