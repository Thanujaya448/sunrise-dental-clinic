package lk.sunrise.client.ui;

import lk.sunrise.client.net.ApiException;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import java.awt.*;

/**
 * Shared look and behaviour for every screen.
 *
 * Centralising it means the six windows cannot drift apart visually, and a
 * change to spacing or colour happens in one place. This is the same
 * reasoning as PercentageDiscount holding the rounding rule once.
 */
public final class Ui {

    public static final Color INK       = new Color(0x0E, 0x2B, 0x2E);
    public static final Color ACCENT    = new Color(0x0C, 0x7B, 0x72);
    public static final Color SURFACE   = new Color(0xF5, 0xF8, 0xF7);
    public static final Color LINE      = new Color(0xD5, 0xE1, 0xDE);
    public static final Color DANGER    = new Color(0x9E, 0x2E, 0x26);

    public static final Font  H1        = new Font("Segoe UI", Font.BOLD, 20);
    public static final Font  H2        = new Font("Segoe UI", Font.BOLD, 14);
    public static final Font  BODY      = new Font("Segoe UI", Font.PLAIN, 13);
    public static final Font  MONO      = new Font("Consolas", Font.PLAIN, 13);

    private Ui() { }

    /** Applies the system look and feel once, at start-up. */
    public static void applyLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // The cross-platform default is an acceptable fallback.
        }
        UIManager.put("Label.font", BODY);
        UIManager.put("Button.font", BODY);
        UIManager.put("TextField.font", BODY);
        UIManager.put("ComboBox.font", BODY);
        UIManager.put("Table.font", BODY);
        UIManager.put("TableHeader.font", H2);
    }

    public static JPanel header(String title, String subtitle) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(INK);
        p.setBorder(BorderFactory.createEmptyBorder(14, 18, 14, 18));

        JLabel t = new JLabel(title);
        t.setFont(H1);
        t.setForeground(Color.WHITE);

        JLabel s = new JLabel(subtitle);
        s.setFont(BODY);
        s.setForeground(new Color(0xB4, 0xC8, 0xC4));

        JPanel text = new JPanel(new GridLayout(2, 1, 0, 2));
        text.setOpaque(false);
        text.add(t);
        text.add(s);

        p.add(text, BorderLayout.WEST);
        return p;
    }

    public static JButton primary(String text) {
        JButton b = new JButton(text);
        b.setBackground(ACCENT);
        b.setForeground(Color.WHITE);
        b.setFocusPainted(false);
        b.setOpaque(true);
        b.setBorderPainted(false);
        b.setPreferredSize(new Dimension(150, 34));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    public static JButton secondary(String text) {
        JButton b = new JButton(text);
        b.setFocusPainted(false);
        b.setPreferredSize(new Dimension(150, 34));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    /** A form row for GridBagLayout: label on the left, field on the right. */
    public static void addRow(JPanel form, GridBagConstraints c, int row,
                              String label, JComponent field) {
        c.gridx = 0;
        c.gridy = row;
        c.weightx = 0;
        c.anchor = GridBagConstraints.LINE_END;
        c.fill = GridBagConstraints.NONE;
        c.insets = new Insets(6, 8, 6, 10);
        JLabel l = new JLabel(label);
        l.setFont(H2);
        form.add(l, c);

        c.gridx = 1;
        c.weightx = 1;
        c.anchor = GridBagConstraints.LINE_START;
        c.fill = GridBagConstraints.HORIZONTAL;
        form.add(field, c);
    }

    public static JTable readOnlyTable(DefaultTableModel model) {
        JTable table = new JTable(model) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        table.setRowHeight(24);
        table.setGridColor(LINE);
        table.setSelectionBackground(new Color(0xE0, 0xF0, 0xED));
        table.setSelectionForeground(INK);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);

        JTableHeader h = table.getTableHeader();
        h.setBackground(SURFACE);
        h.setForeground(INK);

        DefaultTableCellRenderer right = new DefaultTableCellRenderer();
        right.setHorizontalAlignment(SwingConstants.RIGHT);
        return table;
    }

    public static DefaultTableModel model(String... columns) {
        return new DefaultTableModel(columns, 0);
    }

    // -----------------------------------------------------------------
    //  messages - specific, and never a stack trace
    // -----------------------------------------------------------------

    public static void info(Component parent, String message) {
        JOptionPane.showMessageDialog(parent, message, "Sunrise Dental Clinic",
                JOptionPane.INFORMATION_MESSAGE);
    }

    public static void warn(Component parent, String message) {
        JOptionPane.showMessageDialog(parent, message, "Please check",
                JOptionPane.WARNING_MESSAGE);
    }

    public static void error(Component parent, String message) {
        JOptionPane.showMessageDialog(parent, message, "Could not continue",
                JOptionPane.ERROR_MESSAGE);
    }

    /**
     * Shows the message the service produced. On a session failure the user is
     * told to sign in again rather than being left with a dead window.
     */
    public static void showApiError(Component parent, ApiException ex) {
        if (ex.isSessionProblem()) {
            JOptionPane.showMessageDialog(parent,
                    ex.getMessage() + "\n\nPlease close this window and sign in again.",
                    "Session ended", JOptionPane.WARNING_MESSAGE);
            return;
        }
        error(parent, ex.getMessage());
    }

    /** Runs a slow call off the event thread so the window never freezes. */
    public static void background(JComponent busyOwner, Runnable work, Runnable done,
                                 Component parent) {
        busyOwner.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SwingWorker<Void, Void>() {
            private ApiException failure;

            @Override
            protected Void doInBackground() {
                try {
                    work.run();
                } catch (ApiException ex) {
                    failure = ex;
                }
                return null;
            }

            @Override
            protected void done() {
                busyOwner.setCursor(Cursor.getDefaultCursor());
                if (failure != null) {
                    showApiError(parent, failure);
                } else if (done != null) {
                    done.run();
                }
            }
        }.execute();
    }
}
