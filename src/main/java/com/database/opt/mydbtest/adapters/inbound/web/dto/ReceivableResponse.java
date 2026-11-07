package com.database.opt.mydbtest.adapters.inbound.web.dto;

import com.database.opt.mydbtest.domain.model.CardBrand;
import com.database.opt.mydbtest.domain.model.ReceivableStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ReceivableResponse(
        UUID id,
        String merchantId,
        CardBrand cardBrand,
        BigDecimal grossAmount,
        BigDecimal feeRate,
        BigDecimal feeAmount,
        BigDecimal netAmount,
        Integer installments,
        Integer installmentNumber,
        ReceivableStatus status,
        Instant transactionDate,
        LocalDate settlementDate,
        Instant createdAt
) {}
