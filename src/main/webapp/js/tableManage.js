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

// Assignment data per section: { globalRoles: [...], projectRoles: [...], slaveRoles: [...] }
const rspAssignmentData = {};

// ============================================
// Card expand/collapse
// ============================================

const rspToggleCard = (card) => {
  const body = card.querySelector(".rsp-card__body");
  const header = card.querySelector(".rsp-card__header");
  if (!body || !header) return;

  const isExpanded = !body.classList.contains("rsp-card__body--collapsed");
  body.classList.toggle("rsp-card__body--collapsed");
  header.setAttribute("aria-expanded", String(!isExpanded));
  card.setAttribute("aria-expanded", String(!isExpanded));

  // Lazy-load assignments on first expand
  if (!isExpanded) {
    const assignSection = card.querySelector(".rsp-assign[data-loaded='false']");
    if (assignSection) {
      rspLoadAssignmentsForCard(card);
    }
  }
};

// ============================================
// Permission summary
// ============================================

const rspBuildSummary = (card) => {
  const body = card.querySelector(".rsp-card__body");
  if (!body) return "";

  const groups = body.querySelectorAll(".rsp-perm__group");
  const parts = [];
  groups.forEach((group) => {
    const title = group.querySelector(".rsp-perm__group-title");
    if (!title) return;
    const checked = [];
    group.querySelectorAll("input[type=checkbox]").forEach((cb) => {
      const label = cb.closest(".rsp-perm__item");
      const isImplied = label?.classList.contains("rsp-perm__item--implied");
      if (cb.checked && !isImplied) {
        const nameEl = label ? label.querySelector(".rsp-perm__item-name") : null;
        if (nameEl) checked.push(nameEl.textContent.trim());
      }
    });
    if (checked.length > 0) {
      parts.push(title.textContent.trim() + ": " + checked.join(", "));
    }
  });
  return parts.join(" \u00B7 ");
};

const rspUpdateSummary = (card) => {
  const summaryEl = card.querySelector(".rsp-card__summary");
  if (!summaryEl) return;

  const summary = rspBuildSummary(card);
  if (summary) {
    summaryEl.textContent = summary;
    summaryEl.classList.remove("rsp-card__summary--empty");
  } else {
    summaryEl.textContent = summaryEl.getAttribute("data-empty-text") || "No permissions";
    summaryEl.classList.add("rsp-card__summary--empty");
  }
};

// ============================================
// Implied permissions
// ============================================

const rspUpdateImplied = (card) => {
  const labels = card.querySelectorAll(".rsp-perm__item[data-permission-id]");
  labels.forEach((label) => {
    const checkbox = label.querySelector("input[type=checkbox]");
    if (!checkbox) return;

    // Skip template-disabled checkboxes
    if (label.closest(".rsp-card[data-template-name]")?.dataset.templateName) {
      return;
    }

    const impliedByStr = label.getAttribute("data-implied-by-list");
    if (!impliedByStr || impliedByStr.trim() === "") return;

    const impliedByList = impliedByStr.trim().split(" ");
    let isImplied = false;

    for (const permId of impliedByList) {
      const ref = card.querySelector(`.rsp-perm__item[data-permission-id='${permId}'] input[type=checkbox]`);
      if (ref && ref.checked) {
        isImplied = true;
        break;
      }
    }

    const impliedLabel = label.querySelector(".rsp-perm__item-implied");
    if (isImplied) {
      checkbox.checked = false;
      checkbox.disabled = true;
      label.classList.add("rsp-perm__item--implied");
      if (impliedLabel) impliedLabel.hidden = false;
    } else {
      checkbox.disabled = false;
      label.classList.remove("rsp-perm__item--implied");
      if (impliedLabel) impliedLabel.hidden = true;
    }
  });
};

// ============================================
// Search and filter
// ============================================

const rspApplyFilters = (container) => {
  const searchInput = container.querySelector(".rsp-search input");
  const query = searchInput ? searchInput.value.toLowerCase().trim() : "";

  const activePermissions = [];
  container.querySelectorAll(".rsp-filter__item--active").forEach((item) => {
    activePermissions.push(item.getAttribute("data-filter-permission"));
  });

  const cards = container.querySelectorAll(".rsp-card");
  let visibleCount = 0;

  cards.forEach((card) => {
    const roleName = (card.getAttribute("data-role-name") || "").toLowerCase();
    const pattern = (card.getAttribute("data-role-pattern") || "").toLowerCase();
    const matchesText = query === "" || roleName.includes(query) || pattern.includes(query);

    let matchesPermission = activePermissions.length === 0;
    if (!matchesPermission) {
      for (const permId of activePermissions) {
        const cb = card.querySelector(`.rsp-perm__item[data-permission-id='${permId}'] input[type=checkbox]`);
        if (cb && (cb.checked || cb.disabled)) {
          matchesPermission = true;
          break;
        }
      }
    }

    if (matchesText && matchesPermission) {
      card.classList.remove("rsp-card--hidden");
      visibleCount++;
    } else {
      card.classList.add("rsp-card--hidden");
    }
  });

  const emptyState = container.querySelector(".rsp-empty-state");
  if (emptyState) {
    emptyState.hidden = visibleCount > 0 || (query === "" && activePermissions.length === 0);
  }

  const hasActiveFilters = activePermissions.length > 0;
  const filterBtn = container.querySelector(".rsp-filter__button");
  if (filterBtn) {
    filterBtn.classList.toggle("rsp-filter__button--active", hasActiveFilters);
  }
  const resetBtn = container.querySelector(".rsp-filter__reset-button");
  if (resetBtn) {
    resetBtn.hidden = !hasActiveFilters;
  }
};

