package com.database.opt.mydbtest.adapters.inbound.web.dto;

import com.database.opt.mydbtest.domain.model.CardBrand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Payload para cadastro de recebível de cartão")
public record CreateReceivableRequest(
        @Schema(description = "Identificador do lojista (merchant)", example = "merch-1001", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "merchantId is required")
        String merchantId,

        @Schema(description = "Bandeira do cartão", example = "VISA", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "cardBrand is required")
        CardBrand cardBrand,

        @Schema(description = "Valor bruto da transação em reais (maior que zero)", example = "250.00", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "grossAmount is required")
        @DecimalMin(value = "0.01", message = "grossAmount must be greater than zero")
        BigDecimal grossAmount,

        @Schema(description = "Taxa MDR aplicada (ex: 0.0250 para 2.5%)", example = "0.0250", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "feeRate is required")
        @DecimalMin(value = "0.0000", message = "feeRate cannot be negative")
        BigDecimal feeRate,

        @Schema(description = "Quantidade total de parcelas (padrão: 1)", example = "1", defaultValue = "1")
        @Min(value = 1, message = "installments must be at least 1")
        Integer installments,

        @Schema(description = "Número da parcela atual (padrão: 1)", example = "1", defaultValue = "1")
        @Min(value = 1, message = "installmentNumber must be at least 1")
        Integer installmentNumber,

        @Schema(description = "Data e hora da transação (padrão: momento atual caso nulo)", example = "2026-11-07T14:30:00Z")
        Instant transactionDate
) {}
