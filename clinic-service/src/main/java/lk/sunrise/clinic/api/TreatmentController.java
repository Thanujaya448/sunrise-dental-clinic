package lk.sunrise.clinic.api;

import lk.sunrise.clinic.domain.TreatmentType;
import lk.sunrise.clinic.repository.TreatmentTypeRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * First REST endpoint of TIER 2.
 *
 * Its purpose is to prove the whole stack end to end: an HTTP request
 * reaches the service, the service reads MySQL, and JSON comes back. Once
 * this works, every later endpoint is the same shape with more logic.
 */
@RestController
@RequestMapping("/api/treatments")
public class TreatmentController {

    private final TreatmentTypeRepository repository;

    public TreatmentController(TreatmentTypeRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<TreatmentType> listActive() {
        return repository.findAllActive();
    }

    @GetMapping("/{code}")
    public ResponseEntity<TreatmentType> byCode(@PathVariable String code) {
        return repository.findByCode(code)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