// ============================================
// Assignments
// ============================================

const rspGenerateSVGIcon = (iconName) => {
  const icons = document.querySelector("#assign-roles-icons");
  return icons.content.querySelector(`#${iconName}`).cloneNode(true);
};

const rspCreateAssignChip = (name, type, container, roleName) => {
  const chip = document.createElement("span");
  chip.classList.add("rsp-assign__chip");
  if (type === "EITHER") chip.classList.add("rsp-assign__chip--either");
  chip.dataset.name = name;
  chip.dataset.type = type;

  // Icon
  const iconSpan = document.createElement("span");
  iconSpan.classList.add("rsp-assign__chip-icon");
  const iconName = (type === "GROUP" || (type === "EITHER")) ? "rsp-people-icon" : "rsp-person-icon";
  iconSpan.appendChild(rspGenerateSVGIcon(iconName));
  chip.appendChild(iconSpan);

  // Name
  const dataHolder = document.getElementById("role-strategy-data");
  let displayName = name;
  if (name === "anonymous" && type === "USER") {
    displayName = dataHolder.dataset.textAnonymous;
  } else if (name === "authenticated" && type === "GROUP") {
    displayName = dataHolder.dataset.textAuthenticated;
  }
  chip.appendChild(document.createTextNode(displayName));

  // Remove button
  const isReadOnly = container.closest(".rsp-card")?.classList.contains("rsp-card--read-only");
  if (!isReadOnly) {
    // Migration buttons for EITHER type
    if (type === "EITHER") {
      const migrateUser = document.createElement("button");
      migrateUser.type = "button";
      migrateUser.classList.add("rsp-assign__chip-migrate");
      migrateUser.title = "Migrate to user";
      migrateUser.dataset.migrateType = "USER";
      migrateUser.appendChild(rspGenerateSVGIcon("rsp-person-icon"));
      migrateUser.addEventListener("click", (e) => {
        e.stopPropagation();
        rspMigrateAssignment(chip, "USER", roleName, container);
      });
      chip.appendChild(migrateUser);

      const migrateGroup = document.createElement("button");
      migrateGroup.type = "button";
      migrateGroup.classList.add("rsp-assign__chip-migrate");
      migrateGroup.title = "Migrate to group";
      migrateGroup.dataset.migrateType = "GROUP";
      migrateGroup.appendChild(rspGenerateSVGIcon("rsp-people-icon"));
      migrateGroup.addEventListener("click", (e) => {
        e.stopPropagation();
        rspMigrateAssignment(chip, "GROUP", roleName, container);
      });
      chip.appendChild(migrateGroup);
    }

    const removeBtn = document.createElement("button");
    removeBtn.type = "button";
    removeBtn.classList.add("rsp-assign__chip-remove");
    removeBtn.innerHTML = "&#xd7;";
    removeBtn.title = `Remove ${name}`;
    const isBuiltIn = (name === "anonymous" && type === "USER") || (name === "authenticated" && type === "GROUP");
    if (isBuiltIn) {
      removeBtn.style.display = "none";
    }
    removeBtn.addEventListener("click", (e) => {
      e.stopPropagation();
      rspRemoveAssignment(chip, roleName, container);
    });
    chip.appendChild(removeBtn);
  }

  return chip;
};

const rspMigrateAssignment = (chip, newType, roleName, assignContainer) => {
  const sectionId = assignContainer.closest(".rsp-container").id;
  const assignType = assignContainer.closest(".rsp-container").dataset.assignType;
  const json = rspAssignmentData[assignType];
  if (!json) return;

  const oldName = chip.dataset.name;
  const oldType = chip.dataset.type;

  // Find the entry for this user across all roles
  for (const entry of json) {
    if (entry.name === oldName && entry.type === oldType) {
      entry.type = newType;
      break;
    }
  }

  // Refresh assignments display for all cards in this section
  const container = assignContainer.closest(".rsp-container");
  container.querySelectorAll(".rsp-assign[data-loaded='true']").forEach((section) => {
    rspRenderAssignments(section, section.dataset.roleName, assignType);
  });

  rspMarkDirty();
};

const rspRemoveAssignment = (chip, roleName, assignContainer) => {
  const assignType = assignContainer.closest(".rsp-container").dataset.assignType;
  const json = rspAssignmentData[assignType];
  if (!json) return;

  const name = chip.dataset.name;
  const type = chip.dataset.type;

  // Remove role from the entry's roles array
  for (const entry of json) {
    if (entry.name === name && entry.type === type) {
      const idx = entry.roles.indexOf(roleName);
      if (idx !== -1) entry.roles.splice(idx, 1);
      break;
    }
  }

  chip.remove();
  rspMarkDirty();
};

