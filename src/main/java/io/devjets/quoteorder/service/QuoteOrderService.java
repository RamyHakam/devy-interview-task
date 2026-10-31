package io.devjets.quoteorder.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.devjets.quoteorder.api.ApiModels.*;
import io.devjets.quoteorder.domain.*;
import io.devjets.quoteorder.persistence.*;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

@Service
public class QuoteOrderService {
 private final EntityManager em; private final JdbcTemplate jdbc; private final ObjectMapper mapper;
 private final MeterRegistry metrics; private final BigDecimal threshold;
 public QuoteOrderService(EntityManager em,JdbcTemplate jdbc,ObjectMapper mapper,MeterRegistry metrics,
   @Value("${app.approval.threshold:10000.00}") BigDecimal threshold) {
  this.em=em;this.jdbc=jdbc;this.mapper=mapper;this.metrics=metrics;this.threshold=threshold;
 }
 @Transactional
 public QuoteResponse createQuote(QuoteRequest req) {
  CustomerEntity customer=em.find(CustomerEntity.class,req.customerId());
  if(customer==null) throw DomainException.notFound("Customer");
  Map<String,Integer> quantities=new TreeMap<>();
  for(var line:req.items()) quantities.merge(line.sku(),line.quantity(),Integer::sum);
  UUID id=UUID.randomUUID(); BigDecimal total=BigDecimal.ZERO; List<QuoteLine> lines=new ArrayList<>();
  Map<String,ProductEntity> products=new TreeMap<>();
  for(var e:quantities.entrySet()) {
   ProductEntity p=em.find(ProductEntity.class,e.getKey());
   if(p==null) throw DomainException.notFound("SKU "+e.getKey());
   products.put(e.getKey(),p);
   total=total.add(p.unitPrice.multiply(BigDecimal.valueOf(e.getValue())).setScale(2,RoundingMode.HALF_UP));
  }
  QuoteEntity q=new QuoteEntity();q.id=id;q.customerId=req.customerId();q.total=total;q.createdAt=Instant.now();q.expiresAt=q.createdAt.plus(Duration.ofMinutes(15));em.persist(q);
  for(var e:quantities.entrySet()) {
   ProductEntity p=products.get(e.getKey());
   BigDecimal lineTotal=p.unitPrice.multiply(BigDecimal.valueOf(e.getValue())).setScale(2,RoundingMode.HALF_UP);
   QuoteItemEntity qi=new QuoteItemEntity();qi.quoteId=id;qi.sku=p.sku;qi.quantity=e.getValue();qi.unitPrice=p.unitPrice;qi.lineTotal=lineTotal;em.persist(qi);
   lines.add(new QuoteLine(p.sku,e.getValue(),p.unitPrice,lineTotal));
  }
  return new QuoteResponse(id,q.customerId,lines,total,q.expiresAt);
 }
 @Transactional
 public OrderResponse confirm(UUID quoteId,String key,ConfirmRequest req) {
  if(key==null || key.isBlank() || key.length()>200) throw new DomainException("IDEMPOTENCY_KEY_REQUIRED","Idempotency-Key header is required (max 200 chars)",HttpStatus.BAD_REQUEST);
  String hash=sha256(quoteId+"|"+req.actorId());
  jdbc.update("INSERT INTO idempotency_records(idempotency_key,request_hash) VALUES (?,?) ON CONFLICT (idempotency_key) DO NOTHING",key,hash);
  IdempotencyEntity idem=(IdempotencyEntity)em.createNativeQuery("SELECT * FROM idempotency_records WHERE idempotency_key = ? FOR UPDATE",IdempotencyEntity.class).setParameter(1,key).getSingleResult();
  if(!idem.requestHash.equals(hash)){metrics.counter("requests.rejected").increment();throw new DomainException("IDEMPOTENCY_KEY_REUSED","Idempotency-Key was already used with a different payload",HttpStatus.UNPROCESSABLE_ENTITY);}
  if(idem.orderId!=null){metrics.counter("requests.replayed").increment();return response(em.find(OrderEntity.class,idem.orderId));}
  QuoteEntity q=(QuoteEntity)em.createNativeQuery("SELECT * FROM quotes WHERE id = ? FOR UPDATE",QuoteEntity.class).setParameter(1,quoteId).getResultStream().findFirst().orElseThrow(()->DomainException.notFound("Quote"));
  if(!q.expiresAt.isAfter(Instant.now())) throw new DomainException("QUOTE_EXPIRED","Quote has expired",HttpStatus.CONFLICT);
  List<QuoteItemEntity> items=em.createQuery("from QuoteItemEntity where quoteId=:id order by sku",QuoteItemEntity.class).setParameter("id",quoteId).getResultList();
  // Atomic conditional decrement prevents overselling across different quotes/transactions.
  for(QuoteItemEntity item:items) {
   int changed=jdbc.update("UPDATE products SET stock_quantity=stock_quantity-? WHERE sku=? AND stock_quantity>=?",item.quantity,item.sku,item.quantity);
   if(changed!=1) throw new DomainException("INSUFFICIENT_STOCK","Insufficient stock for SKU "+item.sku,HttpStatus.CONFLICT);
  }
  CustomerEntity c=(CustomerEntity)em.createNativeQuery("SELECT * FROM customers WHERE id = ? FOR UPDATE",CustomerEntity.class).setParameter(1,q.customerId).getSingleResult();
  PolicyDecision decision=PolicyDecision.decide(q.total,c.creditLimit,c.creditUsed,threshold);
  OrderEntity o=new OrderEntity();o.id=UUID.randomUUID();o.quoteId=quoteId;o.customerId=q.customerId;o.total=q.total;o.status=decision.status();o.createdAt=Instant.now();em.persist(o);
  for(QuoteItemEntity item:items){OrderItemEntity oi=new OrderItemEntity();oi.orderId=o.id;oi.sku=item.sku;oi.quantity=item.quantity;oi.unitPrice=item.unitPrice;oi.lineTotal=item.lineTotal;em.persist(oi);}
  if(decision.status()==OrderStatus.APPROVED) c.creditUsed=c.creditUsed.add(q.total);
  idem.orderId=o.id;
  audit(o.id,req.actorId(),"POLICY_DECISION",Map.of("total",q.total,"threshold",threshold,"creditLimit",c.creditLimit,"creditUsedBefore",c.creditUsed.subtract(decision.status()==OrderStatus.APPROVED?q.total:BigDecimal.ZERO)),Map.of("status",decision.status(),"reason",decision.reason()));
  audit(o.id,req.actorId(),"ORDER_CREATED",Map.of("quoteId",quoteId,"items",items.size()),Map.of("orderId",o.id,"status",o.status));
  metrics.counter("orders.created").increment(); return response(o);
 }
 @Transactional
 public OrderResponse review(UUID id,ReviewRequest req) {
  OrderEntity o=(OrderEntity)em.createNativeQuery("SELECT * FROM orders WHERE id = ? FOR UPDATE",OrderEntity.class).setParameter(1,id).getResultStream().findFirst().orElseThrow(()->DomainException.notFound("Order"));
  if(o.status!=OrderStatus.PENDING_REVIEW)throw new DomainException("ORDER_NOT_REVIEWABLE","Only PENDING_REVIEW orders can be reviewed",HttpStatus.CONFLICT);
  String decision=req.decision().toLowerCase(Locale.ROOT);
  if(!decision.equals("approve")&&!decision.equals("reject"))throw new DomainException("INVALID_DECISION","Decision must be approve or reject",HttpStatus.BAD_REQUEST);
  o.status=decision.equals("approve")?OrderStatus.APPROVED:OrderStatus.REJECTED;o.reviewer=req.reviewer();o.reviewReason=req.reason();o.reviewedAt=Instant.now();
  if(o.status==OrderStatus.APPROVED) {
   CustomerEntity c=(CustomerEntity)em.createNativeQuery("SELECT * FROM customers WHERE id = ? FOR UPDATE",CustomerEntity.class).setParameter(1,o.customerId).getSingleResult();c.creditUsed=c.creditUsed.add(o.total);
  }
  audit(id,req.reviewer(),"ORDER_REVIEW",Map.of("decision",decision,"reason",req.reason()),Map.of("status",o.status));
  metrics.counter("orders.reviews").increment();return response(o);
 }
 @Transactional(readOnly=true)
 public List<AuditResponse> audit(UUID id) {
  if(!em.createQuery("select count(o) from OrderEntity o where o.id=:id",Long.class).setParameter("id",id).getSingleResult().equals(1L))throw DomainException.notFound("Order");
  return em.createQuery("from AuditEntity where orderId=:id order by id",AuditEntity.class).setParameter("id",id).getResultList()
   .stream().map(a->new AuditResponse(a.id,a.actor,a.action,parse(a.inputs),parse(a.result),a.occurredAt)).toList();
 }
 private void audit(UUID id,String actor,String action,Object inputs,Object result){
  try{AuditEntity a=new AuditEntity();a.orderId=id;a.actor=actor;a.action=action;a.inputs=mapper.writeValueAsString(inputs);a.result=mapper.writeValueAsString(result);a.occurredAt=Instant.now();em.persist(a);}
  catch(Exception e){throw new IllegalStateException("Cannot write audit record",e);}
 }
 private Object parse(String s){try{return mapper.readTree(s);}catch(Exception e){return s;}}
 private OrderResponse response(OrderEntity o){return new OrderResponse(o.id,o.quoteId,o.customerId,o.total,o.status,o.createdAt);}
 private String sha256(String s){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
}
