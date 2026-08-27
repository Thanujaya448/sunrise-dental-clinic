package lk.sunrise.client.ui;

import com.fasterxml.jackson.databind.JsonNode;
import lk.sunrise.client.net.ClinicServiceProxy;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * UC-21 Management reports. FR-23. Administrator only.
 *
 * Each report is presented with the DECISION it supports, not just a title.
 * The rubric asks for "reports to facilitate decision-making"; a table of
 * numbers with no question attached does not facilitate a decision.
 */
public class ReportFrame extends JFrame {

    private final ClinicServiceProxy api = new ClinicServiceProxy();

    private final DefaultListModel<Report> catalogue = new DefaultListModel<>();
    private final JList<Report> list = new JList<>(catalogue);
    private final JLabel question = new JLabel(" ");
    private final DefaultTableModel resultModel = Ui.model("");
    private final JTable results = Ui.readOnlyTable(resultModel);

    private record Report(String id, String title, String question) {
        @Override public String toString() { return title; }
    }

    public ReportFrame() {
        super("Management reports");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());

        add(Ui.header("Management reports",
                "Each report answers one question the clinic manager has to act on"),
                BorderLayout.NORTH);
        add(buildBody(), BorderLayout.CENTER);
        add(buildActions(), BorderLayout.SOUTH);

        pack();
        setMinimumSize(new Dimension(940, 560));
        setLocationRelativeTo(null);

        loadCatalogue();
    }

    private JComponent buildBody() {
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setFont(Ui.BODY);
        list.setFixedCellHeight(30);
        list.addListSelectionListener(e -> {
            Report r = list.getSelectedValue();
            question.setText(r == null ? " " : "<html><i>" + r.question() + "</i></html>");
        });

        JScrollPane left = new JScrollPane(list);
        left.setPreferredSize(new Dimension(260, 0));
        left.setBorder(BorderFactory.createTitledBorder("Available reports"));

        question.setFont(Ui.BODY);
        question.setForeground(Ui.ACCENT);
        question.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));

        JPanel right = new JPanel(new BorderLayout());
        right.setBackground(Color.WHITE);
        right.add(question, BorderLayout.NORTH);
        right.add(new JScrollPane(results), BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
        split.setResizeWeight(0.25);
        return split;
    }

    private JPanel buildActions() {
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 10));
        actions.setBackground(Ui.SURFACE);
        actions.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Ui.LINE));

        JButton run = Ui.primary("Run report");
        run.addActionListener(e -> runSelected());

        JButton close = Ui.secondary("Close");
        close.addActionListener(e -> dispose());

        actions.add(close);
        actions.add(run);
        return actions;
    }

    private void loadCatalogue() {
        final JsonNode[] c = new JsonNode[1];
        Ui.background(getRootPane(),
                () -> c[0] = api.reportCatalogue(),
                () -> {
                    catalogue.clear();
                    c[0].forEach(n -> catalogue.addElement(new Report(
                            n.get("id").asText(),
                            n.get("title").asText(),
                            n.get("question").asText())));
                    if (!catalogue.isEmpty()) {
                        list.setSelectedIndex(0);
                    }
                },
                this);
    }

    private void runSelected() {
        Report r = list.getSelectedValue();
        if (r == null) {
            Ui.warn(this, "Choose a report from the list first.");
            return;
        }
        final JsonNode[] rows = new JsonNode[1];
        Ui.background(getRootPane(),
                () -> rows[0] = api.runReport(r.id()),
                () -> render(rows[0]),
                this);
    }

    /** Builds the columns from whatever the view returned, so a schema change
     *  to a report view needs no change here. */
    private void render(JsonNode rows) {
        resultModel.setRowCount(0);
        resultModel.setColumnCount(0);

        if (rows == null || rows.isEmpty()) {
            Ui.info(this, "That report has no data yet.\n\n"
                    + "Generate a few bills or complete some appointments, then run it again.");
            return;
        }

        List<String> columns = new ArrayList<>();
        rows.get(0).fieldNames().forEachRemaining(columns::add);
        columns.forEach(col -> resultModel.addColumn(prettify(col)));

        rows.forEach(row -> {
            Object[] cells = new Object[columns.size()];
            for (int i = 0; i < columns.size(); i++) {
                JsonNode v = row.get(columns.get(i));
                cells[i] = v == null || v.isNull() ? "-" : v.asText();
            }
            resultModel.addRow(cells);
        });
    }

    private String prettify(String column) {
        String s = column.replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