const rspRenderAssignments = (assignSection, roleName, assignType) => {
  const json = rspAssignmentData[assignType];
  if (!json) return;

  const list = assignSection.querySelector(".rsp-assign__list");
  list.innerHTML = "";

  // Find all entries that have this role
  const entries = json.filter((entry) => entry.roles.includes(roleName));

  // Sort: anonymous first, authenticated second, then alphabetical
  entries.sort((a, b) => {
    if (a.name === "anonymous" && a.type === "USER") return -1;
    if (b.name === "anonymous" && b.type === "USER") return 1;
    if (a.name === "authenticated" && a.type === "GROUP") return -1;
    if (b.name === "authenticated" && b.type === "GROUP") return 1;
    return a.name.localeCompare(b.name);
  });

  if (entries.length === 0) {
    const empty = document.createElement("span");
    empty.classList.add("rsp-assign__loading");
    empty.textContent = "No users or groups assigned";
    list.appendChild(empty);
    return;
  }

  const maxVisible = 10;
  let visibleCount = 0;
  const hiddenChips = [];

  entries.forEach((entry) => {
    const chip = rspCreateAssignChip(entry.name, entry.type, assignSection, roleName);
    if (visibleCount >= maxVisible) {
      chip.style.display = "none";
      hiddenChips.push(chip);
    }
    list.appendChild(chip);
    visibleCount++;
  });

  if (hiddenChips.length > 0) {
    const showMore = document.createElement("button");
    showMore.type = "button";
    showMore.classList.add("rsp-assign__show-more");
    showMore.textContent = `Show all (${entries.length})`;
    showMore.addEventListener("click", () => {
      hiddenChips.forEach((c) => { c.style.display = ""; });
      showMore.remove();
    });
    list.appendChild(showMore);
  }

  assignSection.dataset.loaded = "true";
};

const rspLoadAssignmentsForCard = (card) => {
  const container = card.closest(".rsp-container");
  const assignType = container.dataset.assignType;
  const roleName = card.dataset.roleName;
  const assignSection = card.querySelector(".rsp-assign");
  if (!assignSection) return;

  if (rspAssignmentData[assignType]) {
    rspRenderAssignments(assignSection, roleName, assignType);
    return;
  }

  // Need to fetch
  const dataHolder = document.getElementById("role-strategy-data");
  const fetchUrl = dataHolder.dataset.fetchUrl;
  const params = new URLSearchParams({ type: assignType });

  fetch(fetchUrl + "?" + params)
    .then((rsp) => rsp.json())
    .then((json) => {
      // Ensure anonymous and authenticated exist
      rspEnsureFixedEntry(json, "anonymous", "USER");
      rspEnsureFixedEntry(json, "authenticated", "GROUP");
      rspAssignmentData[assignType] = json;
      rspRenderAssignments(assignSection, roleName, assignType);
    })
    .catch(() => {
      const list = assignSection.querySelector(".rsp-assign__list");
      list.innerHTML = "<span class='rsp-assign__loading'>Failed to load assignments</span>";
    });
};

const rspEnsureFixedEntry = (json, name, type) => {
  const existing = json.find((e) => e.name === name && e.type === type);
  if (!existing) {
    json.push({ name, type, roles: [] });
  }
};

// ============================================
// Add assignment
// ============================================

const rspAddAssignmentToRole = (card, name, type) => {
  const container = card.closest(".rsp-container");
  const assignType = container.dataset.assignType;
  const roleName = card.dataset.roleName;
  const json = rspAssignmentData[assignType];
  if (!json) return;

  // Find or create entry
  let entry = json.find((e) => e.name === name && e.type === type);
  if (!entry) {
    entry = { name, type, roles: [] };
    json.push(entry);
  }

  if (!entry.roles.includes(roleName)) {
    entry.roles.push(roleName);
  }

  // Re-render
  const assignSection = card.querySelector(".rsp-assign");
  if (assignSection) {
    rspRenderAssignments(assignSection, roleName, assignType);
  }
  rspMarkDirty();
};

// ============================================
// Save
// ============================================

const rspCollectRolesData = () => {
  const result = {};

  document.querySelectorAll(".rsp-container").forEach((container) => {
    const roleType = container.dataset.roleType;
    if (!roleType) return;

    const roles = {};
    container.querySelectorAll(".rsp-card").forEach((card) => {
      const roleName = card.dataset.roleName;
      const roleData = {};

      // Pattern
      const pattern = card.dataset.rolePattern;
      if (pattern !== undefined && pattern !== "") {
        roleData.pattern = pattern;
      }

      // Template name
      const templateName = card.dataset.templateName;
      if (templateName) {
        roleData.templateName = templateName;
      }

      // Permissions
      card.querySelectorAll(".rsp-perm__item input[type=checkbox]").forEach((cb) => {
        const permId = cb.getAttribute("data-permission-id");
        if (permId && (cb.checked || cb.disabled)) {
          // For implied (disabled but not checked), we don't include them
          // Only include explicitly checked permissions
          if (cb.checked) {
            roleData[permId] = true;
          }
        }
      });

      roles[roleName] = roleData;
    });

    result[roleType] = { data: roles };
  });

  return result;
};

