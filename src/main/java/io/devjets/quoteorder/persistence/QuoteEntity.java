package io.devjets.quoteorder.persistence;
import jakarta.persistence.*; import java.math.BigDecimal; import java.time.Instant; import java.util.UUID;
@Entity @Table(name="quotes") public class QuoteEntity {
 @Id public UUID id; @Column(name="customer_id") public Long customerId;
 public BigDecimal total; @Column(name="expires_at") public Instant expiresAt;
 @Column(name="created_at") public Instant createdAt;
}