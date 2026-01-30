import { decrypt } from './crypto.js'

const elements = {
    overlay: document.getElementById('auth-overlay'),
    form: document.getElementById('auth-form'),
    passInput: document.getElementById('password-input'),
    errorMsg: document.getElementById('error-msg'),
    loading: document.getElementById('loading'),
    main: document.getElementById('main-content'),
    calendarGrid: document.getElementById('calendar-grid'),
    monthDisplay: document.getElementById('current-month-display'),
    prevBtn: document.getElementById('prev-month-btn'),
    nextBtn: document.getElementById('next-month-btn'),
    tableBody: document.getElementById('events-table-body'),
    refreshBtn: document.getElementById('refresh-btn'),
    logoutBtn: document.getElementById('logout-btn'),
    // NEW ELEMENTS
    weeklyList: document.getElementById('weekly-events-list'),
    tabUpcoming: document.getElementById('tab-upcoming'),
    tabPrevious: document.getElementById('tab-previous'),
    yearFilter: document.getElementById('year-filter'),
    bulkBar: document.getElementById('bulk-actions-bar'),
    selectionCount: document.getElementById('selection-count'),
    hideBtn: document.getElementById('hide-selected-btn'),
    unhideBtn: document.getElementById('unhide-selected-btn'),
    clearBtn: document.getElementById('clear-selection-btn'),
    selectAllCheck: document.getElementById('select-all-checkbox'),
    showHiddenToggle: document.getElementById('show-hidden-toggle'),
    rememberMe: document.getElementById('remember-me'),
    legendFilters: document.querySelectorAll('.legend-item')
};

let eventsData = [];
let currentDate = new Date();
currentDate.setDate(1); // Ensure we start at the 1st to avoid overflowing months (e.g., Jan 29 -> Feb 29 -> March)
let currentTab = 'upcoming'; // 'upcoming' or 'previous'
let selectedYear = localStorage.getItem('school-events-year-filter') || 'All';
let hiddenEventIds = new Set(JSON.parse(localStorage.getItem('school-events-hidden-ids') || '[]'));
let showHidden = localStorage.getItem('school-events-show-hidden') === 'true';
let filterState = JSON.parse(localStorage.getItem('school-events-filters') || '{"normal":true,"recurring":true,"halfTerm":true,"bookBag":true}');
let selectedEventIds = new Set();

// Handle Login
elements.form.addEventListener('submit', async (e) => {
    e.preventDefault();
    const password = elements.passInput.value;

    elements.form.classList.add('hidden');
    elements.loading.classList.remove('hidden');
    elements.errorMsg.classList.add('hidden');

    try {
        let encryptedContent;
        try {
            console.log("Fetching events.json...");
            const response = await fetch('./events.json');
            if (!response.ok) {
                console.error("Fetch failed with status:", response.status);
                throw new Error(`Data file not found (Status: ${response.status}). If you are running on GitHub Pages, remember that events.json is not public!`);
            }
            encryptedContent = await response.text();
            console.log("Encrypted content received (length):", encryptedContent.length);
        } catch (err) {
            console.error("Detailed Fetch Error:", err);
            throw new Error(`Could not load events: ${err.message}`);
        }

        if (encryptedContent.startsWith("v1|")) {
            eventsData = await decrypt(encryptedContent, password);
        } else {
            eventsData = JSON.parse(encryptedContent);
        }

        console.log("Decrypted Events Data:", eventsData.length, "items");
        if (eventsData.length > 0) {
            console.log("Sample Event Fields:", Object.keys(eventsData[0]));
            console.log("Recurring Counts:", eventsData.filter(e => e.isRecurring || e.recurring).length);
        }

        // Save Auth if Remember Me is checked
        if (elements.rememberMe.checked) {
            const expiry = Date.now() + (7 * 24 * 60 * 60 * 1000); // 7 days
            localStorage.setItem('school-events-saved-auth', JSON.stringify({ password, expiry }));
        }

        // Initialize Filter UI
        if (elements.yearFilter) elements.yearFilter.value = selectedYear;
        elements.showHiddenToggle.checked = showHidden;
        updateLegendUI();

        renderUI();
        unlockUI();

    } catch (error) {
        console.error(error);
        elements.loading.classList.add('hidden');
        elements.form.classList.remove('hidden');
        elements.errorMsg.textContent = error.message;
        elements.errorMsg.classList.remove('hidden');
    }
});