const rspSave = (redirect) => {
  const dataHolder = document.getElementById("role-strategy-data");

  // Save roles
  const rolesData = rspCollectRolesData();
  const rolesFormData = new FormData();
  rolesFormData.append("json", JSON.stringify(rolesData));

  // Ensure all assignment types have data (use empty arrays for unloaded sections)
  document.querySelectorAll(".rsp-container").forEach((container) => {
    const assignType = container.dataset.assignType;
    if (assignType && !rspAssignmentData[assignType]) {
      rspAssignmentData[assignType] = [];
    }
  });

  // Save assignments
  const assignFormData = new FormData();
  const assignMapping = { rolesMapping: rspAssignmentData };
  assignFormData.append("json", JSON.stringify(assignMapping));

  const headers = crumb.wrap({});

  // Sequential: roles first, then assignments
  return fetch(dataHolder.dataset.rolesSubmitUrl, {
    method: "POST",
    headers,
    body: rolesFormData,
  })
  .then((rsp) => {
    if (!rsp.ok) throw new Error("Failed to save roles");
    return fetch(dataHolder.dataset.assignSubmitUrl, {
      method: "POST",
      headers,
      body: assignFormData,
    });
  })
  .then((rsp) => {
    if (!rsp.ok) throw new Error("Failed to save assignments");
    return true;
  });
};

const rspMarkDirty = () => {
  const dirtyButton = document.getElementById("rs-dirty-indicator");
  if (dirtyButton) {
    dirtyButton.dispatchEvent(new Event("click"));
  }
};

// ============================================
// Pattern matching (show matching jobs/agents)
// ============================================

const rspShowMatchingItems = (pattern, roleType) => {
  const dataHolder = document.getElementById("role-strategy-data");
  const isAgent = roleType === "slaveRoles";
  const url = isAgent ? dataHolder.dataset.matchingAgentsUrl : dataHolder.dataset.matchingJobsUrl;
  const maxItems = isAgent ? 10 : 15;
  const paramKey = isAgent ? "maxAgents" : "maxJobs";
  const params = { pattern, [paramKey]: maxItems };

  fetch(url + toQueryString(params))
    .then((rsp) => rsp.ok ? rsp.json() : Promise.reject())
    .then((json) => {
      const items = isAgent ? json.matchingAgents : json.matchingJobs;
      const count = json.itemCount;
      if (items == null) {
        dialog.alert("Unable to fetch matching items.");
        return;
      }
      let title = "";
      if (items.length > 0) {
        title = count > items.length
          ? `First ${maxItems} items (out of ${count}) matching`
          : "Items matching";
      } else {
        title = "No items found matching";
      }
      title += ` "${pattern}"`;

      const el = document.createElement("div");
      items.forEach((item) => {
        el.appendChild(document.createTextNode("- " + item));
        el.appendChild(document.createElement("br"));
      });
      dialog.modal(el, { title });
    })
    .catch(() => {
      dialog.alert("Unable to fetch matching items.");
    });
};

// ============================================
// Add new role
// ============================================

