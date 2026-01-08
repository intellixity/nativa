package io.intellixity.nativa.examples.web;

import io.intellixity.nativa.examples.domain.ServiceItem;
import io.intellixity.nativa.examples.service.ServiceCatalogService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/services")
public final class ServiceController {
  private final ServiceCatalogService services;

  public ServiceController(ServiceCatalogService services) {
    this.services = services;
  }

  public record CreateServiceRequest(String code, String name, String description, Double price, String currency) {}

  @PostMapping
  public ServiceItem create(@RequestBody CreateServiceRequest req) {
    ServiceItem s = ServiceItem.builder()
        .code(req.code)
        .name(req.name)
        .description(req.description)
        .price(req.price)
        .currency(req.currency)
        .active(true)
        .build();
    return services.create(s);
  }

  @GetMapping("/{id}")
  public ResponseEntity<ServiceItem> get(@PathVariable("id") UUID id) {
    ServiceItem s = services.get(id);
    return (s == null) ? ResponseEntity.notFound().build() : ResponseEntity.ok(s);
  }
}




