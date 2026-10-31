package io.devjets.quoteorder.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import io.devjets.quoteorder.domain.OrderStatus;

public final class ApiModels {
 private ApiModels() {}
 public record QuoteRequest(@NotNull Long customerId, @NotEmpty List<@Valid QuoteLineRequest> items) {}
 public record QuoteLineRequest(@NotBlank String sku, @Positive int quantity) {}
 public record QuoteLine(String sku,int quantity,BigDecimal unitPrice,BigDecimal lineTotal) {}
 public record QuoteResponse(UUID id,Long customerId,List<QuoteLine> items,BigDecimal total,Instant expiresAt) {}
 public record ConfirmRequest(@NotBlank String actorId) {}
 public record OrderResponse(UUID id,UUID quoteId,Long customerId,BigDecimal total,OrderStatus status,Instant createdAt) {}
 public record ReviewRequest(@NotBlank String decision,@NotBlank String reviewer,@NotBlank String reason) {}
 public record AuditResponse(Long id,String actor,String action,Object inputs,Object result,Instant occurredAt) {}
 public record ErrorResponse(String code,String message,String correlationId,Instant timestamp) {}
}
