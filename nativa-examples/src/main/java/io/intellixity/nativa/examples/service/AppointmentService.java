package io.intellixity.nativa.examples.service;

import io.intellixity.nativa.examples.engine.Engines;
import io.intellixity.nativa.persistence.exec.EntityViewRef;
import io.intellixity.nativa.persistence.query.Query;
import io.intellixity.nativa.persistence.query.QueryFilters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.intellixity.nativa.examples.domain.Appointment;
import io.intellixity.nativa.examples.domain.Order;

@Service
public final class AppointmentService {
  private static final EntityViewRef APPT_TABLE = new EntityViewRef("Appointment", "appointment_table");
  private static final EntityViewRef APPT_VIEW = new EntityViewRef("Appointment", "appointment_view");

  private final Engines engines;
  private final OrderService orders;

  public AppointmentService(Engines engines, OrderService orders) {
    this.engines = engines;
    this.orders = orders;
  }

  public Appointment create(UUID orderId, Instant scheduledAt) {
    Order o = orders.get(orderId);
    if (o == null) throw new IllegalArgumentException("Order not found: " + orderId);

    Appointment a = Appointment.builder()
        .id(UUID.randomUUID())
        .order(o)
        .customer(o.customer())
        .service(o.service())
        .scheduledAt(scheduledAt)
        .status("SCHEDULED")
        .createdAt(Instant.now())
        .build();

    return engines.jdbc(false).insert(APPT_TABLE, a);
  }

  public Appointment get(UUID id) {
    Query q = Query.and(QueryFilters.eq("id", id));
    List<Appointment> rows = engines.jdbc(true).select(APPT_VIEW, q);
    return rows.isEmpty() ? null : rows.getFirst();
  }

  public List<Appointment> search(Query query) {
    Query q = (query == null) ? new Query() : query;
    return engines.jdbc(true).select(APPT_VIEW, q);
  }

  public long count(Query query) {
    Query q = (query == null) ? new Query() : query;
    return engines.jdbc(true).count(APPT_VIEW, q);
  }
}


