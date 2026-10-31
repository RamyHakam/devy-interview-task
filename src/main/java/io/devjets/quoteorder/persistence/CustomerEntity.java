package io.devjets.quoteorder.persistence;
import jakarta.persistence.*; import java.math.BigDecimal;
@Entity @Table(name="customers") public class CustomerEntity {
 @Id public Long id; public String name;
 @Column(name="credit_limit") public BigDecimal creditLimit;
 @Column(name="credit_used") public BigDecimal creditUsed;
}