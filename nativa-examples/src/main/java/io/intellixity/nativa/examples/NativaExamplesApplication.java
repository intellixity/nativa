package io.intellixity.nativa.examples;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class NativaExamplesApplication {
  static void main(String[] args) {
    SpringApplication.run(NativaExamplesApplication.class, args);
  }
}