const rspAddNewRole = (container) => {
  const sectionId = container.id;
  const isGlobal = sectionId === "globalRoles";

  const isProject = sectionId === "projectRoles";

  // Build dialog content
  const content = document.createElement("div");
  content.classList.add("rsp-dialog-content");

  // Role name field
  const nameGroup = document.createElement("div");
  nameGroup.classList.add("jenkins-form-item");
  nameGroup.innerHTML = `
    <label class="jenkins-form-label">Role name</label>
    <div class="jenkins-form-item__control">
      <input type="text" class="jenkins-input" id="rsp-dialog-role-name" autofocus />
    </div>
  `;
  content.appendChild(nameGroup);

  // Pattern field (non-global only)
  if (!isGlobal) {
    const patternGroup = document.createElement("div");
    patternGroup.classList.add("jenkins-form-item");
    patternGroup.innerHTML = `
      <label class="jenkins-form-label">Pattern</label>
      <div class="jenkins-form-item__control">
        <input type="text" class="jenkins-input" id="rsp-dialog-role-pattern" />
      </div>
    `;
    content.appendChild(patternGroup);
  }

  // Template selector (item roles only)
  if (isProject) {
    const templateStr = container.dataset.templates || "";
    const templateNames = templateStr ? templateStr.split(",").map((s) => s.trim()).filter(Boolean) : [];

    if (templateNames.length > 0) {
      const templateGroup = document.createElement("div");
      templateGroup.classList.add("jenkins-form-item");

      const options = templateNames.map((n) => `<option value="${escapeHTML(n)}">${escapeHTML(n)}</option>`).join("");
      templateGroup.innerHTML = `
        <label class="jenkins-form-label">Permission template</label>
        <div class="jenkins-form-item__control">
          <div class="jenkins-select">
            <select class="jenkins-select__input" id="rsp-dialog-role-template">
              <option value="">None (custom permissions)</option>
              ${options}
            </select>
          </div>
        </div>
      `;
      content.appendChild(templateGroup);
    }
  }

  // Permission selection - clone from existing card
  let permClone = null;
  const existingCard = container.querySelector(".rsp-card");
  if (existingCard) {
    const permSection = existingCard.querySelector(".rsp-perm");
    if (permSection) {
      permClone = permSection.cloneNode(true);
      permClone.querySelectorAll("input[type=checkbox]").forEach((cb) => {
        cb.checked = false;
        cb.disabled = false;
        cb.removeAttribute("data-initialized");
      });
      permClone.querySelectorAll(".rsp-perm__item--implied").forEach((el) => {
        el.classList.remove("rsp-perm__item--implied");
      });
      permClone.querySelectorAll(".rsp-perm__item-implied").forEach((el) => {
        el.hidden = true;
      });

      const permWrapper = document.createElement("div");
      permWrapper.classList.add("jenkins-form-item");
      const permTitle = document.createElement("label");
      permTitle.classList.add("jenkins-form-label");
      permTitle.textContent = "Permissions";
      permWrapper.appendChild(permTitle);
      permWrapper.appendChild(permClone);
      content.appendChild(permWrapper);

      // Wire up implied permissions within the dialog
      const updateDialogImplied = () => {
        permClone.querySelectorAll(".rsp-perm__item[data-permission-id]").forEach((label) => {
          const innerCb = label.querySelector("input[type=checkbox]");
          if (!innerCb) return;
          const impliedByStr = label.getAttribute("data-implied-by-list");
          if (!impliedByStr || impliedByStr.trim() === "") return;
          const impliedByList = impliedByStr.trim().split(" ");
          let isImplied = false;
          for (const permId of impliedByList) {
            const ref = permClone.querySelector(`.rsp-perm__item[data-permission-id='${permId}'] input[type=checkbox]`);
            if (ref && ref.checked) { isImplied = true; break; }
          }
          const impliedLabel = label.querySelector(".rsp-perm__item-implied");
          if (isImplied) {
            innerCb.checked = false;
            innerCb.disabled = true;
            label.classList.add("rsp-perm__item--implied");
            if (impliedLabel) impliedLabel.hidden = false;
          } else {
            innerCb.disabled = false;
            label.classList.remove("rsp-perm__item--implied");
            if (impliedLabel) impliedLabel.hidden = true;
          }
        });
      };
      permClone.querySelectorAll("input[type=checkbox]").forEach((cb) => {
        cb.addEventListener("change", updateDialogImplied);
      });
    }
  }

  // Build native dialog
  const dlg = document.createElement("dialog");
  dlg.classList.add("jenkins-dialog");
  dlg.style.cssText = "max-width:550px;min-width:450px;";

  const titleBar = document.createElement("div");
  titleBar.classList.add("jenkins-dialog__title");
  titleBar.innerHTML = `<span>Add Role</span>
    <button class="jenkins-dialog__title__button jenkins-dialog__title__close-button" data-id="cancel">
      <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" class="icon-sm"><path fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="32" d="M368 368L144 144M368 144L144 368"></path></svg>
    </button>`;
  dlg.appendChild(titleBar);

  const body = document.createElement("div");
  body.classList.add("jenkins-dialog__contents");
  body.appendChild(content);
  dlg.appendChild(body);

  const footer = document.createElement("div");
  footer.classList.add("jenkins-dialog__footer", "jenkins-buttons-row", "jenkins-buttons-row--equal-width");
  footer.innerHTML = `
    <button class="jenkins-button" data-id="cancel">Cancel</button>
    <button class="jenkins-button jenkins-button--primary" data-id="ok">Add</button>
  `;
  dlg.appendChild(footer);

  // Wire up template selector to disable/enable permissions
  const templateSelect = dlg.querySelector("#rsp-dialog-role-template");
  if (templateSelect && permClone) {
    templateSelect.addEventListener("change", () => {
      const isTemplate = templateSelect.value !== "";
      permClone.querySelectorAll("input[type=checkbox]").forEach((cb) => {
        if (isTemplate) {
          cb.checked = false;
          cb.disabled = true;
        } else {
          cb.disabled = false;
        }
      });
      permClone.style.opacity = isTemplate ? "0.5" : "1";
      permClone.style.pointerEvents = isTemplate ? "none" : "";
    });
  }

  document.body.appendChild(dlg);
  dlg.showModal();

  const closeDialog = () => {
    dlg.close();
    dlg.remove();
  };

  dlg.querySelectorAll("[data-id='cancel']").forEach((btn) => {
    btn.addEventListener("click", closeDialog);
  });

  dlg.addEventListener("cancel", closeDialog);

  dlg.querySelector("[data-id='ok']").addEventListener("click", () => {
    const nameInput = dlg.querySelector("#rsp-dialog-role-name");
    const name = nameInput?.value?.trim();
    if (!name) {
      nameInput?.focus();
      return;
    }

    // Check duplicate
    const existing = container.querySelector(`.rsp-card[data-role-name='${CSS.escape(name)}']`);
    if (existing) {
      closeDialog();
      dialog.alert(`A role named "${name}" already exists.`);
      return;
    }

    let pattern = "";
    if (!isGlobal) {
      const patternInput = dlg.querySelector("#rsp-dialog-role-pattern");
      pattern = patternInput?.value?.trim();
      if (!pattern) {
        patternInput?.focus();
        return;
      }
    }

    // Template selection (item roles)
    let templateName = "";
    const templateSelect = dlg.querySelector("#rsp-dialog-role-template");
    if (templateSelect) {
      templateName = templateSelect.value;
    }

    // Collect selected permissions
    const selectedPermissions = new Set();
    dlg.querySelectorAll(".rsp-perm__item input[type=checkbox]:checked").forEach((cb) => {
      const permId = cb.getAttribute("data-permission-id");
      if (permId) selectedPermissions.add(permId);
    });

    closeDialog();
    rspCreateRoleCard(container, name, pattern, templateName, selectedPermissions);
  });
};

