package io.devjets.quoteorder.persistence;
import jakarta.persistence.*; import java.math.BigDecimal; import java.time.Instant; import java.util.UUID;
import io.devjets.quoteorder.domain.OrderStatus;
@Entity @Table(name="orders") public class OrderEntity {
 @Id public UUID id; @Column(name="quote_id") public UUID quoteId; @Column(name="customer_id") public Long customerId;
 public BigDecimal total; @Enumerated(EnumType.STRING) public OrderStatus status;
 @Column(name="created_at") public Instant createdAt; @Column(name="reviewed_at") public Instant reviewedAt;
 public String reviewer; @Column(name="review_reason") public String reviewReason;
}