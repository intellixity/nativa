package io.intellixity.nativa.examples.web;

import io.intellixity.nativa.examples.domain.Order;
import io.intellixity.nativa.examples.service.OrderService;
import io.intellixity.nativa.persistence.query.Query;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
public final class OrderController {
  private final OrderService orders;

  public OrderController(OrderService orders) {
    this.orders = orders;
  }

  public record CreateOrderRequest(UUID customerId,
                                   UUID serviceId,
                                   Double totalAmount,
                                   String currency,
                                   String paymentMethod) {}

  @PostMapping
  public Order create(@RequestBody CreateOrderRequest req) {
    return orders.create(req.customerId, req.serviceId, req.totalAmount, req.currency, req.paymentMethod);
  }

  @GetMapping("/{id}")
  public ResponseEntity<Order> get(@PathVariable("id") UUID id) {
    Order o = orders.get(id);
    return (o == null) ? ResponseEntity.notFound().build() : ResponseEntity.ok(o);
  }

  @PostMapping("/search")
  public List<Order> search(@RequestBody(required = false) Query query) {
    return orders.search(query);
  }

  @PostMapping("/count")
  public long count(@RequestBody(required = false) Query query) {
    return orders.count(query);
  }
}