const rspCreateRoleCard = (container, name, pattern, templateName, initialPermissions = new Set()) => {
  const cardsContainer = container.querySelector(".rsp-cards");
  const sectionId = container.id;

  // Find an existing card to clone as template
  const existingCard = container.querySelector(".rsp-card");

  const card = document.createElement("div");
  card.classList.add("rsp-card");
  card.dataset.roleName = name;
  card.dataset.rolePattern = pattern;
  card.dataset.templateName = templateName;
  card.dataset.sectionId = sectionId;

  // Build header
  const header = document.createElement("div");
  header.classList.add("rsp-card__header");
  header.setAttribute("role", "button");
  header.setAttribute("tabindex", "0");
  header.setAttribute("aria-expanded", "false");

  const nameSpan = document.createElement("span");
  nameSpan.classList.add("rsp-card__name");
  nameSpan.textContent = name;
  header.appendChild(nameSpan);

  if (pattern) {
    const patternSpan = document.createElement("span");
    patternSpan.classList.add("rsp-card__pattern");
    patternSpan.dataset.pattern = pattern;
    patternSpan.dataset.roleType = container.dataset.roleType;
    patternSpan.textContent = `"${pattern}"`;
    header.appendChild(patternSpan);
  }

  const summarySpan = document.createElement("span");
  summarySpan.classList.add("rsp-card__summary", "rsp-card__summary--empty");
  summarySpan.dataset.emptyText = "No permissions";
  summarySpan.textContent = "No permissions";
  header.appendChild(summarySpan);

  // Actions
  const actions = document.createElement("div");
  actions.classList.add("rsp-card__actions");

  if (pattern) {
    const editBtn = document.createElement("button");
    editBtn.type = "button";
    editBtn.classList.add("jenkins-button", "jenkins-button--tertiary", "rsp-card__action", "rsp-card__edit-pattern");
    editBtn.setAttribute("tooltip", "Edit pattern");
    editBtn.innerHTML = '<svg class="icon-sm"><use href="/jenkins/static/a2e59e3c/images/symbols/icons.svg#pencil"></use></svg>';
    actions.appendChild(editBtn);
  }

  const deleteBtn = document.createElement("button");
  deleteBtn.type = "button";
  deleteBtn.classList.add("jenkins-button", "jenkins-button--tertiary", "jenkins-!-destructive-color", "rsp-card__action", "rsp-card__delete");
  deleteBtn.setAttribute("tooltip", "Delete role");
  deleteBtn.innerHTML = '<svg class="icon-sm"><use href="/jenkins/static/a2e59e3c/images/symbols/icons.svg#trash-outline"></use></svg>';
  actions.appendChild(deleteBtn);
  header.appendChild(actions);

  // Toggle chevron
  const toggle = document.createElement("div");
  toggle.classList.add("rsp-card__toggle");
  toggle.innerHTML = '<svg class="icon-sm"><use href="/jenkins/static/a2e59e3c/images/symbols/icons.svg#chevron-down-outline"></use></svg>';
  header.appendChild(toggle);

  card.appendChild(header);

  // Body - clone permission groups from an existing card
  const body = document.createElement("div");
  body.classList.add("rsp-card__body", "rsp-card__body--collapsed");

  if (existingCard) {
    const existingPerm = existingCard.querySelector(".rsp-perm");
    if (existingPerm) {
      const permClone = existingPerm.cloneNode(true);
      // Set initial permissions
      permClone.querySelectorAll("input[type=checkbox]").forEach((cb) => {
        const permId = cb.getAttribute("data-permission-id");
        cb.checked = initialPermissions.has(permId);
        cb.disabled = false;
      });
      permClone.querySelectorAll(".rsp-perm__item--implied").forEach((el) => {
        el.classList.remove("rsp-perm__item--implied");
      });
      permClone.querySelectorAll(".rsp-perm__item-implied").forEach((el) => {
        el.hidden = true;
      });
      body.appendChild(permClone);
    }
  }

  // Assignment section
  const assignDiv = document.createElement("div");
  assignDiv.classList.add("rsp-assign");
  assignDiv.dataset.roleName = name;
  assignDiv.dataset.loaded = "false";
  assignDiv.innerHTML = `
    <h4 class="rsp-assign__title">Assigned Users and Groups</h4>
    <div class="rsp-assign__list">
      <span class="rsp-assign__loading">No users or groups assigned</span>
    </div>
    <div class="rsp-assign__actions">
      <button type="button" class="jenkins-button jenkins-button--tertiary rsp-assign__add" data-type="USER">Add User</button>
      <button type="button" class="jenkins-button jenkins-button--tertiary rsp-assign__add" data-type="GROUP">Add Group</button>
    </div>
  `;
  body.appendChild(assignDiv);

  card.appendChild(body);
  cardsContainer.appendChild(card);

  // Apply behaviors
  Behaviour.applySubtree(card, true);

  // Expand the new card
  rspToggleCard(card);
  card.classList.add("rsp-highlight-entry");

  rspMarkDirty();
};

// ============================================
// Behaviours
// ============================================

// Card header click
Behaviour.specify(".rsp-card__header", "RoleStrategyCards", 0, (header) => {
  if (header.dataset.initialized === "true") return;
  header.dataset.initialized = "true";

  const handleToggle = (e) => {
    if (e.target.closest(".rsp-card__actions") && !e.target.closest(".rsp-card__toggle")) return;
    const card = header.closest(".rsp-card");
    if (card && !card.classList.contains("rsp-card--read-only")) {
      rspToggleCard(card);
    }
  };

  header.addEventListener("click", handleToggle);
  header.addEventListener("keydown", (e) => {
    if (e.key === "Enter" || e.key === " ") {
      e.preventDefault();
      handleToggle(e);
    }
  });
});