function unlockUI() {
    elements.overlay.classList.add('hidden');
    elements.main.classList.remove('hidden');
}

function renderUI() {
    const filtered = getFilteredEvents();
    renderCalendar(filtered);
    renderWeeklyEvents(filtered);
    renderTable(filtered);
}

function getFilteredEvents() {
    const hiddenSet = hiddenEventIds;
    let filtered = eventsData;

    // If not showing hidden, filter them out entirely
    if (!showHidden) {
        filtered = filtered.filter(e => !hiddenSet.has(e.id));
    }

    // Category Filtering
    filtered = filtered.filter(e => {
        const title = (e.title || '').toLowerCase();
        const isRecur = e.isRecurring || e.recurring === true;

        if (title.includes('half term')) return filterState.halfTerm;
        if (title.includes('book bag')) return filterState.bookBag;
        if (isRecur) return filterState.recurring;
        return filterState.normal;
    });

    if (selectedYear === 'All') return filtered;

    return filtered.filter(event => {
        const text = `${event.title} ${event.notes || ''}`.toLowerCase();

        if (selectedYear === 'Reception') {
            return text.includes('reception');
        }

        const yearNum = selectedYear;
        // Match "Year X", "Yr X", "Yrs X", "Y X" where X is the year number
        const yearRegex = new RegExp(`(year|yr|yrs|y)\\s*.*${yearNum}`, 'i');

        return yearRegex.test(text);
    });
}

function renderWeeklyEvents(filteredData) {
    elements.weeklyList.innerHTML = '';

    // Define current week range (Now to Sat)
    const today = new Date();
    const dayOfWeek = today.getDay(); // 0 (Sun) to 6 (Sat)

    const endOfWeek = new Date(today);
    endOfWeek.setDate(today.getDate() + (6 - dayOfWeek));
    endOfWeek.setHours(23, 59, 59, 999);

    const hiddenSet = hiddenEventIds;
    const weekEvents = filteredData.filter(e => {
        const d = new Date(e.startDate);
        return d >= today && d <= endOfWeek;
    }).sort((a, b) => new Date(a.startDate) - new Date(b.startDate));

    if (weekEvents.length === 0) {
        elements.weeklyList.innerHTML = '<div class="empty-msg" style="font-size: 0.85rem; color: var(--color-text-secondary); text-align: center; margin-top: 1rem;">No upcoming events this week.</div>';
        return;
    }

    weekEvents.forEach(event => {
        const date = new Date(event.startDate);
        const dateStr = date.toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: 'numeric' });

        const card = document.createElement('div');
        card.className = 'event-card';
        if (hiddenSet.has(event.id)) card.classList.add('event-hidden');

        card.innerHTML = `
            <div class="card-date">${dateStr}</div>
            <h3>${event.title}</h3>
            <p>${event.notes || 'No description available.'}</p>
        `;
        card.onclick = () => {
            const row = document.getElementById(`event-row-${event.id}`);
            if (row) {
                row.scrollIntoView({ behavior: 'smooth', block: 'center' });
                row.classList.remove('blink-highlight');
                void row.offsetWidth;
                row.classList.add('blink-highlight');
            }
        };
        elements.weeklyList.appendChild(card);
    });
}

