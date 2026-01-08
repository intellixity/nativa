package io.intellixity.nativa.examples.service;

import io.intellixity.nativa.examples.engine.Engines;
import io.intellixity.nativa.persistence.exec.EntityViewRef;
import io.intellixity.nativa.persistence.query.Query;
import io.intellixity.nativa.persistence.query.QueryFilters;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

import io.intellixity.nativa.examples.domain.ServiceItem;

@Service
public final class ServiceCatalogService {
  private static final EntityViewRef SERVICE_TABLE = new EntityViewRef("Service", "service_table");

  private final Engines engines;

  public ServiceCatalogService(Engines engines) {
    this.engines = engines;
  }

  public ServiceItem create(ServiceItem s) {
    if (s.id() == null) s.id(UUID.randomUUID());
    return engines.jdbc(false).insert(SERVICE_TABLE, s);
  }

  public ServiceItem get(UUID id) {
    Query q = Query.and(QueryFilters.eq("id", id));
    List<ServiceItem> rows = engines.jdbc(true).select(SERVICE_TABLE, q);
    return rows.isEmpty() ? null : rows.getFirst();
  }
}