// Permission checkbox change
Behaviour.specify(".rsp-perm__item input[type=checkbox]", "RoleStrategyCards", 0, (cb) => {
  if (cb.dataset.initialized === "true") return;
  cb.dataset.initialized = "true";

  cb.addEventListener("change", () => {
    const card = cb.closest(".rsp-card");
    if (card) {
      rspUpdateImplied(card);
      rspUpdateSummary(card);
      rspMarkDirty();
    }
  });
});

// Initialize summaries and implied on page load
Behaviour.specify(".rsp-card", "RoleStrategyCards", 1, (card) => {
  if (card.dataset.summaryInitialized === "true") return;
  card.dataset.summaryInitialized = "true";
  rspUpdateImplied(card);
  rspUpdateSummary(card);
});

// Delete role
Behaviour.specify(".rsp-card__delete", "RoleStrategyCards", 0, (btn) => {
  if (btn.dataset.initialized === "true") return;
  btn.dataset.initialized = "true";

  btn.addEventListener("click", (e) => {
    e.stopPropagation();
    const card = btn.closest(".rsp-card");
    if (!card) return;
    const roleName = card.dataset.roleName;

    dialog.confirm("Delete role", {
      message: `Are you sure you want to delete the role "${roleName}"?`,
      type: "destructive",
    }).then(() => {
      // Remove assignments for this role
      const container = card.closest(".rsp-container");
      const assignType = container.dataset.assignType;
      const json = rspAssignmentData[assignType];
      if (json) {
        json.forEach((entry) => {
          const idx = entry.roles.indexOf(roleName);
          if (idx !== -1) entry.roles.splice(idx, 1);
        });
      }
      card.remove();
      rspMarkDirty();
    }).catch(() => {});
  });
});

// Edit pattern
Behaviour.specify(".rsp-card__edit-pattern", "RoleStrategyCards", 0, (btn) => {
  if (btn.dataset.initialized === "true") return;
  btn.dataset.initialized = "true";

  btn.addEventListener("click", (e) => {
    e.stopPropagation();
    const card = btn.closest(".rsp-card");
    if (!card) return;
    const currentPattern = card.dataset.rolePattern || "";

    dialog.prompt("Edit pattern", {
      message: "Enter the new pattern:",
      defaultValue: currentPattern,
    }).then((newPattern) => {
      if (newPattern === null || newPattern === undefined) return;
      newPattern = newPattern.trim();
      if (!newPattern) return;

      card.dataset.rolePattern = newPattern;
      const patternSpan = card.querySelector(".rsp-card__pattern");
      if (patternSpan) {
        patternSpan.textContent = `"${newPattern}"`;
        patternSpan.dataset.pattern = newPattern;
      }
      rspMarkDirty();
    }).catch(() => {});
  });
});

// Pattern click - show matching items
Behaviour.specify(".rsp-card__pattern", "RoleStrategyCards", 0, (span) => {
  if (span.dataset.initialized === "true") return;
  span.dataset.initialized = "true";

  span.addEventListener("click", (e) => {
    // Don't trigger if we're clicking during expand/collapse
    if (e.target.closest(".rsp-card__actions")) return;
    e.stopPropagation();
    const pattern = span.dataset.pattern;
    const roleType = span.closest(".rsp-container")?.dataset.roleType;
    if (pattern && roleType) {
      rspShowMatchingItems(pattern, roleType);
    }
  });
});

// Add role button
Behaviour.specify(".rsp-add-role", "RoleStrategyCards", 0, (btn) => {
  if (btn.dataset.initialized === "true") return;
  btn.dataset.initialized = "true";

  btn.addEventListener("click", () => {
    const sectionId = btn.dataset.sectionId;
    const container = document.getElementById(sectionId);
    if (container) {
      rspAddNewRole(container);
    }
  });
});

// Add user/group assignment
Behaviour.specify(".rsp-assign__add", "RoleStrategyCards", 0, (btn) => {
  if (btn.dataset.initialized === "true") return;
  btn.dataset.initialized = "true";

  btn.addEventListener("click", () => {
    const type = btn.dataset.type;
    const card = btn.closest(".rsp-card");
    if (!card) return;

    const promptText = type === "USER" ? "Enter the user name:" : "Enter the group name:";
    dialog.prompt(promptText).then((name) => {
      if (!name || !name.trim()) return;
      name = name.trim();
      rspAddAssignmentToRole(card, name, type);
    });
  });
});

// Search bar
Behaviour.specify(".rsp-search input", "RoleStrategyCards", 0, (input) => {
  if (input.dataset.searchInitialized === "true") return;
  input.dataset.searchInitialized = "true";

  input.addEventListener("input", () => {
    const container = input.closest(".rsp-container");
    if (container) rspApplyFilters(container);
  });
});

