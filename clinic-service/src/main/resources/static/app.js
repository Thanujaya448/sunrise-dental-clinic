/* =====================================================================
   Sunrise Dental Clinic - TIER 1 (browser)
   CIS6003 Advanced Programming - WRIT1

   The browser is the presentation tier. It holds no business rules: every
   figure it displays and every decision it reports was produced by the REST
   service in tier 2. Its jobs are to collect input, validate it cheaply
   before troubling the server, and render what comes back.
   ===================================================================== */

'use strict';

const API = '/api';

/* =====================================================================
   SESSION (FR-04)

   The token is kept in sessionStorage rather than a cookie. A cookie would
   be sent automatically on every request, which invites CSRF and would need
   a synchroniser token to defend. sessionStorage is read only by this
   script, so the token is attached deliberately, and the browser clears it
   when the tab closes. The trade-off is that sessionStorage is readable by
   any script on the page, so it depends on there being no XSS - which is
   why every value rendered below goes through text(), never innerHTML.
   ===================================================================== */
const Session = {
  save(dto) {
    sessionStorage.setItem('clinic.session', JSON.stringify(dto));
  },
  load() {
    try { return JSON.parse(sessionStorage.getItem('clinic.session')); }
    catch { return null; }
  },
  clear() {
    sessionStorage.removeItem('clinic.session');
  },
  token() {
    const s = Session.load();
    return s ? s.token : null;
  },
  role() {
    const s = Session.load();
    return s ? s.role : null;
  },
  isLive() {
    const s = Session.load();
    return !!s && new Date(s.expiresAt) > new Date();
  },
  can(...roles) {
    return roles.includes(Session.role());
  }
};

/* =====================================================================
   HTTP - the only place that knows the service exists
   ===================================================================== */
class ApiError extends Error {
  constructor(status, message, suggestedSlots) {
    super(message);
    this.status = status;
    this.suggestedSlots = suggestedSlots || [];
  }
}

async function request(method, path, body) {
  const headers = { 'Accept': 'application/json' };
  const token = Session.token();
  if (token) headers['Authorization'] = token;
  if (body !== undefined) headers['Content-Type'] = 'application/json';

  let response;
  try {
    response = await fetch(API + path, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body)
    });
  } catch {
    throw new ApiError(0, 'Cannot reach the clinic service. Check that it is running.');
  }

  const raw = await response.text();
  const data = raw ? JSON.parse(raw) : null;

  if (response.ok) return data;

  if (response.status === 401) {
    Session.clear();
    showLogin('Your session has ended. Please sign in again.');
  }
  throw new ApiError(
    response.status,
    (data && data.message) || `The service returned HTTP ${response.status}.`,
    data && data.suggestedSlots
  );
}

const api = {
  login:        (username, password) => request('POST', '/auth/login', { username, password }),
  logout:       ()                   => request('POST', '/auth/logout', {}),
  dentists:     ()                   => request('GET',  '/dentists'),
  treatments:   ()                   => request('GET',  '/treatments'),
  patients:     (q)                  => request('GET',  `/patients?q=${encodeURIComponent(q)}`),
  addPatient:   (p)                  => request('POST', '/patients', p),
  book:         (b)                  => request('POST', '/appointments', b),
  appointment:  (no)                 => request('GET',  `/appointments/${encodeURIComponent(no)}`),
  onDay:        (d)                  => request('GET',  `/appointments?date=${d}`),
  cancel:       (no, reason)         => request('POST', `/appointments/${encodeURIComponent(no)}/cancel`, { reason }),
  complete:     (no)                 => request('POST', `/appointments/${encodeURIComponent(no)}/complete`, {}),
  noShow:       (no)                 => request('POST', `/appointments/${encodeURIComponent(no)}/no-show`, {}),
  generateBill: (appointmentNo)      => request('POST', '/bills', { appointmentNo }),
  bill:         (no)                 => request('GET',  `/bills/${encodeURIComponent(no)}`),
  reports:      ()                   => request('GET',  '/reports'),
  report:       (type)               => request('GET',  `/reports/${type}`)
};

/* =====================================================================
   Small DOM helpers
   ===================================================================== */
const $  = (sel) => document.querySelector(sel);
const $$ = (sel) => Array.from(document.querySelectorAll(sel));

/** Creates an element and sets its text content - never its HTML. */
function el(tag, className, textContent) {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (textContent !== undefined && textContent !== null) node.textContent = String(textContent);
  return node;
}

