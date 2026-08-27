package lk.sunrise.client.ui;

import javax.swing.*;
import java.awt.*;

/**
 * UC-04 View Help. FR-25.
 *
 * The brief lists a help screen as a functional requirement, so this is
 * written as genuine instructions for clinic staff who have never used the
 * system - not as placeholder text.
 */
public class HelpFrame extends JFrame {

    public HelpFrame() {
        super("Help");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());

        add(Ui.header("Help", "Step-by-step instructions for clinic staff"), BorderLayout.NORTH);

        JEditorPane pane = new JEditorPane("text/html", CONTENT);
        pane.setEditable(false);
        pane.setBackground(Color.WHITE);
        pane.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));
        pane.setCaretPosition(0);

        add(new JScrollPane(pane), BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 10));
        actions.setBackground(Ui.SURFACE);
        actions.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Ui.LINE));
        JButton close = Ui.secondary("Close");
        close.addActionListener(e -> dispose());
        actions.add(close);
        add(actions, BorderLayout.SOUTH);

        pack();
        setMinimumSize(new Dimension(700, 620));
        setLocationRelativeTo(null);
    }

    private static final String CONTENT = """
        <html><body style="font-family:Segoe UI; font-size:12px; color:#0E2B2E;">

        <h2 style="color:#0C7B72;">Booking an appointment</h2>
        <ol>
          <li>Open <b>Book appointment</b> from the main menu.</li>
          <li>Type the patient's name, phone number or patient number in the search box
              and press <b>Search</b>.</li>
          <li>If they appear in the list, click their row. If they do not, press
              <b>New patient</b> and fill in the form - the system will allocate a
              patient number such as PAT-2026-000006.</li>
          <li>Choose the dentist and the treatment. The treatment sets how long the
              appointment lasts, so you do not enter an end time.</li>
          <li>Enter the date as YYYY-MM-DD and the time as HH:MM, then press
              <b>Book appointment</b>.</li>
          <li>Write down the appointment number shown in the confirmation, or tell it
              to the patient.</li>
        </ol>

        <h2 style="color:#0C7B72;">If the slot is already taken</h2>
        <p>The system will not allow two appointments to overlap for the same dentist.
        Instead of simply refusing, it offers the next free times. Pick one from the
        list and the booking continues, or press Cancel and try a different day.</p>
        <p>A ten-minute gap is always left between a dentist's appointments, so a time
        that looks free immediately after another appointment may still be refused.</p>

        <h2 style="color:#0C7B72;">Finding an appointment</h2>
        <ol>
          <li>Open <b>Search appointment</b>.</li>
          <li>Type the appointment number and press <b>Find</b> to see the full detail.</li>
          <li>Or enter a date and press <b>Show day</b> to list everything booked that day,
              then click a row.</li>
        </ol>

        <h2 style="color:#0C7B72;">After the visit</h2>
        <p>The dentist marks the appointment <b>Completed</b> (or <b>No-show</b> if the
        patient did not arrive). A bill can only be produced once an appointment is
        completed.</p>

        <h2 style="color:#0C7B72;">Producing a bill</h2>
        <ol>
          <li>Open <b>Billing and receipts</b>.</li>
          <li>Enter the appointment number and press <b>Generate bill</b>.</li>
          <li>The total is the dentist's consultation fee plus the treatments, less any
              discount the patient qualifies for. Discounts apply to treatments only.</li>
          <li>Press <b>Print receipt</b> to send it to the printer.</li>
        </ol>
        <p>Each appointment can be billed once. If you try again, the system will say so
        rather than creating a second bill.</p>

        <h2 style="color:#0C7B72;">Signing out</h2>
        <p>Always use <b>Sign out</b> rather than closing the window from the taskbar, so
        that your session is ended properly. Sessions also expire automatically after
        20 minutes of inactivity; if that happens, sign in again.</p>

        <h2 style="color:#0C7B72;">If something goes wrong</h2>
        <ul>
          <li><b>"Cannot reach the clinic service"</b> - the server application is not
              running. Ask the Administrator to start it.</li>
          <li><b>"Your session has expired"</b> - close the window and sign in again.</li>
          <li><b>"Your role does not permit this action"</b> - the task belongs to a
              different role. A receptionist cannot mark visits completed; a dentist
              cannot produce bills.</li>
          <li>Repeated failed sign-ins lock the account after five attempts. Only the
              Administrator can unlock it.</li>
        </ul>

        </body></html>
        """;
}
