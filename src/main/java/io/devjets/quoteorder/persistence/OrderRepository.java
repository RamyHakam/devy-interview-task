package io.devjets.quoteorder.persistence;
import org.springframework.data.jpa.repository.JpaRepository;
public interface OrderRepository extends JpaRepository<OrderEntity,java.util.UUID> { java.util.Optional<OrderEntity> findByQuoteId(java.util.UUID id);}
