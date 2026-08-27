package lk.sunrise.client.ui;

import com.fasterxml.jackson.databind.JsonNode;
import lk.sunrise.client.net.ClinicServiceProxy;
import lk.sunrise.client.net.SessionHolder;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * UC-10 Search and Display Appointment, plus the lifecycle actions
 * UC-11 / UC-13 (FR-13, FR-14, FR-15, FR-16).
 *
 * The brief asks for a separate window for entering and one for viewing. This
 * is the viewing window: it never writes appointment data itself, it asks the
 * service to change status and then re-reads.
 */
public class SearchAppointmentFrame extends JFrame {

    private final ClinicServiceProxy api = new ClinicServiceProxy();

    private final JTextField appointmentNo = new JTextField(18);
    private final JTextField dayField = new JTextField(LocalDate.now().toString(), 12);
    private final JTextArea detail = new JTextArea(10, 40);
    private final DefaultTableModel dayModel =
            Ui.model("Appointment no", "Time", "Patient", "Dentist", "Status");
    private final JTable dayTable = Ui.readOnlyTable(dayModel);

    public SearchAppointmentFrame() {
        super("Search appointments");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());

        add(Ui.header("Search appointments",
                "Look up one appointment by number, or browse a whole day"), BorderLayout.NORTH);
        add(buildBody(), BorderLayout.CENTER);
        add(buildActions(), BorderLayout.SOUTH);

        pack();
        setMinimumSize(new Dimension(940, 600));
        setLocationRelativeTo(null);

