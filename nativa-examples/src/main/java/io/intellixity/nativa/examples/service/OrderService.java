package io.intellixity.nativa.examples.service;

import io.intellixity.nativa.examples.engine.Engines;
import io.intellixity.nativa.persistence.exec.EntityViewRef;
import io.intellixity.nativa.persistence.query.Query;
import io.intellixity.nativa.persistence.query.QueryFilters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.intellixity.nativa.examples.domain.Customer;
import io.intellixity.nativa.examples.domain.Order;
import io.intellixity.nativa.examples.domain.ServiceItem;

@Service
public final class OrderService {
  private static final EntityViewRef ORDER_TABLE = new EntityViewRef("Order", "order_table");
  private static final EntityViewRef ORDER_VIEW = new EntityViewRef("Order", "order_view");

  private final Engines engines;
  private final CustomerService customers;
  private final ServiceCatalogService services;

  public OrderService(Engines engines, CustomerService customers, ServiceCatalogService services) {
    this.engines = engines;
    this.customers = customers;
    this.services = services;
  }

  public Order create(UUID customerId,
                      UUID serviceId,
                      Double totalAmount,
                      String currency,
                      String paymentMethod) {
    Customer c = customers.get(customerId);
    if (c == null) throw new IllegalArgumentException("Customer not found: " + customerId);
    ServiceItem s = services.get(serviceId);
    if (s == null) throw new IllegalArgumentException("Service not found: " + serviceId);

    Order o = Order.builder()
        .id(UUID.randomUUID())
        .createdAt(Instant.now())
        .customer(c)
        .service(s)
        .totalAmount(totalAmount)
        .currency(currency)
        .paymentMethod(paymentMethod)
        .paymentStatus("PENDING")
        .build();
    return engines.jdbc(false).insert(ORDER_TABLE, o);
  }

  public Order get(UUID id) {
    Query q = Query.and(QueryFilters.eq("id", id));
    List<Order> rows = engines.jdbc(true).select(ORDER_VIEW, q);
    return rows.isEmpty() ? null : rows.getFirst();
  }

  public List<Order> search(Query query) {
    Query q = (query == null) ? new Query() : query;
    return engines.jdbc(true).select(ORDER_VIEW, q);
  }

  public long count(Query query) {
    Query q = (query == null) ? new Query() : query;
    return engines.jdbc(true).count(ORDER_VIEW, q);
  }
}


