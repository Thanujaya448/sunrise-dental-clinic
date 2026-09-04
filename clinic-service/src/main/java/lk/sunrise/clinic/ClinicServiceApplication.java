package lk.sunrise.clinic;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for TIER 2 of the Sunrise Dental Clinic system.
 *
 * This is a separate operating-system process from the Swing client.
 * The client reaches it only over HTTP, which is what makes the system
 * distributed rather than merely layered (NFR-01).
 *
 * @author Thanujaya Hasaranga Perera (st20374257)
 */
@SpringBootApplication
public class ClinicServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClinicServiceApplication.class, args);
    }
}
