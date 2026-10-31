package io.devjets.quoteorder.persistence;
import jakarta.persistence.*; import java.math.BigDecimal;
@Entity @Table(name="products") public class ProductEntity {
 @Id public String sku; @Column(name="unit_price") public BigDecimal unitPrice;
 @Column(name="stock_quantity") public Integer stockQuantity;
}