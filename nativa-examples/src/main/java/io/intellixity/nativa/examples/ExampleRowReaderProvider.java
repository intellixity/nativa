package io.intellixity.nativa.examples;

import io.intellixity.nativa.persistence.mapping.RowReader;
import io.intellixity.nativa.persistence.mapping.RowReaderProvider;

import java.util.Map;

// Generated classes (from YAML):
import io.intellixity.nativa.examples.domain.AppointmentRowReader;
import io.intellixity.nativa.examples.domain.CustomerRowReader;
import io.intellixity.nativa.examples.domain.OrderRowReader;
import io.intellixity.nativa.examples.domain.ServiceItemRowReader;

public final class ExampleRowReaderProvider implements RowReaderProvider {
  @Override
  public Map<String, RowReader<?>> rowReadersByType() {
    return Map.of(
        "Customer", CustomerRowReader.INSTANCE,
        "Service", ServiceItemRowReader.INSTANCE,
        "Order", OrderRowReader.INSTANCE,
        "Appointment", AppointmentRowReader.INSTANCE
    );
  }
}