function text(value, fallback = '—') {
  return (value === null || value === undefined || value === '') ? fallback : String(value);
}

function money(value) {
  const n = Number(value);
  return Number.isFinite(n)
    ? n.toLocaleString('en-LK', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
    : text(value);
}

let toastTimer;
function toast(message, kind = 'ok') {
  const t = $('#toast');
  t.textContent = message;
  t.className = `toast ${kind}`;
  t.hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { t.hidden = true; }, kind === 'ok' ? 3500 : 6000);
}

/** Every failed call lands here, so the service's own wording is what the user sees. */
function handle(err) {
  if (err instanceof ApiError) {
    toast(err.message, err.status === 0 || err.status >= 500 ? 'error' : 'warn');
  } else {
    console.error(err);
    toast('Something went wrong in the browser. Please reload the page.', 'error');
  }
}

async function busy(button, work) {
  const label = button ? button.textContent : null;
  if (button) { button.disabled = true; button.textContent = 'Working…'; }
  try {
    return await work();
  } finally {
    if (button) { button.disabled = false; button.textContent = label; }
  }
}

/* =====================================================================
   Navigation - FR-05, the tabs a role may use
   ===================================================================== */
const TABS = [
  { id: 'book',    label: 'Book',           icon: 'i-book',    roles: ['RECEPTIONIST', 'ADMINISTRATOR'] },
  { id: 'search',  label: 'Appointments',   icon: 'i-search',  roles: ['RECEPTIONIST', 'DENTIST', 'ADMINISTRATOR'] },
  { id: 'billing', label: 'Billing',        icon: 'i-billing', roles: ['RECEPTIONIST', 'ADMINISTRATOR'] },
  { id: 'reports', label: 'Reports',        icon: 'i-reports', roles: ['ADMINISTRATOR'] },
  { id: 'help',    label: 'Help',           icon: 'i-help',    roles: ['RECEPTIONIST', 'DENTIST', 'ADMINISTRATOR'] }
];

/** Builds an <svg><use href="#id"/></svg> without touching innerHTML. */
function icon(id) {
  const NS = 'http://www.w3.org/2000/svg';
  const svg = document.createElementNS(NS, 'svg');
  svg.setAttribute('class', 'ico');
  svg.setAttribute('aria-hidden', 'true');
  const use = document.createElementNS(NS, 'use');
  use.setAttribute('href', '#' + id);
  svg.appendChild(use);
  return svg;
}

function buildTabs() {
  const nav = $('#nav');
  nav.replaceChildren();
  TABS.filter(t => Session.can(...t.roles)).forEach(t => {
    const b = el('button', 'nav-item');
    b.type = 'button';
    b.dataset.panel = t.id;
    b.appendChild(icon(t.icon));
    b.appendChild(el('span', null, t.label));
    b.addEventListener('click', () => showPanel(t.id));
    nav.appendChild(b);
  });
}

function showPanel(id) {
  TABS.forEach(t => {
    const panel = $(`#panel-${t.id}`);
    if (panel) panel.hidden = t.id !== id;
  });
  $$('.nav-item').forEach(b => b.classList.toggle('active', b.dataset.panel === id));
  if (id === 'reports' && !$('#report-list').children.length) loadReports();
}

/* =====================================================================
   Login / logout
   ===================================================================== */
function showLogin(message) {
  $('#app-view').hidden = true;
  $('#login-view').hidden = false;
  $('#login-error').textContent = message || '';
  $('#password').value = '';
  stopSessionClock();
}

async function enterApp() {
  const s = Session.load();
  $('#login-view').hidden = true;
  $('#app-view').hidden = false;
  $('#who-name').textContent = s.fullName;
  $('#who-role').textContent = s.role.charAt(0) + s.role.slice(1).toLowerCase();
  $('#who-initials').textContent = s.fullName.split(/\s+/).map(w => w[0]).slice(0, 2).join('').toUpperCase();

  buildTabs();
  showPanel(TABS.find(t => Session.can(...t.roles)).id);
  startSessionClock();

  $('#book-date').value = new Date(Date.now() + 86400000).toISOString().slice(0, 10);
  $('#appt-day').value  = new Date().toISOString().slice(0, 10);

  if (Session.can('RECEPTIONIST', 'ADMINISTRATOR')) {
    await loadReferenceData();
  }
}

