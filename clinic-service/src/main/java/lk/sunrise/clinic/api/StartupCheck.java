package lk.sunrise.clinic.api;

import lk.sunrise.clinic.repository.TreatmentTypeRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Prints a one-line confirmation at start-up that TIER 2 really reached
 * TIER 3. Without it, a broken datasource only shows up on the first
 * request, which is a confusing place to discover it.
 */
@Component
public class StartupCheck implements CommandLineRunner {

    private final TreatmentTypeRepository repository;

    public StartupCheck(TreatmentTypeRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(String... args) {
        int count = repository.findAllActive().size();
        System.out.println("=================================================");
        System.out.println(" Sunrise Clinic service started on port 8080");
        System.out.println(" MySQL connected - " + count + " active treatment types loaded");
        System.out.println(" Try: http://localhost:8080/api/treatments");
        System.out.println("=================================================");
    }
}
