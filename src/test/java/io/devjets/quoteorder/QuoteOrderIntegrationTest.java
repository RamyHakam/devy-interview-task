package io.devjets.quoteorder;
import io.devjets.quoteorder.api.ApiModels.*;
import io.devjets.quoteorder.domain.OrderStatus;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest
class QuoteOrderIntegrationTest {
 @Container static PostgreSQLContainer<?> db=new PostgreSQLContainer<>("postgres:16-alpine").withDatabaseName("quotes").withUsername("quotes").withPassword("quotes");
 @DynamicPropertySource static void props(DynamicPropertyRegistry r){r.add("spring.datasource.url",db::getJdbcUrl);r.add("spring.datasource.username",db::getUsername);r.add("spring.datasource.password",db::getPassword);}
 @Autowired io.devjets.quoteorder.service.QuoteOrderService service;
 @Autowired JdbcTemplate jdbc;
 @BeforeEach void reset(){jdbc.update("DELETE FROM audit_records");jdbc.update("DELETE FROM order_items");jdbc.update("DELETE FROM idempotency_records");jdbc.update("DELETE FROM orders");jdbc.update("DELETE FROM quote_items");jdbc.update("DELETE FROM quotes");jdbc.update("UPDATE products SET stock_quantity=1000");jdbc.update("UPDATE customers SET credit_used=0");}
 QuoteResponse quote(int qty){return service.createQuote(new QuoteRequest(1L,List.of(new QuoteLineRequest("BOLT-M12",qty))));}
 @Test void replayReturnsSameOrder(){var q=quote(2);var a=service.confirm(q.id(),"same-key",new ConfirmRequest("agent-1"));var b=service.confirm(q.id(),"same-key",new ConfirmRequest("agent-1"));assertEquals(a.id(),b.id());assertEquals(1,jdbc.queryForObject("select count(*) from orders",Integer.class));}
 @Test void sameKeyDifferentPayloadIs422(){var q=quote(1);service.confirm(q.id(),"key-2",new ConfirmRequest("agent-a"));var ex=assertThrows(io.devjets.quoteorder.service.DomainException.class,()->service.confirm(q.id(),"key-2",new ConfirmRequest("agent-b")));assertEquals("IDEMPOTENCY_KEY_REUSED",ex.code);}
 @Test void twentyParallelConfirmsCreateOneOrder() throws Exception {var q=quote(1);var pool=Executors.newFixedThreadPool(20);try{var tasks=new ArrayList<Callable<OrderResponse>>();for(int i=0;i<20;i++)tasks.add(()->service.confirm(q.id(),"parallel-key",new ConfirmRequest("agent")));var results=pool.invokeAll(tasks);UUID id=null;for(var f:results){var value=f.get();if(id==null)id=value.id();assertEquals(id,value.id());}assertEquals(1,jdbc.queryForObject("select count(*) from orders",Integer.class));}finally{pool.shutdownNow();}}
 @Test void competingQuotesNeverOversell() throws Exception {jdbc.update("update products set stock_quantity=5 where sku='BOLT-M12'");var quotes=new ArrayList<QuoteResponse>();for(int i=0;i<10;i++)quotes.add(quote(1));var pool=Executors.newFixedThreadPool(10);try{var tasks=new ArrayList<Callable<Boolean>>();for(int i=0;i<10;i++){final int n=i;tasks.add(()->{try{service.confirm(quotes.get(n).id(),"stock-"+n,new ConfirmRequest("agent"));return true;}catch(io.devjets.quoteorder.service.DomainException e){return false;}});}int succeeded=0;for(var f:pool.invokeAll(tasks))if(f.get())succeeded++;assertEquals(5,succeeded);assertEquals(0,jdbc.queryForObject("select stock_quantity from products where sku='BOLT-M12'",Integer.class));assertEquals(5,jdbc.queryForObject("select count(*) from orders",Integer.class));}finally{pool.shutdownNow();}}
}
