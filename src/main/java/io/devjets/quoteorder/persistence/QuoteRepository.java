package io.devjets.quoteorder.persistence;
import org.springframework.data.jpa.repository.JpaRepository;
public interface QuoteRepository extends JpaRepository<QuoteEntity,java.util.UUID> {}
