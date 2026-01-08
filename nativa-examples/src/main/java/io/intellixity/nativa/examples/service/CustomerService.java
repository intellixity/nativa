package io.intellixity.nativa.examples.service;

import io.intellixity.nativa.examples.engine.Engines;
import io.intellixity.nativa.persistence.exec.EntityViewRef;
import io.intellixity.nativa.persistence.query.Query;
import io.intellixity.nativa.persistence.query.QueryFilters;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

import io.intellixity.nativa.examples.domain.Customer;

@Service
public final class CustomerService {
  private static final EntityViewRef CUSTOMER_TABLE = new EntityViewRef("Customer", "customer_table");

  private final Engines engines;

  public CustomerService(Engines engines) {
    this.engines = engines;
  }

  public Customer create(Customer c) {
    if (c.id() == null) c.id(UUID.randomUUID());
    return engines.jdbc(false).insert(CUSTOMER_TABLE, c);
  }

  public Customer get(UUID id) {
    Query q = Query.and(QueryFilters.eq("id", id));
    List<Customer> rows = engines.jdbc(true).select(CUSTOMER_TABLE, q);
    return rows.isEmpty() ? null : rows.getFirst();
  }

  public List<Customer> search(Query query) {
    Query q = (query == null) ? new Query() : query;
    return engines.jdbc(true).select(CUSTOMER_TABLE, q);
  }

  public long count(Query query) {
    Query q = (query == null) ? new Query() : query;
    return engines.jdbc(true).count(CUSTOMER_TABLE, q);
  }
}



