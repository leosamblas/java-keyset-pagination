package com.database.opt.mydbtest.adapters.inbound.web.dto;

import com.database.opt.mydbtest.domain.model.CardBrand;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;

public record CreateReceivableRequest(
        @NotBlank(message = "merchantId is required")
        String merchantId,

        @NotNull(message = "cardBrand is required")
        CardBrand cardBrand,

        @NotNull(message = "grossAmount is required")
        @DecimalMin(value = "0.01", message = "grossAmount must be greater than zero")
        BigDecimal grossAmount,

        @NotNull(message = "feeRate is required")
        @DecimalMin(value = "0.0000", message = "feeRate cannot be negative")
        BigDecimal feeRate,

        @Min(value = 1, message = "installments must be at least 1")
        Integer installments,

        @Min(value = 1, message = "installmentNumber must be at least 1")
        Integer installmentNumber,

        Instant transactionDate
) {}

