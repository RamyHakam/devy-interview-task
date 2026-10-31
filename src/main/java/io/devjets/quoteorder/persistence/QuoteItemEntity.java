package io.devjets.quoteorder.persistence;
import jakarta.persistence.*; import java.math.BigDecimal; import java.util.UUID;
@Entity @Table(name="quote_items") public class QuoteItemEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
 @Column(name="quote_id") public UUID quoteId; public String sku; public Integer quantity;
 @Column(name="unit_price") public BigDecimal unitPrice; @Column(name="line_total") public BigDecimal lineTotal;
}