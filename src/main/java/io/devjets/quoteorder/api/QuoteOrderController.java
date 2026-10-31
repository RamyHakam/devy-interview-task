package io.devjets.quoteorder.api;
import io.devjets.quoteorder.api.ApiModels.*;
import io.devjets.quoteorder.service.QuoteOrderService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController
public class QuoteOrderController {
 private final QuoteOrderService service;
 public QuoteOrderController(QuoteOrderService service){this.service=service;}
 @PostMapping("/quotes") public QuoteResponse quote(@Valid @RequestBody QuoteRequest req){return service.createQuote(req);}
 @PostMapping("/quotes/{id}/confirm") public OrderResponse confirm(@PathVariable UUID id,@RequestHeader(value="Idempotency-Key",required=false) String key,@Valid @RequestBody ConfirmRequest req){return service.confirm(id,key,req);}
 @PostMapping("/orders/{id}/review") public OrderResponse review(@PathVariable UUID id,@Valid @RequestBody ReviewRequest req){return service.review(id,req);}
 @GetMapping("/orders/{id}/audit") public List<AuditResponse> audit(@PathVariable UUID id){return service.audit(id);}
}
