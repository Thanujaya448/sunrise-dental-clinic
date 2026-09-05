package lk.sunrise.clinic.repository;

import lk.sunrise.clinic.domain.TreatmentType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * DAO implementation of {@link TreatmentTypeRepository}.
 *
 * Every query is parameterised. String-concatenated SQL would be open to
 * injection, which the ETHICAL outcome's "secure coding practices" rules
 * out (NFR-06).
 */
@Repository
public class JdbcTreatmentTypeRepository implements TreatmentTypeRepository {

    private static final String COLUMNS =
            "treatment_id, code, name, price, duration_minutes, active";

    private final JdbcTemplate jdbc;

    public JdbcTreatmentTypeRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<TreatmentType> MAPPER = (rs, rowNum) ->
            new TreatmentType(
                    rs.getLong("treatment_id"),
                    rs.getString("code"),
                    rs.getString("name"),
                    rs.getBigDecimal("price"),
                    rs.getInt("duration_minutes"),
                    rs.getBoolean("active"));

    @Override
    public List<TreatmentType> findAllActive() {
        return jdbc.query(
                "SELECT " + COLUMNS + " FROM treatment_type WHERE active = TRUE ORDER BY price",
                MAPPER);
    }

    @Override
    public List<TreatmentType> findAll() {
        return jdbc.query("SELECT " + COLUMNS + " FROM treatment_type ORDER BY code", MAPPER);
    }

    @Override
    public String insert(String code, String name, java.math.BigDecimal price, int durationMinutes) {
        jdbc.update("INSERT INTO treatment_type (code, name, price, duration_minutes, active) "
                  + "VALUES (?, ?, ?, ?, TRUE)", code, name, price, durationMinutes);
        return code;
    }

    @Override
    public int update(String code, String name, java.math.BigDecimal price,
                      int durationMinutes, boolean active) {
        return jdbc.update("UPDATE treatment_type SET name = ?, price = ?, "
                         + "duration_minutes = ?, active = ? WHERE code = ?",
                           name, price, durationMinutes, active, code);
    }

    @Override
    public Optional<TreatmentType> findByCode(String code) {
        return jdbc.query(
                        "SELECT " + COLUMNS + " FROM treatment_type WHERE code = ?",
                        MAPPER, code)
                .stream()
                .findFirst();
    }
}
