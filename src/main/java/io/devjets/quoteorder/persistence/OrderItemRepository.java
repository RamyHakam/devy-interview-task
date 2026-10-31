package io.devjets.quoteorder.persistence;
import org.springframework.data.jpa.repository.JpaRepository;
public interface OrderItemRepository extends JpaRepository<OrderItemEntity,Long> {}
