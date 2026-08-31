package io.github.dancan254.logguard.demo.leaks;

import io.github.dancan254.logguard.demo.customer.Customer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

/** One endpoint per masked channel, so each can be watched on the console and at the collector. */
@RestController
@RequestMapping("/leaks")
public class LeakController {

    private static final Logger log = LoggerFactory.getLogger(LeakController.class);

    @GetMapping("/mdc")
    public ResponseEntity<String> mdc() {
        MDC.put("actor", "jane.wanjiru@acme.io");
        MDC.put("customerName", "Jane Wanjiru");
        MDC.put("requestId", "7f3a-11");
        try {
            log.info("handling a request with the caller in the MDC");
            return ResponseEntity.ok("logged with MDC");
        } finally {
            MDC.clear();
        }
    }

    @GetMapping("/kv")
    public ResponseEntity<String> keyValuePairs() {
        log.atInfo()
                .addKeyValue("actor", "jane.wanjiru@acme.io")
                .addKeyValue("customer", sampleCustomer())
                .addKeyValue("requestId", "7f3a-11")
                .log("structured event");

        return ResponseEntity.ok("logged key-value pairs");
    }

    @GetMapping("/exception")
    public ResponseEntity<String> exception() {
        SQLException root = new SQLException(
                "duplicate key value violates unique constraint: Key (email)=(jane.wanjiru@acme.io) already exists");
        log.error("could not save the customer", new IllegalStateException("insert failed", root));

        return ResponseEntity.ok("logged an exception");
    }

    @GetMapping("/nested")
    public ResponseEntity<String> nested() {
        log.info("order {}", new Order("ORD-9", sampleCustomer(), List.of(sampleCustomer())));

        return ResponseEntity.ok("logged a nested object");
    }

    private static Customer sampleCustomer() {
        return new Customer(1L, "jane.wanjiru@acme.io", "+254712345891", "31234567",
                LocalDate.of(1994, 3, 11), "Nairobi");
    }

    /** No annotation of its own: it is masked because of what it holds. */
    private record Order(String reference, Customer buyer, List<Customer> recipients) {
    }
}