$('#login-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  $('#login-error').textContent = '';
  const username = $('#username').value.trim();
  const password = $('#password').value;
  if (!username || !password) {
    $('#login-error').textContent = 'Enter both a username and a password.';
    return;
  }
  try {
    await busy($('#login-btn'), async () => {
      const dto = await api.login(username, password);
      Session.save(dto);
      await enterApp();
    });
  } catch (err) {
    $('#login-error').textContent = err.message;
  }
});

$('#logout-btn').addEventListener('click', async () => {
  try { await api.logout(); } catch { /* signing out locally must still work */ }
  Session.clear();
  showLogin('You have been signed out.');
});

/* ---- session countdown (FR-04) ---- */
let clockTimer;
function startSessionClock() {
  stopSessionClock();
  const tick = () => {
    const s = Session.load();
    if (!s) return;
    const left = Math.max(0, Math.floor((new Date(s.expiresAt) - new Date()) / 1000));
    $('#session-pill').textContent =
      `${String(Math.floor(left / 60)).padStart(2, '0')}:${String(left % 60).padStart(2, '0')}`;
    $('#session-box').classList.toggle('expiring', left < 300);
    if (left === 0) {
      Session.clear();
      showLogin('Your session expired after 20 minutes of inactivity.');
    }
  };
  tick();
  clockTimer = setInterval(tick, 1000);
}
function stopSessionClock() { clearInterval(clockTimer); }

/* =====================================================================
   BOOK  (UC-05, UC-06, UC-07)
   ===================================================================== */
let selectedPatientNo = null;
let treatmentIndex = {};

async function loadReferenceData() {
  try {
    const [dentists, treatments] = await Promise.all([api.dentists(), api.treatments()]);

    const d = $('#book-dentist');
    d.replaceChildren();
    dentists.forEach(x => {
      const o = el('option', null, `${x.fullName} — ${x.specialisation}`);
      o.value = x.dentistId;
      d.appendChild(o);
    });

    const t = $('#book-treatment');
    t.replaceChildren();
    treatments.forEach(x => {
      treatmentIndex[x.code] = x;
      const o = el('option', null, `${x.name} — LKR ${money(x.price)}`);
      o.value = x.code;
      t.appendChild(o);
    });
    updateTreatmentHint();
  } catch (err) { handle(err); }
}

function updateTreatmentHint() {
  const t = treatmentIndex[$('#book-treatment').value];
  $('#treatment-hint').textContent = t
    ? `Takes ${t.durationMinutes} minutes, so the end time is calculated for you.`
    : '';
}
$('#book-treatment').addEventListener('change', updateTreatmentHint);

async function searchPatients() {
  const term = $('#patient-search').value.trim();
  try {
    const rows = await busy($('#patient-search-btn'), () => api.patients(term));
    const tbody = $('#patient-table tbody');
    tbody.replaceChildren();

    if (!rows.length) {
      const tr = el('tr', 'empty');
      tr.appendChild(el('td', null, `No patient matched "${term}". Use New patient to register them.`))
        .colSpan = 4;
      tbody.appendChild(tr);
      return;
    }

    rows.forEach(p => {
      const tr = el('tr');
      tr.appendChild(el('td', null, p.patientNo));
      tr.appendChild(el('td', null, p.fullName));
      tr.appendChild(el('td', null, p.contactNumber));
      tr.appendChild(el('td', 'num', p.completedVisits));
      tr.addEventListener('click', () => {
        $$('#patient-table tbody tr').forEach(r => r.classList.remove('selected-row'));
        tr.classList.add('selected-row');
        selectedPatientNo = p.patientNo;
        const box = $('#selected-patient');
        box.textContent = `${p.fullName} (${p.patientNo})`;
        box.classList.add('chosen');
      });
      tbody.appendChild(tr);
    });
  } catch (err) { handle(err); }
}

$('#patient-search-btn').addEventListener('click', searchPatients);
$('#patient-search').addEventListener('keydown', e => { if (e.key === 'Enter') searchPatients(); });

/* ---- register a new patient (FR-06) ---- */
$('#patient-new-btn').addEventListener('click', () => {
  $('#np-error').textContent = '';
  $('#patient-form').reset();
  $('#patient-dialog').showModal();
});

