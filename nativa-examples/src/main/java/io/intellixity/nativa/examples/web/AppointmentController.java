package io.intellixity.nativa.examples.web;

import io.intellixity.nativa.examples.domain.Appointment;
import io.intellixity.nativa.examples.service.AppointmentService;
import io.intellixity.nativa.persistence.query.Query;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/appointments")
public final class AppointmentController {
  private final AppointmentService appts;

  public AppointmentController(AppointmentService appts) {
    this.appts = appts;
  }

  public record CreateAppointmentRequest(UUID orderId, Instant scheduledAt) {}

  @PostMapping
  public Appointment create(@RequestBody CreateAppointmentRequest req) {
    return appts.create(req.orderId, req.scheduledAt);
  }

  @GetMapping("/{id}")
  public ResponseEntity<Appointment> get(@PathVariable("id") UUID id) {
    Appointment a = appts.get(id);
    return (a == null) ? ResponseEntity.notFound().build() : ResponseEntity.ok(a);
  }

  @PostMapping("/search")
  public List<Appointment> search(@RequestBody(required = false) Query query) {
    return appts.search(query);
  }

  @PostMapping("/count")
  public long count(@RequestBody(required = false) Query query) {
    return appts.count(query);
  }
}


