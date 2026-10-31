package io.devjets.quoteorder.domain;
import java.math.BigDecimal;
public record PolicyDecision(OrderStatus status, String reason) {
 public static PolicyDecision decide(BigDecimal total, BigDecimal creditLimit, BigDecimal creditUsed, BigDecimal threshold) {
  if (total.compareTo(threshold) >= 0) return new PolicyDecision(OrderStatus.PENDING_REVIEW,"TOTAL_THRESHOLD");
  if (creditUsed.add(total).compareTo(creditLimit) > 0) return new PolicyDecision(OrderStatus.PENDING_REVIEW,"CREDIT_LIMIT_EXCEEDED");
  return new PolicyDecision(OrderStatus.APPROVED,"AUTO_APPROVED");
 }
}