$('#patient-dialog').addEventListener('close', async () => {
  if ($('#patient-dialog').returnValue !== 'save') return;

  const p = {
    fullName:      $('#np-name').value.trim(),
    address:       $('#np-address').value.trim(),
    contactNumber: $('#np-phone').value.trim(),
    email:         $('#np-email').value.trim() || null,
    dateOfBirth:   $('#np-dob').value,
    staffFamily:   $('#np-staff').checked
  };

  // Cheap client-side checks before troubling the service; the server
  // validates again and is the authority (NFR-04).
  if (!p.fullName || !p.address || !p.contactNumber || !p.dateOfBirth) {
    toast('Name, address, contact number and date of birth are all required.', 'warn');
    return;
  }
  if (!/^0\d{9}$/.test(p.contactNumber)) {
    toast('Contact number must be 10 digits starting with 0, for example 0712345678.', 'warn');
    return;
  }
  if (new Date(p.dateOfBirth) > new Date()) {
    toast('Date of birth cannot be in the future.', 'warn');
    return;
  }

  try {
    const created = await api.addPatient(p);
    toast(`Patient registered as ${created.patientNo}.`);
    $('#patient-search').value = created.patientNo;
    await searchPatients();
  } catch (err) { handle(err); }
});

/* ---- book (FR-09 to FR-12, FR-20) ---- */
$('#book-btn').addEventListener('click', () => attemptBooking($('#book-time').value));

async function attemptBooking(startTime) {
  if (!selectedPatientNo) {
    toast('Select a patient from the list on the left first.', 'warn');
    return;
  }
  const date = $('#book-date').value;
  if (!date || !startTime) {
    toast('Choose a date and a start time.', 'warn');
    return;
  }
  if (date < new Date().toISOString().slice(0, 10)) {
    toast('Appointments cannot be booked in the past.', 'warn');
    return;
  }

  const payload = {
    patientNo:       selectedPatientNo,
    dentistId:       Number($('#book-dentist').value),
    treatmentCode:   $('#book-treatment').value,
    appointmentDate: date,
    startTime:       startTime.length === 5 ? `${startTime}:00` : startTime,
    notes:           $('#book-notes').value.trim() || null
  };

  try {
    const saved = await busy($('#book-btn'), () => api.book(payload));
    toast(`Booked — ${saved.appointmentNo}, ${saved.dentistName}, `
        + `${saved.appointmentDate} ${saved.startTime}–${saved.endTime}`);
    $('#book-notes').value = '';
  } catch (err) {
    if (err.status === 409 && err.suggestedSlots.length) {
      offerAlternatives(err);
    } else {
      handle(err);
    }
  }
}

/** FR-11 / ASM-09 - a refusal that offers a way forward. */
function offerAlternatives(err) {
  $('#slots-message').textContent = err.message;
  const box = $('#slot-options');
  box.replaceChildren();
  err.suggestedSlots.forEach(slot => {
    const b = el('button', null, slot.slice(0, 5));
    b.type = 'button';
    b.addEventListener('click', () => {
      $('#slots-dialog').close();
      $('#book-time').value = slot.slice(0, 5);
      attemptBooking(slot);
    });
    box.appendChild(b);
  });
  $('#slots-dialog').showModal();
}
$('#slots-cancel').addEventListener('click', () => $('#slots-dialog').close());

/* =====================================================================
   SEARCH  (UC-10 to UC-13)
   ===================================================================== */
$('#appt-find-btn').addEventListener('click', findAppointment);
$('#appt-no').addEventListener('keydown', e => { if (e.key === 'Enter') findAppointment(); });
$('#appt-day-btn').addEventListener('click', loadDay);

async function findAppointment() {
  const no = $('#appt-no').value.trim();
  if (!no) { toast('Enter an appointment number, for example APT-2026-000005.', 'warn'); return; }
  try {
    const a = await busy($('#appt-find-btn'), () => api.appointment(no));
    renderDetail(a);
  } catch (err) { handle(err); }
}

