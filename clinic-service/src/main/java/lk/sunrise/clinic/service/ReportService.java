package lk.sunrise.clinic.service;

import lk.sunrise.clinic.exception.ClinicExceptions.NotFoundException;
import lk.sunrise.clinic.repository.ClinicRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * FR-23 - reports that support a decision, not just lists of rows.
 *
 * The report name is mapped through a fixed enum before it reaches SQL. The
 * caller can never influence the table name, which is what keeps
 * runReportView() safe despite interpolating a name (NFR-06).
 */
@Service
public class ReportService {

    public enum ReportType {
        DAILY_REVENUE      ("vw_daily_revenue",      "Daily revenue",       "Is income rising or falling, and which days are worth opening?"),
        DENTIST_UTILISATION("vw_dentist_utilisation","Dentist utilisation", "Who is overbooked and who has spare capacity?"),
        TREATMENT_MIX      ("vw_treatment_mix",      "Treatment mix",       "Which treatments earn the practice its money?"),
        ATTENDANCE         ("vw_attendance_rate",    "Attendance and no-shows", "Are reminders working, or is the no-show rate rising?");

        public final String view;
        public final String title;
        public final String question;

        ReportType(String view, String title, String question) {
            this.view = view;
            this.title = title;
            this.question = question;
        }
    }

    private final ClinicRepository repository;

    public ReportService(ClinicRepository repository) {
        this.repository = repository;
    }

    public List<Map<String, Object>> run(String typeName) {
        ReportType type;
        try {
            type = ReportType.valueOf(typeName.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new NotFoundException("No report called " + typeName);
        }
        return repository.runReportView(type.view);
    }

    public List<Map<String, String>> catalogue() {
        return java.util.Arrays.stream(ReportType.values())
                .map(t -> Map.of("id", t.name(), "title", t.title, "question", t.question))
                .toList();
    }
}
