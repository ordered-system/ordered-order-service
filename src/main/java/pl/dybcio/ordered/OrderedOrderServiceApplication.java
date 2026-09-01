package pl.dybcio.ordered;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class OrderedOrderServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(OrderedOrderServiceApplication.class, args);
  }
}