async function loadDay() {
  const day = $('#appt-day').value;
  if (!day) { toast('Choose a date.', 'warn'); return; }
  try {
    const rows = await busy($('#appt-day-btn'), () => api.onDay(day));
    const tbody = $('#appt-table tbody');
    tbody.replaceChildren();

    if (!rows.length) {
      const tr = el('tr', 'empty');
      const td = el('td', null, `No appointments on ${day}.`);
      td.colSpan = 5;
      tr.appendChild(td);
      tbody.appendChild(tr);
      $('#appt-detail').hidden = true;
      $('#day-stats').hidden = true;
      return;
    }

    const count = (st) => rows.filter(a => a.status === st).length;
    $('#stat-total').textContent  = rows.length;
    $('#stat-booked').textContent = count('BOOKED');
    $('#stat-done').textContent   = count('COMPLETED');
    $('#stat-miss').textContent   = count('CANCELLED') + count('NO_SHOW');
    $('#day-stats').hidden = false;

    rows.forEach(a => {
      const tr = el('tr');
      tr.appendChild(el('td', null, a.appointmentNo));
      tr.appendChild(el('td', null, `${a.startTime.slice(0,5)}–${a.endTime.slice(0,5)}`));
      tr.appendChild(el('td', null, a.patientName));
      tr.appendChild(el('td', null, a.dentistName));
      const status = el('td');
      status.appendChild(el('span', `pill ${a.status}`, a.status.replace('_', ' ')));
      tr.appendChild(status);
      tr.addEventListener('click', () => {
        $('#appt-no').value = a.appointmentNo;
        renderDetail(a);
      });
      tbody.appendChild(tr);
    });
  } catch (err) { handle(err); }
}

function renderDetail(a) {
  const box = $('#appt-detail');
  box.replaceChildren();

  const dl = el('dl');
  const add = (label, value) => {
    dl.appendChild(el('dt', null, label));
    dl.appendChild(el('dd', null, value));
  };
  add('Appointment',   a.appointmentNo);
  add('Status',        a.status.replace('_', ' '));
  add('Patient',       `${a.patientName} (${a.patientNo})`);
  add('Contact',       text(a.contactNumber));
  add('Dentist',       `${a.dentistName} — ${a.specialisation}`);
  add('Date and time', `${a.appointmentDate}, ${a.startTime.slice(0,5)} to ${a.endTime.slice(0,5)}`);
  add('Treatments',    text(a.treatments));
  add('Treatment cost',`LKR ${money(a.treatmentSubtotal)}`);
  add('Consultation',  `LKR ${money(a.consultationFee)}`);
  add('Notes',         text(a.notes));
  box.appendChild(dl);

  const actions = el('div', 'detail-actions');
  const no = a.appointmentNo;

  if (a.status === 'BOOKED' && Session.can('RECEPTIONIST', 'ADMINISTRATOR')) {
    const cancel = el('button', 'btn', 'Cancel appointment');
    cancel.addEventListener('click', async () => {
      const reason = prompt(`Why is ${no} being cancelled?`);
      if (reason === null) return;
      if (!reason.trim()) { toast('A reason is required so the cancellation can be audited.', 'warn'); return; }
      try {
        await api.cancel(no, reason.trim());
        toast(`Appointment ${no} cancelled.`);
        await refreshAfterChange(no);
      } catch (err) { handle(err); }
    });
    actions.appendChild(cancel);
  }

  if (a.status === 'BOOKED' && Session.can('DENTIST', 'ADMINISTRATOR')) {
    const noShow = el('button', 'btn', 'Mark no-show');
    noShow.addEventListener('click', () => changeStatus(no, api.noShow));
    actions.appendChild(noShow);

    const done = el('button', 'btn btn-primary', 'Mark completed');
    done.addEventListener('click', () => changeStatus(no, api.complete));
    actions.appendChild(done);
  }

  if (a.status === 'COMPLETED' && Session.can('RECEPTIONIST', 'ADMINISTRATOR')) {
    const bill = el('button', 'btn btn-primary', 'Generate bill');
    bill.addEventListener('click', () => {
      $('#bill-appt-no').value = no;
      showPanel('billing');
      generateBill();
    });
    actions.appendChild(bill);
  }

  box.appendChild(actions);
  box.hidden = false;
}

async function changeStatus(no, call) {
  try {
    await call(no);
    toast(`Appointment ${no} updated.`);
    await refreshAfterChange(no);
  } catch (err) { handle(err); }
}

async function refreshAfterChange(no) {
  try { renderDetail(await api.appointment(no)); } catch { /* detail is optional */ }
  if ($('#appt-day').value) await loadDay();
}

/* =====================================================================
   BILLING  (UC-15, UC-18)
   ===================================================================== */
$('#bill-generate-btn').addEventListener('click', generateBill);
$('#bill-appt-no').addEventListener('keydown', e => { if (e.key === 'Enter') generateBill(); });
$('#bill-find-btn').addEventListener('click', findBill);
$('#print-btn').addEventListener('click', () => window.print());