// Filter button
Behaviour.specify(".rsp-filter__button", "RoleStrategyCards", 0, (btn) => {
  if (btn.dataset.filterInitialized === "true") return;
  btn.dataset.filterInitialized = "true";

  const filterEl = btn.parentElement;
  const dropdown = filterEl.querySelector(".rsp-filter__dropdown");
  if (!dropdown) return;
  const container = filterEl.closest(".rsp-container");

  // Filter item click
  dropdown.querySelectorAll(".rsp-filter__item").forEach((item) => {
    item.addEventListener("click", () => {
      item.classList.toggle("rsp-filter__item--active");
      if (container) rspApplyFilters(container);
    });
  });

  // Search within dropdown
  const filterSearchInput = dropdown.querySelector(".rsp-filter__search-input");
  const filterSearchClear = dropdown.querySelector(".rsp-filter__search-clear");

  const applyFilterSearch = () => {
    const q = filterSearchInput ? filterSearchInput.value.toLowerCase().trim() : "";
    const groupTitles = dropdown.querySelectorAll(".rsp-filter__group-title");

    dropdown.querySelectorAll(".rsp-filter__item").forEach((item) => {
      const label = (item.getAttribute("data-filter-label") || "").toLowerCase();
      item.classList.toggle("rsp-filter__item--filter-hidden", q !== "" && !label.includes(q));
    });

    groupTitles.forEach((title) => {
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

    if (filterSearchClear) {
      filterSearchClear.classList.toggle("rsp-filter__search-clear--visible", q !== "");
    }
    const noResults = dropdown.querySelector(".rsp-filter__no-results");
    if (noResults) {
      const hasAnyVisible = dropdown.querySelector(".rsp-filter__item:not(.rsp-filter__item--filter-hidden)");
      noResults.hidden = !!hasAnyVisible;
    }
  };

  if (filterSearchInput) {
    filterSearchInput.addEventListener("input", applyFilterSearch);
    filterSearchInput.addEventListener("click", (e) => e.stopPropagation());
  }

  if (filterSearchClear) {
    filterSearchClear.addEventListener("click", (e) => {
      e.stopPropagation();
      filterSearchInput.value = "";
      applyFilterSearch();
      filterSearchInput.focus();
    });
  }

  // Reset button
  const resetBtn = dropdown.querySelector(".rsp-filter__reset-button");
  if (resetBtn) {
    resetBtn.addEventListener("click", (e) => {
      e.preventDefault();
      dropdown.querySelectorAll(".rsp-filter__item--active").forEach((item) => {
        item.classList.remove("rsp-filter__item--active");
      });
      if (filterSearchInput) filterSearchInput.value = "";
      applyFilterSearch();
      if (container) rspApplyFilters(container);
    });
  }

  // Toggle dropdown
  btn.addEventListener("click", (e) => {
    e.stopPropagation();
    // Close all other open filter dropdowns
    document.querySelectorAll(".rsp-filter__dropdown").forEach((d) => {
      if (d !== dropdown) d.hidden = true;
    });
    document.querySelectorAll(".rsp-filter__button").forEach((b) => {
      if (b !== btn) b.setAttribute("aria-expanded", "false");
    });
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
        if (evt.key === "Escape") {
          closeDropdown();
          btn.focus();
        }
      };
      setTimeout(() => {
        document.addEventListener("click", clickHandler);
        document.addEventListener("keydown", escHandler);
      }, 0);
    }
  });
});

// Save button
Behaviour.specify("#rsp-save", "RoleStrategyCards", 0, (button) => {
  button.addEventListener("click", () => {
    rspSave(true).then(() => {
      window.location.reload();
    }).catch((err) => {
      notificationBar.show("Failed to save: " + err.message, notificationBar.ERROR);
    });
  });
});

// Apply button
Behaviour.specify("#rsp-apply", "RoleStrategyCards", 0, (button) => {
  button.addEventListener("click", () => {
    rspSave(false).then(() => {
      notificationBar.show(button.dataset.message, notificationBar.SUCCESS);
    }).catch((err) => {
      notificationBar.show("Failed to apply: " + err.message, notificationBar.ERROR);
    });
  });
});


// ============================================
// Initialization - eagerly load all assignment data
// ============================================

const rspLoadAllAssignments = () => {
  const dataHolder = document.getElementById("role-strategy-data");
  if (!dataHolder) return;

  const fetchUrl = dataHolder.dataset.fetchUrl;
  if (!fetchUrl) return;

  document.querySelectorAll(".rsp-container").forEach((container) => {
    const assignType = container.dataset.assignType;
    if (!assignType || rspAssignmentData[assignType]) return;

    const params = new URLSearchParams({ type: assignType });
    fetch(fetchUrl + "?" + params)
      .then((rsp) => rsp.json())
      .then((json) => {
        rspEnsureFixedEntry(json, "anonymous", "USER");
        rspEnsureFixedEntry(json, "authenticated", "GROUP");
        rspAssignmentData[assignType] = json;
      })
      .catch(() => {
        // Initialize with empty array to prevent save failures
        rspAssignmentData[assignType] = [];
      });
  });
};

document.addEventListener("DOMContentLoaded", rspLoadAllAssignments);

// ============================================
// Collapsible sections
// ============================================

Behaviour.specify(".rsp-container", "RoleStrategyCollapsible", 0, (container) => {
  const section = container.closest(".jenkins-section");
  if (!section || section.dataset.collapsibleInit === "true") return;
  section.dataset.collapsibleInit = "true";
  section.classList.add("rsp-section-collapsible");

  const title = section.querySelector(".jenkins-section__title");
  if (!title) return;

  title.addEventListener("click", () => {
    section.classList.toggle("rsp-section--collapsed");
  });
});
