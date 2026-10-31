package io.devjets.quoteorder.domain;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
class PolicyDecisionTest {
 @Test void thresholdRequiresReview(){assertEquals(OrderStatus.PENDING_REVIEW,PolicyDecision.decide(new BigDecimal("10000"),new BigDecimal("50000"),BigDecimal.ZERO,new BigDecimal("10000")).status());}
 @Test void creditLimitRequiresReview(){assertEquals(OrderStatus.PENDING_REVIEW,PolicyDecision.decide(new BigDecimal("100"),new BigDecimal("500"),new BigDecimal("450"),new BigDecimal("10000")).status());}
 @Test void lowRiskIsApproved(){assertEquals(OrderStatus.APPROVED,PolicyDecision.decide(new BigDecimal("100"),new BigDecimal("500"),new BigDecimal("100"),new BigDecimal("10000")).status());}
}
