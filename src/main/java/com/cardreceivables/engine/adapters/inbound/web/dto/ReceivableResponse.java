package com.cardreceivables.engine.adapters.inbound.web.dto;

import com.cardreceivables.engine.domain.model.CardBrand;
import com.cardreceivables.engine.domain.model.ReceivableStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "Representação detalhada de um recebível de cartão")
public record ReceivableResponse(
        @Schema(description = "Identificador único ordenado temporalmente (PostgreSQL 18 UUIDv7)", example = "01930777-a123-7456-8abc-def012345678")
        UUID id,

        @Schema(description = "Identificador do lojista", example = "merch-1001")
        String merchantId,

        @Schema(description = "Bandeira do cartão", example = "VISA")
        CardBrand cardBrand,

        @Schema(description = "Valor bruto da transação", example = "250.00")
        BigDecimal grossAmount,

        @Schema(description = "Taxa MDR contratada", example = "0.0250")
        BigDecimal feeRate,

        @Schema(description = "Valor da taxa MDR calculado (arredondamento HALF_UP na escala 2)", example = "6.25")
        BigDecimal feeAmount,

        @Schema(description = "Valor líquido a ser repassado ao lojista (grossAmount - feeAmount)", example = "243.75")
        BigDecimal netAmount,

        @Schema(description = "Total de parcelas", example = "1")
        Integer installments,

        @Schema(description = "Número da parcela", example = "1")
        Integer installmentNumber,

        @Schema(description = "Status do recebível", example = "PENDING")
        ReceivableStatus status,

        @Schema(description = "Data e hora da transação original", example = "2026-11-07T14:30:00Z")
        Instant transactionDate,

        @Schema(description = "Data prevista de liquidação financeira (D+30)", example = "2026-12-07")
        LocalDate settlementDate,

        @Schema(description = "Data e hora de criação do registro no banco", example = "2026-11-07T14:30:00.123456Z")
        Instant createdAt
) {}
