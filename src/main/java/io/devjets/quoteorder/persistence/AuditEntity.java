package io.devjets.quoteorder.persistence;
import jakarta.persistence.*; import java.time.Instant; import java.util.UUID;
@Entity @Table(name="audit_records") public class AuditEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
 @Column(name="order_id") public UUID orderId; public String actor; public String action;
 @Column(columnDefinition="jsonb") public String inputs; @Column(columnDefinition="jsonb") public String result;
 @Column(name="occurred_at") public Instant occurredAt;
}