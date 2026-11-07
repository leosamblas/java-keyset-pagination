package com.database.opt.mydbtest.adapters.inbound.web.dto;

import java.time.Instant;
import java.util.List;

public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        List<String> details
) {}
