package com.example.smoke;

import io.github.dancan254.logguard.MaskStrategy;
import io.github.dancan254.logguard.Pii;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** No log-guard configuration anywhere. The dependency is meant to be enough. */
@SpringBootApplication
public class CleanApplication {

    private static final Logger log = LoggerFactory.getLogger(CleanApplication.class);

    static class Customer {
        Long id = 42L;
        @Pii(strategy = MaskStrategy.PARTIAL)
        String email = "jane.wanjiru@acme.io";
        @Pii
        String nationalId = "31234567";
        String city = "Nairobi";

        @Override
        public String toString() {
            return "Customer(id=" + id + ", email=" + email
                    + ", nationalId=" + nationalId + ", city=" + city + ")";
        }
    }

    public static void main(String[] args) {
        SpringApplication.run(CleanApplication.class, args).close();
        log.info("SMOKE type-aware {}", new Customer());
        log.info("SMOKE pattern mailing jane.wanjiru@acme.io now");
    }
}