async function generateBill() {
  const no = $('#bill-appt-no').value.trim();
  if (!no) { toast('Enter the appointment number.', 'warn'); return; }
  try {
    const bill = await busy($('#bill-generate-btn'), () => api.generateBill(no));
    renderReceipt(bill);
    toast(`Bill ${bill.billNo} created.`);
  } catch (err) { handle(err); }
}

async function findBill() {
  const no = $('#bill-no').value.trim();
  if (!no) { toast('Enter a bill number.', 'warn'); return; }
  try {
    renderReceipt(await busy($('#bill-find-btn'), () => api.bill(no)));
  } catch (err) { handle(err); }
}

/**
 * Formats the receipt. Note that no arithmetic happens here - every figure
 * was calculated by the service and the stored procedure. The browser only
 * lays them out.
 */
function renderReceipt(b) {
  const W = 54;
  const line = (ch) => ch.repeat(W);
  const pad  = (left, right) => left.padEnd(W - String(right).length) + right;

  const rows = b.lines.map(l =>
    `${l.description.slice(0, 30).padEnd(32)}${String(l.quantity).padStart(4)}${money(l.lineTotal).padStart(18)}`
  ).join('\n');

  $('#receipt').textContent = [
    '           SUNRISE DENTAL CLINIC',
    '      14 Temple Road, Nugegoda, Sri Lanka',
    line('='),
    pad('Bill number', b.billNo),
    pad('Appointment', b.appointmentNo),
    pad('Patient', b.patientName),
    pad('Issued', String(b.issuedOn).replace('T', ' ').slice(0, 16)),
    line('='),
    'Description'.padEnd(32) + 'Qty'.padStart(4) + 'Amount'.padStart(18),
    line('-'),
    rows,
    line('-'),
    pad('Consultation fee', money(b.consultationFee)),
    pad('Treatment subtotal', money(b.treatmentSubtotal)),
    pad(b.discountLabel, '-' + money(b.discountAmount)),
    line('='),
    pad('TOTAL PAYABLE (LKR)', money(b.totalPayable)),
    pad('Payment status', b.paymentStatus),
    line('='),
    '',
    '    Thank you for visiting Sunrise Dental Clinic.'
  ].join('\n');

  $('#receipt-area').hidden = false;
}

/* =====================================================================
   REPORTS  (UC-21) - Administrator only
   ===================================================================== */
async function loadReports() {
  try {
    const list = await api.reports();
    const ul = $('#report-list');
    ul.replaceChildren();
    list.forEach((r, i) => {
      const li = el('li');
      const b = el('button', null, r.title);
      b.type = 'button';
      b.addEventListener('click', () => {
        $$('#report-list button').forEach(x => x.classList.remove('active'));
        b.classList.add('active');
        $('#report-question').textContent = r.question;
        runReport(r.id);
      });
      li.appendChild(b);
      ul.appendChild(li);
      if (i === 0) b.click();
    });
  } catch (err) { handle(err); }
}

async function runReport(id) {
  try {
    const rows = await api.report(id);
    const thead = $('#report-table thead');
    const tbody = $('#report-table tbody');
    thead.replaceChildren();
    tbody.replaceChildren();

    if (!rows.length) {
      const tr = el('tr', 'empty');
      const td = el('td', null, 'No data yet. Complete some appointments or generate bills, then run it again.');
      tr.appendChild(td);
      tbody.appendChild(tr);
      return;
    }

    const columns = Object.keys(rows[0]);
    // A column is numeric if its first value is - so the header aligns with
    // the cells beneath it.
    const numeric = {};
    columns.forEach(c => { numeric[c] = typeof rows[0][c] === 'number'; });

    const headRow = el('tr');
    columns.forEach(c => {
      const label = c.replace(/_/g, ' ');
      headRow.appendChild(el('th', numeric[c] ? 'num' : null,
        label.charAt(0).toUpperCase() + label.slice(1)));
    });
    thead.appendChild(headRow);

    rows.forEach(r => {
      const tr = el('tr', 'static-row');   // data row, not selectable
      columns.forEach(c => {
        const v = r[c];
        tr.appendChild(el('td', numeric[c] ? 'num' : null,
          typeof v === 'number'
            ? v.toLocaleString('en-LK', { maximumFractionDigits: 2 })
            : text(v)));
      });
      tbody.appendChild(tr);
    });
  } catch (err) { handle(err); }
}

/* =====================================================================
   Start-up
   ===================================================================== */
if (Session.isLive()) {
  enterApp().catch(handle);
} else {
  Session.clear();
  showLogin();
}
