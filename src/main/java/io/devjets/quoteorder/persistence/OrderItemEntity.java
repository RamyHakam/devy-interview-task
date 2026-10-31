package io.devjets.quoteorder.persistence;
import jakarta.persistence.*; import java.math.BigDecimal; import java.util.UUID;
@Entity @Table(name="order_items") public class OrderItemEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
 @Column(name="order_id") public UUID orderId; public String sku; public Integer quantity;
 @Column(name="unit_price") public BigDecimal unitPrice; @Column(name="line_total") public BigDecimal lineTotal;
}