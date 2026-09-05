package lk.sunrise.clinic.support;

import lk.sunrise.clinic.domain.TreatmentType;
import lk.sunrise.clinic.repository.TreatmentTypeRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Test double for {@link TreatmentTypeRepository}, written by hand for the
 * same reason as InMemoryClinicRepository: it holds state, so a test can
 * assert that a price really changed rather than that a method was called.
 */
public class InMemoryTreatmentTypeRepository implements TreatmentTypeRepository {

    private final Map<String, TreatmentType> byCode = new LinkedHashMap<>();
    private long nextId = 1L;

    public InMemoryTreatmentTypeRepository seed(String code, String name,
                                                String price, int minutes, boolean active) {
        byCode.put(code, new TreatmentType(nextId++, code, name,
                new BigDecimal(price), minutes, active));
        return this;
    }

    @Override
    public List<TreatmentType> findAllActive() {
        List<TreatmentType> out = new ArrayList<>();
        byCode.values().forEach(t -> { if (t.isActive()) out.add(t); });
        return out;
    }

    @Override
    public List<TreatmentType> findAll() {
        return new ArrayList<>(byCode.values());
    }

    @Override
    public Optional<TreatmentType> findByCode(String code) {
        return Optional.ofNullable(byCode.get(code));
    }

    @Override
    public String insert(String code, String name, BigDecimal price, int durationMinutes) {
        byCode.put(code, new TreatmentType(nextId++, code, name, price, durationMinutes, true));
        return code;
    }

    @Override
    public int update(String code, String name, BigDecimal price,
                      int durationMinutes, boolean active) {
        TreatmentType existing = byCode.get(code);
        if (existing == null) {
            return 0;
        }
        byCode.put(code, new TreatmentType(existing.getId(), code, name,
                price, durationMinutes, active));
        return 1;
    }
}
