package io.devjets.quoteorder.persistence;
import org.springframework.data.jpa.repository.JpaRepository;
public interface QuoteItemRepository extends JpaRepository<QuoteItemEntity,Long> { java.util.List<QuoteItemEntity> findByQuoteId(java.util.UUID id);}