function renderCalendar(filteredData) {
    elements.calendarGrid.innerHTML = '';
    const year = currentDate.getFullYear();
    const month = currentDate.getMonth();

    elements.monthDisplay.textContent = new Intl.DateTimeFormat('en-US', { month: 'long', year: 'numeric' }).format(currentDate);

    // Weekday Headers
    const days = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
    days.forEach(day => {
        const div = document.createElement('div');
        div.className = 'weekday-header';
        div.textContent = day;
        elements.calendarGrid.appendChild(div);
    });

    const firstDay = new Date(year, month, 1).getDay();
    const daysInMonth = new Date(year, month + 1, 0).getDate();

    // Padding for first week
    for (let i = 0; i < firstDay; i++) {
        const div = document.createElement('div');
        div.className = 'calendar-day other-month';
        elements.calendarGrid.appendChild(div);
    }

    // Actual days
    for (let day = 1; day <= daysInMonth; day++) {
        const div = document.createElement('div');
        div.className = 'calendar-day';
        div.textContent = day;

        const dateStr = `${year}-${String(month + 1).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
        const dayDate = new Date(year, month, day);
        dayDate.setHours(0, 0, 0, 0);

        // Multi-day Match: Check if day falls between startDate and endDate
        const dayEvents = filteredData.filter(e => {
            const start = new Date(e.startDate);
            start.setHours(0, 0, 0, 0);

            if (e.endDate) {
                const end = new Date(e.endDate);
                end.setHours(23, 59, 59, 999);
                return dayDate >= start && dayDate <= end;
            }
            return dayDate.getTime() === start.getTime();
        });

        if (dayEvents.length > 0) {
            div.classList.add('has-events');

            const dotsContainer = document.createElement('div');
            dotsContainer.className = 'dots-container';

            dayEvents.forEach(e => {
                const dot = document.createElement('div');
                dot.className = 'event-dot';

                const lowerTitle = (e.title || '').toLowerCase();
                const isRecur = e.isRecurring || e.recurring === true;

                if (lowerTitle.includes('half term')) {
                    dot.classList.add('half-term');
                } else if (lowerTitle.includes('book bag')) {
                    dot.classList.add('book-bag');
                } else if (isRecur) {
                    dot.classList.add('recurring');
                }

                if (hiddenEventIds.has(e.id)) dot.style.opacity = '0.3';
                dotsContainer.appendChild(dot);
            });

            div.appendChild(dotsContainer);

            div.title = dayEvents.map(e => e.title).join('\n');

            // INTERACTIVITY: Click day to scroll to first event in table
            div.onclick = () => {
                const event = dayEvents[0];
                const eventDate = new Date(event.startDate);
                const today = new Date();
                today.setHours(0, 0, 0, 0);

                // Smart Tab Switching
                const targetTab = eventDate >= today ? 'upcoming' : 'previous';
                if (currentTab !== targetTab) {
                    currentTab = targetTab;
                    if (currentTab === 'upcoming') {
                        elements.tabUpcoming.classList.add('active');
                        elements.tabPrevious.classList.remove('active');
                    } else {
                        elements.tabPrevious.classList.add('active');
                        elements.tabUpcoming.classList.remove('active');
                    }
                    renderTable(filteredData);
                }

                const row = document.getElementById(`event-row-${event.id}`);
                if (row) {
                    row.scrollIntoView({ behavior: 'smooth', block: 'center' });
                    row.classList.remove('blink-highlight');
                    void row.offsetWidth; // trigger reflow
                    row.classList.add('blink-highlight');
                }
            };
        }

        if (isToday(year, month, day)) {
            div.classList.add('today');
        }

        elements.calendarGrid.appendChild(div);
    }
}

function renderTable(filteredData) {
    elements.tableBody.innerHTML = '';

    const today = new Date();
    today.setHours(0, 0, 0, 0);

    let tabFiltered;
    if (currentTab === 'upcoming') {
        tabFiltered = filteredData.filter(e => new Date(e.startDate) >= today);
    } else {
        tabFiltered = filteredData.filter(e => new Date(e.startDate) < today);
    }

    // Sort: Upcoming (ASC), Previous (DESC)
    const sortedEvents = [...tabFiltered].sort((a, b) => {
        const diff = new Date(a.startDate) - new Date(b.startDate);
        return currentTab === 'upcoming' ? diff : -diff;
    });

    if (sortedEvents.length === 0) {
        elements.tableBody.innerHTML = `<tr><td colspan="4" style="text-align: center; padding: 2rem; color: var(--color-text-secondary);">No ${currentTab} events found.</td></tr>`;
        return;
    }

    sortedEvents.forEach(event => {
        const date = new Date(event.startDate);
        const dateStr = date.toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: 'numeric' });

        let sourceSubj = event.sourceEmailSubject || 'N/A';
        if (sourceSubj.startsWith('Fwd: ')) sourceSubj = sourceSubj.substring(5);
        const sourceTime = event.sourceEmailReceivedAt
            ? new Date(event.sourceEmailReceivedAt).toLocaleString('en-US', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })
            : 'N/A';

        const tr = document.createElement('tr');
        tr.id = `event-row-${event.id}`;

        const isHidden = hiddenEventIds.has(event.id);
        if (isHidden) tr.classList.add('event-hidden');

        const isSelected = selectedEventIds.has(event.id);

        const isRecur = event.isRecurring || event.recurring === true;
        const icon = isRecur ? `
            <svg class="recurring-icon" viewBox="0 0 24 24" title="Recurring Event">
                <path d="M 20 12 A 8 8 0 1 1 12 4" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round"/>
                <polyline points="17,15 20,12 23,15" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>` : '';

        tr.innerHTML = `
            <td><input type="checkbox" class="event-checkbox" data-id="${event.id}" ${isSelected ? 'checked' : ''}></td>
            <td class="date-cell">${dateStr}</td>
            <td>${icon}<strong>${event.title}</strong></td>
            <td>
                <div style="font-size: 0.8rem; font-weight: 600;">${sourceSubj}</div>
                <div style="font-size: 0.7rem; color: var(--color-text-secondary);">${sourceTime}</div>
            </td>
            <td><span class="event-notes">${event.notes || ''}</span></td>
        `;

        const checkbox = tr.querySelector('.event-checkbox');
        checkbox.addEventListener('change', (e) => {
            if (e.target.checked) {
                selectedEventIds.add(event.id);
            } else {
                selectedEventIds.delete(event.id);
                elements.selectAllCheck.checked = false;
            }
            updateBulkBar();
        });

        elements.tableBody.appendChild(tr);
    });
}

function updateBulkBar() {
    const count = selectedEventIds.size;
    if (count > 0) {
        elements.bulkBar.classList.remove('hidden');
        elements.selectionCount.textContent = `${count} item${count > 1 ? 's' : ''} selected`;

        // Show/Hide specific buttons based on selection
        const selectedHiddenCount = [...selectedEventIds].filter(id => hiddenEventIds.has(id)).length;

        if (selectedHiddenCount > 0) {
            elements.unhideBtn.classList.remove('hidden');
        } else {
            elements.unhideBtn.classList.add('hidden');
        }

        if (selectedHiddenCount < count) {
            elements.hideBtn.classList.remove('hidden');
        } else {
            elements.hideBtn.classList.add('hidden');
        }

    } else {
        elements.bulkBar.classList.add('hidden');
        elements.selectAllCheck.checked = false;
    }
}

function isToday(y, m, d) {
    const today = new Date();
    return today.getFullYear() === y && today.getMonth() === m && today.getDate() === d;
}

// Navigation
elements.prevBtn.addEventListener('click', () => {
    currentDate.setDate(1);
    currentDate.setMonth(currentDate.getMonth() - 1);
    renderUI();
});

elements.nextBtn.addEventListener('click', () => {
    currentDate.setDate(1);
    currentDate.setMonth(currentDate.getMonth() + 1);
    renderUI();
});

elements.tabUpcoming.addEventListener('click', () => {
    currentTab = 'upcoming';
    elements.tabUpcoming.classList.add('active');
    elements.tabPrevious.classList.remove('active');
    renderUI();
});

elements.tabPrevious.addEventListener('click', () => {
    currentTab = 'previous';
    elements.tabPrevious.classList.add('active');
    elements.tabUpcoming.classList.remove('active');
    renderUI();
});

elements.refreshBtn.addEventListener('click', () => location.reload());
elements.logoutBtn.addEventListener('click', () => {
    localStorage.removeItem('school-events-saved-auth');
    location.reload();
});

if (elements.yearFilter) {
    elements.yearFilter.addEventListener('change', (e) => {
        selectedYear = e.target.value;
        localStorage.setItem('school-events-year-filter', selectedYear);
        renderUI();
    });
}

elements.showHiddenToggle.addEventListener('change', (e) => {
    showHidden = e.target.checked;
    localStorage.setItem('school-events-show-hidden', showHidden);
    renderUI();
});

// Legend Filters
elements.legendFilters.forEach(item => {
    item.addEventListener('click', () => {
        const category = item.dataset.category;
        const key = category === 'half-term' ? 'halfTerm' :
            category === 'book-bag' ? 'bookBag' : category;

        filterState[key] = !filterState[key];
        console.log(`Toggled ${key}:`, filterState[key]);
        localStorage.setItem('school-events-filters', JSON.stringify(filterState));
        updateLegendUI();
        renderUI();
    });
});

function updateLegendUI() {
    elements.legendFilters.forEach(item => {
        const category = item.dataset.category;
        const key = category === 'half-term' ? 'halfTerm' :
            category === 'book-bag' ? 'bookBag' : category;

        if (filterState[key]) {
            item.classList.remove('inactive');
        } else {
            item.classList.add('inactive');
        }
    });
}

// Bulk Actions Logic
elements.selectAllCheck.addEventListener('change', (e) => {
    const filtered = getFilteredEvents();
    const today = new Date();
    today.setHours(0, 0, 0, 0);

    const visibleInTab = currentTab === 'upcoming'
        ? filtered.filter(ev => new Date(ev.startDate) >= today)
        : filtered.filter(ev => new Date(ev.startDate) < today);

    if (e.target.checked) {
        visibleInTab.forEach(ev => selectedEventIds.add(ev.id));
    } else {
        selectedEventIds.clear();
    }
    renderTable(filtered);
    updateBulkBar();
});

elements.clearBtn.addEventListener('click', () => {
    selectedEventIds.clear();
    elements.selectAllCheck.checked = false;
    renderUI();
    updateBulkBar();
});

elements.hideBtn.addEventListener('click', () => {
    selectedEventIds.forEach(id => hiddenEventIds.add(id));
    saveHiddenIds();
    finishBulkAction();
});

elements.unhideBtn.addEventListener('click', () => {
    selectedEventIds.forEach(id => hiddenEventIds.delete(id));
    saveHiddenIds();
    finishBulkAction();
});

function saveHiddenIds() {
    localStorage.setItem('school-events-hidden-ids', JSON.stringify([...hiddenEventIds]));
}

function finishBulkAction() {
    selectedEventIds.clear();
    elements.selectAllCheck.checked = false;
    renderUI();
    updateBulkBar();
}

// For testing/debugging: Double-click logo to reset hidden items
document.querySelector('.logo').addEventListener('dblclick', () => {
    if (confirm('Reset all hidden events?')) {
        hiddenEventIds.clear();
        localStorage.removeItem('school-events-hidden-ids');
        renderUI();
    }
});

// Auto-Login on Boot
async function tryAutoLogin() {
    const savedAuth = JSON.parse(localStorage.getItem('school-events-saved-auth'));
    if (!savedAuth) return;

    if (Date.now() > savedAuth.expiry) {
        localStorage.removeItem('school-events-saved-auth');
        return;
    }

    try {
        elements.overlay.classList.add('hidden'); // Hide prompt immediately
        elements.loading.classList.remove('hidden');

        console.log("Attempting auto-login...");
        const response = await fetch('./events.json');
        if (!response.ok) throw new Error(`Fetch failed: ${response.status}`);

        const encryptedContent = await response.text();

        if (encryptedContent.startsWith("v1|")) {
            eventsData = await decrypt(encryptedContent, savedAuth.password);
        } else {
            eventsData = JSON.parse(encryptedContent);
        }

        // Initialize Filter UI
        if (elements.yearFilter) elements.yearFilter.value = selectedYear;
        elements.showHiddenToggle.checked = showHidden;
        updateLegendUI();

        renderUI();
        unlockUI();
    } catch (err) {
        console.warn("Auto-login failed:", err);
        localStorage.removeItem('school-events-saved-auth');
        elements.overlay.classList.remove('hidden');
        elements.loading.classList.add('hidden');
        elements.form.classList.remove('hidden');
    }
}

tryAutoLogin();
