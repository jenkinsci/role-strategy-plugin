/*
 * The MIT License
 *
 * Copyright (c) 2022-2026, Markus Winter, Tim Jacomb
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

// ============================================
// Data
// ============================================

// Assignment data: { globalRoles: [...], projectRoles: [...], slaveRoles: [...] }
const rspAssignmentData = {};

// Display name cache: { "USER:john": "John Doe", "GROUP:security-chapter": "Security Chapter" }
const rspDisplayNameCache = {};

// Role definitions loaded from embedded JSON
const rspRoleDefinitions = {
  globalRoles: [],
  projectRoles: [],
  slaveRoles: [],
};

const rspTypeLabels = {
  globalRoles: "Global",
  projectRoles: "Item",
  slaveRoles: "Agent",
};

const rspAssignTypes = ["globalRoles", "projectRoles", "slaveRoles"];

// Merged user map: { "USER:alice": { name, type, roles: { globalRoles: [...], ... } } }
let rspMergedUsers = {};

// ============================================
// Load data
// ============================================

const rspLoadRoleDefinitions = () => {
  rspAssignTypes.forEach((type) => {
    document.getElementById(`rsp-roles-${type.replace("Roles", "")}`);
    // Map: globalRoles -> rsp-roles-global, projectRoles -> rsp-roles-project, slaveRoles -> rsp-roles-slave
    const idMap = { globalRoles: "rsp-roles-global", projectRoles: "rsp-roles-project", slaveRoles: "rsp-roles-slave" };
    const scriptEl = document.getElementById(idMap[type]);
    if (scriptEl) {
      try { rspRoleDefinitions[type] = JSON.parse(scriptEl.textContent); } catch (e) {}
    }
  });
};

// Server-side paginated fetch
const rspFetchPage = (start, count, query, roleFilters) => {
  const dataHolder = document.getElementById("role-strategy-data");
  if (!dataHolder) return Promise.resolve({ total: 0, items: [] });

  const fetchUrl = dataHolder.dataset.fetchUrl.replace("/getRoleAssignments", "/getPaginatedAssignments");
  const params = new URLSearchParams({ start, limit: count });
  if (query) params.set("query", query);
  if (roleFilters && roleFilters.length > 0) {
    params.set("filterRole", roleFilters.map((f) => f.assignType + ":" + f.roleName).join(","));
  }

  // Search display name cache for matches and send as includeSids
  if (query) {
    const lowerQuery = query.toLowerCase();
    const matchingSids = [];
    for (const [key, displayName] of Object.entries(rspDisplayNameCache)) {
      if (displayName.toLowerCase().includes(lowerQuery)) {
        matchingSids.push(key);
      }
    }
    if (matchingSids.length > 0) {
      params.set("includeSids", matchingSids.join(","));
    }
  }

  return fetch(fetchUrl + "?" + params)
    .then((rsp) => rsp.json())
    .catch(() => ({ total: 0, items: [] }));
};

// Legacy client-side data (kept for assignment saving and dialog)
const rspLoadAllAssignments = () => {
  const dataHolder = document.getElementById("role-strategy-data");
  if (!dataHolder) return Promise.resolve();
  const fetchUrl = dataHolder.dataset.fetchUrl;
  if (!fetchUrl) return Promise.resolve();
  const promises = rspAssignTypes.map((type) => {
    const params = new URLSearchParams({ type });
    return fetch(fetchUrl + "?" + params)
      .then((rsp) => rsp.json())
      .then((json) => { rspAssignmentData[type] = json; })
      .catch(() => { rspAssignmentData[type] = []; });
  });
  return Promise.all(promises);
};

// ============================================
// Build user summary
// ============================================

const rspBuildUserSummary = (userData) => {
  const parts = [];
  rspAssignTypes.forEach((type) => {
    const roles = userData.roles[type];
    if (roles && roles.length > 0) {
      parts.push(rspTypeLabels[type] + ": " + roles.join(", "));
    }
  });
  return parts.length > 0 ? parts.join(" \u00B7 ") : "";
};

// ============================================
// Render user cards
// ============================================

const rspGenerateIcon = (type) => {
  const icons = document.querySelector("#assign-roles-icons");
  const iconId = type === "GROUP" ? "rsp-people-icon" : "rsp-person-icon";
  return icons.content.querySelector(`#${iconId}`).cloneNode(true);
};

// ============================================
// Pagination
// ============================================

const RSP_PAGE_SIZE = 100;
let rspCurrentPage = 0;

let rspSearchDebounce = null;

const rspApplyFilterAndPaginate = () => {
  // Debounce search to avoid hammering the server
  if (rspSearchDebounce) clearTimeout(rspSearchDebounce);
  rspSearchDebounce = setTimeout(() => {
    rspCurrentPage = 0;
    rspRenderCurrentPage();
  }, 300);
};

const rspRenderCurrentPage = () => {
  rspCancelPendingValidations();
  const container = document.getElementById("rsp-user-cards");
  container.innerHTML = '<div class="rsp-assign__loading" style="padding:1rem;">Loading...</div>';

  const input = document.querySelector(".rsp-assign-search input");
  const query = input ? input.value.trim() : "";
  const start = rspCurrentPage * RSP_PAGE_SIZE;

  rspFetchPage(start, RSP_PAGE_SIZE, query || null, rspActiveRoleFilters).then((data) => {
    container.innerHTML = "";

    data.items.forEach((user) => {
      // Convert server format { roles: { globalRoles: [...], ... } } to flat user object
      const userData = { name: user.name, type: user.type, roles: user.roles };
      // Cache in rspMergedUsers for save compatibility
      const key = `${user.type}:${user.name}`;
      rspMergedUsers[key] = userData;
      rspRenderOneCard(container, userData);
    });

    Behaviour.applySubtree(container, true);
    rspUpdateCardBorders();
    // Debounce validation — wait for page to stabilize before firing requests
    if (rspValidationDebounce) clearTimeout(rspValidationDebounce);
    rspValidationDebounce = setTimeout(rspValidateUserCards, 500);

    const totalFiltered = data.total;
    const totalPages = Math.max(1, Math.ceil(totalFiltered / RSP_PAGE_SIZE));
    rspUpdatePaginationUI(totalFiltered, totalPages);

    const emptyState = document.getElementById("rsp-user-empty");
    if (emptyState) emptyState.hidden = totalFiltered > 0;
  });
};

const rspUpdatePaginationUI = (totalFiltered, totalPages) => {
  // Top count
  let topCount = document.getElementById("rsp-result-count");
  if (!topCount) {
    topCount = document.createElement("div");
    topCount.id = "rsp-result-count";
    topCount.style.cssText = "color:var(--text-color-secondary);font-size:0.875rem;margin-bottom:0.5rem;";
    const cardsContainer = document.getElementById("rsp-user-cards");
    cardsContainer.parentNode.insertBefore(topCount, cardsContainer);
  }
  topCount.textContent = totalFiltered > 0 ? totalFiltered.toLocaleString() + (totalFiltered === 1 ? " result" : " results") : "";

  // Bottom pagination
  let nav = document.getElementById("rsp-pagination");
  if (!nav) {
    nav = document.createElement("div");
    nav.id = "rsp-pagination";
    nav.style.cssText = "display:flex;align-items:center;justify-content:center;gap:1rem;padding:1rem 0;";
    const cardsContainer = document.getElementById("rsp-user-cards");
    cardsContainer.parentNode.insertBefore(nav, cardsContainer.nextSibling);
  }

  if (totalPages <= 1) {
    nav.innerHTML = "";
    return;
  }

  const fmt = (n) => n.toLocaleString();
  const start = rspCurrentPage * RSP_PAGE_SIZE + 1;
  const end = Math.min((rspCurrentPage + 1) * RSP_PAGE_SIZE, totalFiltered);

  nav.innerHTML = `
    <button class="jenkins-button jenkins-button--tertiary" ${rspCurrentPage === 0 ? "disabled" : ""} id="rsp-page-prev">Previous</button>
    <span style="font-size:0.875rem;">${fmt(start)}–${fmt(end)} of ${fmt(totalFiltered)}</span>
    <button class="jenkins-button jenkins-button--tertiary" ${rspCurrentPage >= totalPages - 1 ? "disabled" : ""} id="rsp-page-next">Next</button>
  `;

  document.getElementById("rsp-page-prev")?.addEventListener("click", () => {
    if (rspCurrentPage > 0) { rspCurrentPage--; rspRenderCurrentPage(); }
  });
  document.getElementById("rsp-page-next")?.addEventListener("click", () => {
    if (rspCurrentPage < totalPages - 1) { rspCurrentPage++; rspRenderCurrentPage(); }
  });
};

const rspRenderOneCard = (container, user) => {
    const dataHolder = document.getElementById("role-strategy-data");
    const isBuiltIn = (user.name === "anonymous" && user.type === "USER") ||
                      (user.name === "authenticated" && user.type === "GROUP");

    const card = document.createElement("div");
    card.classList.add("rsp-card");
    card.dataset.userName = user.name;
    card.dataset.userType = user.type;

    // Hidden validation target
    const validationTarget = document.createElement("div");
    validationTarget.classList.add("rsp-card__validation-target");
    validationTarget.style.display = "none";
    card.appendChild(validationTarget);

    // Header
    const header = document.createElement("div");
    header.classList.add("rsp-card__header");
    header.setAttribute("role", "button");
    header.setAttribute("tabindex", "0");
    header.setAttribute("aria-expanded", "false");

    // Icon
    const iconSpan = document.createElement("span");
    iconSpan.classList.add("rsp-assign__chip-icon");
    iconSpan.appendChild(rspGenerateIcon(user.type));
    header.appendChild(iconSpan);

    // Name — use cached display name if available
    const nameSpan = document.createElement("span");
    nameSpan.classList.add("rsp-card__name");
    const cacheKey = user.type + ":" + user.name;
    let displayName = rspDisplayNameCache[cacheKey] || user.name;
    if (user.name === "anonymous" && user.type === "USER") displayName = dataHolder.dataset.textAnonymous;
    if (user.name === "authenticated" && user.type === "GROUP") displayName = dataHolder.dataset.textAuthenticated;
    nameSpan.textContent = displayName;
    if (rspDisplayNameCache[cacheKey]) {
      nameSpan.setAttribute("tooltip", user.type + ": " + user.name);
    }
    header.appendChild(nameSpan);

    // Summary
    const summarySpan = document.createElement("span");
    summarySpan.classList.add("rsp-card__summary");
    const summary = rspBuildUserSummary(user);
    if (summary) {
      summarySpan.textContent = summary;
    } else {
      summarySpan.textContent = "No roles assigned";
      summarySpan.classList.add("rsp-card__summary--empty");
    }
    header.appendChild(summarySpan);

    // Actions — only show if user has edit permissions
    const canEdit = dataHolder.dataset.canEdit === "true";
    const actions = document.createElement("div");
    actions.classList.add("rsp-card__actions");
    if (canEdit && !isBuiltIn) {
      const deleteBtn = document.createElement("button");
      deleteBtn.type = "button";
      deleteBtn.classList.add("jenkins-button", "jenkins-button--tertiary", "jenkins-!-destructive-color", "rsp-card__action", "rsp-user-delete");
      deleteBtn.setAttribute("tooltip", `Remove ${user.name}`);
      deleteBtn.innerHTML = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" class="icon-sm"><path d="M112 112l20 320c.95 18.49 14.4 32 32 32h184c17.67 0 30.87-13.51 32-32l20-320" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="32"/><path stroke="currentColor" stroke-linecap="round" stroke-miterlimit="10" stroke-width="32" d="M80 112h352"/><path d="M192 112V72h0a23.93 23.93 0 0124-24h80a23.93 23.93 0 0124 24h0v40M256 176v224M184 176l8 224M328 176l-8 224" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="32"/></svg>';
      actions.appendChild(deleteBtn);
    }
    if (!canEdit) card.classList.add("rsp-card--read-only");
    header.appendChild(actions);

    // Toggle
    const toggle = document.createElement("div");
    toggle.classList.add("rsp-card__toggle");
    toggle.innerHTML = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" class="icon-sm"><path fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="48" d="M112 184l144 144 144-144"/></svg>';
    header.appendChild(toggle);

    card.appendChild(header);

    // Body — lazy loaded on first expand
    const body = document.createElement("div");
    body.classList.add("rsp-card__body", "rsp-card__body--collapsed");
    body.dataset.lazy = "true";
    card.appendChild(body);
    container.appendChild(card);
};

// Lazy-build role checkboxes for a card body
const rspPopulateCardBody = (card) => {
  const body = card.querySelector(".rsp-card__body");
  if (!body || body.dataset.lazy !== "true") return;
  body.dataset.lazy = "false";

  const userName = card.dataset.userName;
  const userType = card.dataset.userType;
  const key = `${userType}:${userName}`;
  const user = rspMergedUsers[key];
  if (!user) return;

  const roleSection = document.createElement("div");
  roleSection.classList.add("rsp-perm");
  roleSection.style.padding = "0.75rem";

  rspAssignTypes.forEach((type) => {
    const roles = rspRoleDefinitions[type];
    if (!roles || roles.length === 0) return;

    const group = document.createElement("fieldset");
    group.classList.add("rsp-perm__group");

    const legend = document.createElement("legend");
    legend.classList.add("rsp-perm__group-title");
    legend.textContent = rspTypeLabels[type] + " roles";
    group.appendChild(legend);

    const perms = document.createElement("div");
    perms.classList.add("rsp-perm__permissions");

    roles.forEach((role) => {
      const label = document.createElement("label");
      label.classList.add("rsp-perm__item");
      label.dataset.roleName = role.name;
      label.dataset.assignType = type;

      const cb = document.createElement("input");
      cb.type = "checkbox";
      cb.dataset.roleName = role.name;
      cb.dataset.assignType = type;
      cb.checked = (user.roles[type] || []).includes(role.name);
      label.appendChild(cb);

      const nameSpan = document.createElement("span");
      nameSpan.classList.add("rsp-perm__item-name");
      nameSpan.textContent = role.name;
      label.appendChild(nameSpan);

      if (role.pattern) {
        const patternSpan = document.createElement("span");
        patternSpan.classList.add("rsp-assign-dialog__role-pattern");
        patternSpan.textContent = ` "${role.pattern}"`;
        label.appendChild(patternSpan);
      }

      perms.appendChild(label);
    });

    group.appendChild(perms);
    roleSection.appendChild(group);
  });

  body.appendChild(roleSection);
  Behaviour.applySubtree(body, true);
};

// Initial render — just fetch first page
const rspRenderUserCards = () => {
  rspCurrentPage = 0;
  rspRenderCurrentPage();
};

// ============================================
// User/group validation against security realm
// ============================================

const rspProcessValidation = (card) => {
  const target = card.querySelector(".rsp-card__validation-target");
  if (!target) return;

  const nameEl = card.querySelector(".rsp-card__name");

  // Check for not-found state
  const notFound = target.querySelector(".rsp-entry-not-found");
  if (notFound) {
    card.classList.add("rsp-card--not-found");
  } else {
    card.classList.remove("rsp-card--not-found");
  }

  // Check for warning state
  const warningCell = target.querySelector(".rsp-table__icon-alert");
  if (warningCell) {
    card.classList.add("rsp-card--warning");
  }

  // Extract display name from the validation response
  const responseDiv = target.querySelector(".rsp-table__cell");
  if (responseDiv && nameEl) {
    // The response contains icon SVGs + text. Get the text content after icons.
    const textNodes = [];
    responseDiv.childNodes.forEach((node) => {
      if (node.nodeType === Node.TEXT_NODE) {
        const text = node.textContent.trim();
        if (text) textNodes.push(text);
      } else if (node.tagName === "SPAN") {
        const text = node.textContent.trim();
        if (text) textNodes.push(text);
      }
    });
    const displayName = textNodes.join("").trim();
    if (displayName && displayName !== card.dataset.userName) {
      nameEl.textContent = displayName;
      nameEl.setAttribute("tooltip", card.dataset.userType + ": " + card.dataset.userName);
      // Cache the resolved display name for search
      const cacheKey = card.dataset.userType + ":" + card.dataset.userName;
      rspDisplayNameCache[cacheKey] = displayName;
    }

    // Copy tooltip from validation response
    const tooltip = responseDiv.getAttribute("tooltip");
    if (tooltip) {
      nameEl.setAttribute("tooltip", tooltip);
    }
  }
};

// Track active validation so we can abort on page change
let rspValidationAbortController = null;
let rspValidationDebounce = null;

const rspCancelPendingValidations = () => {
  if (rspValidationAbortController) { rspValidationAbortController.abort(); rspValidationAbortController = null; }
  if (rspValidationDebounce) { clearTimeout(rspValidationDebounce); rspValidationDebounce = null; }
};

const rspValidateUserCards = () => {
  const dataHolder = document.getElementById("role-strategy-data");
  const descriptorUrl = dataHolder?.dataset.descriptorUrl;
  if (!descriptorUrl) return;

  // Abort any previous validation batch
  if (rspValidationAbortController) rspValidationAbortController.abort();
  rspValidationAbortController = new AbortController();
  const signal = rspValidationAbortController.signal;

  // Collect cards to validate
  const cards = [];
  document.querySelectorAll("#rsp-user-cards .rsp-card").forEach((card) => {
    const userName = card.dataset.userName;
    const userType = card.dataset.userType;
    if (!userName || !userType) return;
    if (userName === "anonymous" && userType === "USER") return;
    if (userName === "authenticated" && userType === "GROUP") return;
    if (!card.querySelector(".rsp-card__validation-target")) return;
    cards.push(card);
  });

  const maxParallel = isHttp2Enabled() ? 30 : 1;

  const validateCard = (card) => {
    if (signal.aborted) return Promise.resolve();
    const target = card.querySelector(".rsp-card__validation-target");
    const checkValue = "[" + card.dataset.userType + ":" + card.dataset.userName + "]";
    const checkUrl = descriptorUrl + "/checkName?value=" + encodeURIComponent(checkValue);
    return fetch(checkUrl, { method: "POST", headers: crumb.wrap({}), signal })
      .then((rsp) => rsp.text())
      .then((html) => {
        if (signal.aborted) return;
        target.innerHTML = html;
        rspProcessValidation(card);
      })
      .catch(() => {});
  };

  // Process in batches of maxParallel
  let idx = 0;
  const processNext = () => {
    if (signal.aborted || idx >= cards.length) return Promise.resolve();
    const batch = cards.slice(idx, idx + maxParallel);
    idx += maxParallel;
    return Promise.all(batch.map(validateCard)).then(processNext);
  };
  processNext();
};

// ============================================
// Save assignments
// ============================================

const rspSaveAssignments = () => {
  const dataHolder = document.getElementById("role-strategy-data");
  const formData = new FormData();
  const assignMapping = { rolesMapping: rspAssignmentData };
  formData.append("json", JSON.stringify(assignMapping));

  return fetch(dataHolder.dataset.assignSubmitUrl, {
    method: "POST",
    headers: crumb.wrap({}),
    body: formData,
  }).then((rsp) => {
    if (!rsp.ok) throw new Error("Failed to save assignments");
  });
};

let rspAutoSaveTimer = null;
const rspAutoSave = () => {
  if (rspAutoSaveTimer) clearTimeout(rspAutoSaveTimer);
  rspAutoSaveTimer = setTimeout(() => {
    rspSaveAssignments().catch((err) => {
      notificationBar.show("Failed to save: " + err.message, notificationBar.ERROR);
    });
  }, 500);
};

// ============================================
// Search
// ============================================

// Active role filters: [{ assignType, roleName }, ...]
let rspActiveRoleFilters = [];

const rspApplyUserFilters = () => {
  rspApplyFilterAndPaginate();

  // Update filter button active state
  const filterBtn = document.querySelector(".rsp-role-filter-btn");
  if (filterBtn) {
    const active = rspActiveRoleFilters.length > 0;
    filterBtn.classList.toggle("jenkins-button--tertiary", !active);
    filterBtn.classList.toggle("jenkins-!-accent-color", active);
  }
  const resetBtn = document.querySelector(".rsp-role-filter-reset");
  if (resetBtn) resetBtn.hidden = rspActiveRoleFilters.length === 0;
};

const rspPopulateRoleFilter = () => {
  const list = document.querySelector(".rsp-role-filter-list");
  if (!list) return;
  list.innerHTML = "";

  rspAssignTypes.forEach((type) => {
    const roles = rspRoleDefinitions[type];
    if (!roles || roles.length === 0) return;

    const groupTitle = document.createElement("div");
    groupTitle.classList.add("rsp-filter__group-title");
    groupTitle.textContent = rspTypeLabels[type] + " roles";
    list.appendChild(groupTitle);

    roles.forEach((role) => {
      const item = document.createElement("button");
      item.type = "button";
      item.classList.add("rsp-filter__item");
      item.dataset.assignType = type;
      item.dataset.roleName = role.name;
      item.dataset.filterLabel = (rspTypeLabels[type] + " " + role.name).toLowerCase();

      item.innerHTML = `<span class="rsp-filter__item-indicator"></span>
        <span class="rsp-filter__item-name">${escapeHTML(role.name)}</span>`;

      if (role.pattern) {
        const patternSpan = document.createElement("span");
        patternSpan.style.cssText = "font-size:0.75rem;color:var(--text-color-secondary);margin-left:0.25rem;";
        patternSpan.textContent = `"${role.pattern}"`;
        item.appendChild(patternSpan);
      }

      item.addEventListener("click", () => {
        item.classList.toggle("rsp-filter__item--active");
        // Rebuild active filters
        rspActiveRoleFilters = [];
        list.querySelectorAll(".rsp-filter__item--active").forEach((active) => {
          rspActiveRoleFilters.push({
            assignType: active.dataset.assignType,
            roleName: active.dataset.roleName,
          });
        });
        rspApplyUserFilters();
      });

      list.appendChild(item);
    });
  });
};

// Initialize role filter dropdown behaviour
const rspInitRoleFilterDropdown = () => {
  const btn = document.querySelector(".rsp-role-filter-btn");
  const dropdown = document.querySelector(".rsp-role-filter-dropdown");
  if (!btn || !dropdown) return;

  const searchInput = dropdown.querySelector(".rsp-role-filter-search input");
  const resetBtn = dropdown.querySelector(".rsp-role-filter-reset");

  // Search within dropdown
  const applyFilterSearch = () => {
    const q = searchInput ? searchInput.value.toLowerCase().trim() : "";
    dropdown.querySelectorAll(".rsp-filter__item").forEach((item) => {
      const label = item.dataset.filterLabel || "";
      item.classList.toggle("rsp-filter__item--filter-hidden", q !== "" && !label.includes(q));
    });
    dropdown.querySelectorAll(".rsp-filter__group-title").forEach((title) => {
      let next = title.nextElementSibling;
      let hasVisible = false;
      while (next && !next.classList.contains("rsp-filter__group-title")) {
        if (next.classList.contains("rsp-filter__item") && !next.classList.contains("rsp-filter__item--filter-hidden")) {
          hasVisible = true;
        }
        next = next.nextElementSibling;
      }
      title.classList.toggle("rsp-filter__group-title--filter-hidden", !hasVisible);
    });
  };

  if (searchInput) {
    searchInput.addEventListener("input", applyFilterSearch);
    searchInput.addEventListener("click", (e) => e.stopPropagation());
  }

  if (resetBtn) {
    resetBtn.addEventListener("click", (e) => {
      e.preventDefault();
      dropdown.querySelectorAll(".rsp-filter__item--active").forEach((item) => {
        item.classList.remove("rsp-filter__item--active");
      });
      rspActiveRoleFilters = [];
      if (searchInput) searchInput.value = "";
      applyFilterSearch();
      rspApplyUserFilters();
    });
  }

  // Toggle dropdown
  btn.addEventListener("click", (e) => {
    e.stopPropagation();
    const isOpen = !dropdown.hidden;
    dropdown.hidden = isOpen;
    btn.setAttribute("aria-expanded", String(!isOpen));

    if (!isOpen) {
      const closeDropdown = () => {
        dropdown.hidden = true;
        btn.setAttribute("aria-expanded", "false");
        document.removeEventListener("click", clickHandler);
        document.removeEventListener("keydown", escHandler);
      };
      const clickHandler = (evt) => {
        if (!dropdown.contains(evt.target) && evt.target !== btn) closeDropdown();
      };
      const escHandler = (evt) => {
        if (evt.key === "Escape") { closeDropdown(); btn.focus(); }
      };
      setTimeout(() => {
        document.addEventListener("click", clickHandler);
        document.addEventListener("keydown", escHandler);
      }, 0);
    }
  });
};

// ============================================
// Assign role dialog
// ============================================

const rspAssignRoleDialog = () => {
  const rootUrl = document.querySelector("[data-rooturl]")?.getAttribute("data-rooturl") || "";
  const dialogUrl = rootUrl + "/manage/role-strategy/assign-role-dialog";

  dialog.wizard(dialogUrl, {
    onClose: () => {
      // Reload page to show updated assignments
      window.location.reload();
    }
  });
};

// ============================================
// Behaviours
// ============================================

// Card header toggle
Behaviour.specify("#rsp-user-cards .rsp-card__header", "RoleStrategyAssign", 0, (header) => {
  if (header.dataset.initialized === "true") return;
  header.dataset.initialized = "true";

  const handleToggle = (e) => {
    if (e.target.closest(".rsp-card__actions") && !e.target.closest(".rsp-card__toggle")) return;
    const card = header.closest(".rsp-card");
    if (!card) return;
    const body = card.querySelector(".rsp-card__body");
    const isExpanded = !body.classList.contains("rsp-card__body--collapsed");
    // Lazy-load role checkboxes on first expand
    if (!isExpanded) rspPopulateCardBody(card);
    body.classList.toggle("rsp-card__body--collapsed");
    header.setAttribute("aria-expanded", String(!isExpanded));
    card.setAttribute("aria-expanded", String(!isExpanded));
  };

  header.addEventListener("click", handleToggle);
  header.addEventListener("keydown", (e) => {
    if (e.key === "Enter" || e.key === " ") { e.preventDefault(); handleToggle(e); }
  });
});

// Role checkbox toggle — auto-save
Behaviour.specify("#rsp-user-cards .rsp-perm__item input[type=checkbox]", "RoleStrategyAssign", 0, (cb) => {
  if (cb.dataset.initialized === "true") return;
  cb.dataset.initialized = "true";

  cb.addEventListener("change", () => {
    const card = cb.closest(".rsp-card");
    if (!card) return;
    if (card.classList.contains("rsp-card--read-only")) { cb.checked = !cb.checked; return; }
    const userName = card.dataset.userName;
    const userType = card.dataset.userType;
    const roleName = cb.dataset.roleName;
    const assignType = cb.dataset.assignType;

    // Update assignment data
    if (!rspAssignmentData[assignType]) rspAssignmentData[assignType] = [];
    let entry = rspAssignmentData[assignType].find((e) => e.name === userName && e.type === userType);
    if (!entry) {
      entry = { name: userName, type: userType, roles: [] };
      rspAssignmentData[assignType].push(entry);
    }

    if (cb.checked) {
      if (!entry.roles.includes(roleName)) entry.roles.push(roleName);
    } else {
      const idx = entry.roles.indexOf(roleName);
      if (idx !== -1) entry.roles.splice(idx, 1);
    }

    // Update merged data and summary
    const key = `${userType}:${userName}`;
    if (rspMergedUsers[key]) {
      rspMergedUsers[key].roles[assignType] = [...entry.roles];
    }
    const summaryEl = card.querySelector(".rsp-card__summary");
    if (summaryEl) {
      const summary = rspBuildUserSummary(rspMergedUsers[key]);
      if (summary) {
        summaryEl.textContent = summary;
        summaryEl.classList.remove("rsp-card__summary--empty");
      } else {
        summaryEl.textContent = "No roles assigned";
        summaryEl.classList.add("rsp-card__summary--empty");
      }
    }

    rspAutoSave();
  });
});

// Delete user
Behaviour.specify(".rsp-user-delete", "RoleStrategyAssign", 0, (btn) => {
  if (btn.dataset.initialized === "true") return;
  btn.dataset.initialized = "true";

  btn.addEventListener("click", (e) => {
    e.stopPropagation();
    const card = btn.closest(".rsp-card");
    if (!card) return;
    const userName = card.dataset.userName;
    const userType = card.dataset.userType;

    dialog.confirm(`Remove all role assignments for "${userName}"?`, {
      type: "destructive",
      okText: "Remove",
    }).then(() => {
      // Remove from all assignment data
      rspAssignTypes.forEach((type) => {
        const json = rspAssignmentData[type];
        if (!json) return;
        const idx = json.findIndex((e) => e.name === userName && e.type === userType);
        if (idx !== -1) json.splice(idx, 1);
      });

      delete rspMergedUsers[`${userType}:${userName}`];
      card.remove();
      rspUpdateCardBorders();

      rspSaveAssignments().then(() => {
        notificationBar.show(`Removed "${userName}"`, notificationBar.SUCCESS);
      }).catch((err) => {
        notificationBar.show("Failed to save: " + err.message, notificationBar.ERROR);
      });
    }).catch(() => {});
  });
});

// Assign role button
Behaviour.specify(".rsp-assign-role-btn", "RoleStrategyAssign", 0, (btn) => {
  if (btn.dataset.initialized === "true") return;
  btn.dataset.initialized = "true";
  btn.addEventListener("click", rspAssignRoleDialog);
});

// Assign role dialog submit button + enter key
Behaviour.specify("#rsp-assign-role-submit-btn", "RoleStrategyAssign", 0, (btn) => {
  if (btn.dataset.initialized === "true") return;
  btn.dataset.initialized = "true";

  const form = btn.closest("form");
  if (!form) return;

  const validateAndSubmit = () => {
    const nameInput = form.querySelector("input[name='name']");
    if (!nameInput || !nameInput.value.trim()) {
      nameInput?.focus();
      if (nameInput) {
        nameInput.style.outline = "2px solid var(--error-color)";
        nameInput.addEventListener("input", () => { nameInput.style.outline = ""; }, { once: true });
      }
      return;
    }
    form.requestSubmit();
  };

  btn.addEventListener("click", validateAndSubmit);

  // Enter key anywhere in the form triggers submit
  form.addEventListener("keydown", (e) => {
    if (e.key === "Enter") {
      e.preventDefault();
      validateAndSubmit();
    }
  });
});

// Role dialog filter
Behaviour.specify(".rsp-role-dialog-filter input", "RoleStrategyAssign", 0, (input) => {
  if (input.dataset.initialized === "true") return;
  input.dataset.initialized = "true";
  input.addEventListener("input", () => {
    const q = input.value.toLowerCase().trim();
    const container = input.closest(".jenkins-form-item")?.querySelector(".rsp-assign-dialog__roles");
    if (!container) return;

    container.querySelectorAll(".rsp-assign-dialog__role-item").forEach((item) => {
      const name = (item.dataset.roleName || "").toLowerCase();
      item.style.display = (q === "" || name.includes(q)) ? "" : "none";
    });

    container.querySelectorAll(".rsp-assign-dialog__group-title").forEach((title) => {
      const next = title.nextElementSibling;
      let hasVisible = false;
      if (next && next.classList.contains("rsp-assign-dialog__group")) {
        next.querySelectorAll(".rsp-assign-dialog__role-item").forEach((child) => {
          if (child.style.display !== "none") hasVisible = true;
        });
      }
      title.style.display = hasVisible ? "" : "none";
    });
  });
});

// Search
Behaviour.specify(".rsp-assign-search input", "RoleStrategyAssign", 0, (input) => {
  if (input.dataset.initialized === "true") return;
  input.dataset.initialized = "true";
  input.addEventListener("input", rspApplyUserFilters);
});

// ============================================
// Initialization
// ============================================

document.addEventListener("DOMContentLoaded", () => {
  rspLoadRoleDefinitions();
  rspPopulateRoleFilter();
  rspInitRoleFilterDropdown();
  // Load assignments for save compatibility (background), render via paginated endpoint
  rspLoadAllAssignments();
  rspRenderUserCards();
});
