package io.intellixity.nativa.examples.web;

import io.intellixity.nativa.examples.domain.Customer;
import io.intellixity.nativa.examples.service.CustomerService;
import io.intellixity.nativa.persistence.query.Query;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/customers")
public final class CustomerController {
  private final CustomerService customers;

  public CustomerController(CustomerService customers) {
    this.customers = customers;
  }

  public record CreateCustomerRequest(String firstName, String lastName, String email, String phone) {}

  @PostMapping
  public Customer create(@RequestBody CreateCustomerRequest req) {
    Customer c = Customer.builder()
        .firstName(req.firstName)
        .lastName(req.lastName)
        .email(req.email)
        .phone(req.phone)
        .build();

    return customers.create(c);
  }

  @GetMapping("/{id}")
  public ResponseEntity<Customer> get(@PathVariable("id") UUID id) {
    Customer c = customers.get(id);
    return (c == null) ? ResponseEntity.notFound().build() : ResponseEntity.ok(c);
  }

  @PostMapping(value = "/search", consumes = MediaType.APPLICATION_JSON_VALUE)
  public List<Customer> search(@RequestBody Query query) {
    return customers.search(query);
  }

  @PostMapping("/count")
  public long count(@RequestBody(required = false) Query query) {
    return customers.count(query);
  }
}



