package io.devjets.quoteorder.persistence;
import jakarta.persistence.*; import java.util.UUID;
@Entity @Table(name="idempotency_records") public class IdempotencyEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
 @Column(name="idempotency_key") public String idempotencyKey; @Column(name="request_hash") public String requestHash;
 @Column(name="order_id") public UUID orderId;
}