        loadDay();
    }

    private JComponent buildBody() {
        // ---- top: single lookup ------------------------------------
        JPanel lookup = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 10));
        lookup.setBackground(Color.WHITE);
        JButton find = Ui.secondary("Find");
        find.setPreferredSize(new Dimension(90, 30));
        JButton loadDay = Ui.secondary("Show day");
        loadDay.setPreferredSize(new Dimension(110, 30));

        lookup.add(new JLabel("Appointment number:"));
        lookup.add(appointmentNo);
        lookup.add(find);
        lookup.add(Box.createHorizontalStrut(24));
        lookup.add(new JLabel("Day (YYYY-MM-DD):"));
        lookup.add(dayField);
        lookup.add(loadDay);

        find.addActionListener(e -> findOne());
        appointmentNo.addActionListener(e -> findOne());
        loadDay.addActionListener(e -> loadDay());

        // ---- detail pane -------------------------------------------
        detail.setEditable(false);
        detail.setFont(Ui.MONO);
        detail.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));

        dayTable.getSelectionModel().addListSelectionListener(e -> {
            int row = dayTable.getSelectedRow();
            if (row >= 0) {
                appointmentNo.setText(String.valueOf(dayModel.getValueAt(row, 0)));
            }
        });

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(dayTable), new JScrollPane(detail));
        split.setResizeWeight(0.55);

        JPanel body = new JPanel(new BorderLayout());
        body.add(lookup, BorderLayout.NORTH);
        body.add(split, BorderLayout.CENTER);
        return body;
    }

    private JPanel buildActions() {
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 10));
        actions.setBackground(Ui.SURFACE);
        actions.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Ui.LINE));

        // FR-05 - the buttons a role cannot use are not shown.
        if (SessionHolder.getInstance().hasRole("RECEPTIONIST", "ADMINISTRATOR")) {
            JButton cancel = Ui.secondary("Cancel appointment");
            cancel.setPreferredSize(new Dimension(170, 34));
            cancel.addActionListener(e -> cancelAppointment());
            actions.add(cancel);
        }
        if (SessionHolder.getInstance().hasRole("DENTIST", "ADMINISTRATOR")) {
            JButton noShow = Ui.secondary("Mark no-show");
            noShow.setPreferredSize(new Dimension(150, 34));
            noShow.addActionListener(e -> changeStatus("no-show"));
            actions.add(noShow);

            JButton complete = Ui.primary("Mark completed");
            complete.setPreferredSize(new Dimension(170, 34));
            complete.addActionListener(e -> changeStatus("complete"));
            actions.add(complete);
        }

        JButton close = Ui.secondary("Close");
        close.addActionListener(e -> dispose());
        actions.add(close);
        return actions;
    }

    // =================================================================

    private void findOne() {
        String no = appointmentNo.getText().trim();
        if (no.isEmpty()) {
            Ui.warn(this, "Enter an appointment number, for example APT-2026-000005.");
            return;
        }
        final JsonNode[] a = new JsonNode[1];
        Ui.background(getRootPane(),
                () -> a[0] = api.findAppointment(no),
                () -> detail.setText(format(a[0])),
                this);
    }

    private void loadDay() {
        LocalDate day;
        try {
            day = LocalDate.parse(dayField.getText().trim());
        } catch (DateTimeParseException ex) {
            Ui.warn(this, "Day must be in the form YYYY-MM-DD.");
            return;
        }
        final JsonNode[] list = new JsonNode[1];
        Ui.background(getRootPane(),
                () -> list[0] = api.appointmentsOn(day),
                () -> {
                    dayModel.setRowCount(0);
                    list[0].forEach(n -> dayModel.addRow(new Object[]{
                            n.get("appointmentNo").asText(),
                            n.get("startTime").asText() + " - " + n.get("endTime").asText(),
                            n.get("patientName").asText(),
                            n.get("dentistName").asText(),
                            n.get("status").asText()}));
                    if (dayModel.getRowCount() == 0) {
                        detail.setText("No appointments on " + day + ".");
                    }
                },
                this);
    }

    private void cancelAppointment() {
        String no = appointmentNo.getText().trim();
        if (no.isEmpty()) {
            Ui.warn(this, "Select or enter an appointment number first.");
            return;
        }
        String reason = JOptionPane.showInputDialog(this,
                "Why is appointment " + no + " being cancelled?", "Cancel appointment",
                JOptionPane.QUESTION_MESSAGE);
        if (reason == null) {
            return;
        }
        if (reason.isBlank()) {
            Ui.warn(this, "A reason is required so the cancellation can be audited.");
            return;
        }
        Ui.background(getRootPane(),
                () -> api.cancel(no, reason),
                () -> {
                    Ui.info(this, "Appointment " + no + " cancelled.");
                    findOne();
                    loadDay();
                },
                this);
    }

    private void changeStatus(String action) {
        String no = appointmentNo.getText().trim();
        if (no.isEmpty()) {
            Ui.warn(this, "Select or enter an appointment number first.");
            return;
        }
        Ui.background(getRootPane(),
                () -> {
                    if ("complete".equals(action)) {
                        api.complete(no);
                    } else {
                        api.noShow(no);
                    }
                },
                () -> {
                    Ui.info(this, "Appointment " + no + " updated.");
                    findOne();
                    loadDay();
                },
                this);
    }

    private String format(JsonNode a) {
        return """
               Appointment   : %s   [%s]
               ---------------------------------------------------------
               Patient       : %s   (%s)
               Contact       : %s
               Dentist       : %s   -   %s
               Date and time : %s   %s to %s
               Treatments    : %s
               Treatment cost: LKR %s
               Consultation  : LKR %s
               Notes         : %s
               """.formatted(
                text(a, "appointmentNo"), text(a, "status"),
                text(a, "patientName"), text(a, "patientNo"),
                text(a, "contactNumber"),
                text(a, "dentistName"), text(a, "specialisation"),
                text(a, "appointmentDate"), text(a, "startTime"), text(a, "endTime"),
                text(a, "treatments"),
                text(a, "treatmentSubtotal"), text(a, "consultationFee"),
                text(a, "notes"));
    }

    private String text(JsonNode n, String field) {
        return n == null || !n.hasNonNull(field) ? "-" : n.get(field).asText();
    }
}
