package io.devjets.quoteorder.persistence;
import org.springframework.data.jpa.repository.JpaRepository;
public interface AuditRepository extends JpaRepository<AuditEntity,Long> { java.util.List<AuditEntity> findByOrderIdOrderById(java.util.UUID id);}
