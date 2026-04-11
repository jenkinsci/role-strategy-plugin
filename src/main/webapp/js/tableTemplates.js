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
// Summary
// ============================================

const tplBuildSummary = (card) => {
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
        const nameEl = label?.querySelector(".rsp-perm__item-name");
        if (nameEl) checked.push(nameEl.textContent.trim());
      }
    });
    if (checked.length > 0) parts.push(title.textContent.trim() + ": " + checked.join(", "));
  });
  return parts.join(" \u00B7 ");
};

const tplUpdateSummary = (card) => {
  const el = card.querySelector(".rsp-card__summary");
  if (!el) return;
  const summary = tplBuildSummary(card);
  if (summary) { el.textContent = summary; el.classList.remove("rsp-card__summary--empty"); }
  else { el.textContent = el.dataset.emptyText || "No permissions"; el.classList.add("rsp-card__summary--empty"); }
};

// ============================================
// Implied
// ============================================

const tplUpdateImplied = (card) => {
  card.querySelectorAll(".rsp-perm__item[data-permission-id]").forEach((label) => {
    const cb = label.querySelector("input[type=checkbox]");
    if (!cb) return;
    const impliedByStr = label.getAttribute("data-implied-by-list");
    if (!impliedByStr || !impliedByStr.trim()) return;
    let isImplied = false;
    for (const permId of impliedByStr.trim().split(" ")) {
      const ref = card.querySelector(`.rsp-perm__item[data-permission-id='${permId}'] input[type=checkbox]`);
      if (ref && ref.checked) { isImplied = true; break; }
    }
    const il = label.querySelector(".rsp-perm__item-implied");
    if (isImplied) { cb.checked = false; cb.disabled = true; label.classList.add("rsp-perm__item--implied"); if (il) il.hidden = false; }
    else { cb.disabled = false; label.classList.remove("rsp-perm__item--implied"); if (il) il.hidden = true; }
  });
};

// ============================================
// Save (used only for delete — add/edit use Jelly dialogs)
// ============================================

const tplCollectData = () => {
  const result = {};
  document.querySelectorAll("#rsp-template-cards .rsp-card").forEach((card) => {
    const name = card.dataset.templateName;
    const data = {};
    card.querySelectorAll(".rsp-perm__item input[type=checkbox]").forEach((cb) => {
      const permId = cb.getAttribute("data-permission-id");
      if (permId && cb.checked) data[permId] = true;
    });
    result[name] = data;
  });
  return result;
};

const tplSave = () => {
  const dataHolder = document.getElementById("template-data");
  const submitData = { permissionTemplates: { data: tplCollectData() } };
  const formData = new FormData();
  formData.append("json", JSON.stringify(submitData));
  return fetch(dataHolder.dataset.submitUrl, {
    method: "POST",
    headers: crumb.wrap({}),
    body: formData,
  }).then((rsp) => { if (!rsp.ok) throw new Error("Failed to save"); });
};

// ============================================
// Search + Filter
// ============================================

let tplActivePermFilters = [];

const tplApplyFilters = () => {
  const searchInput = document.querySelector(".rsp-template-search input");
  const query = searchInput ? searchInput.value.toLowerCase().trim() : "";
  const cards = document.querySelectorAll("#rsp-template-cards .rsp-card");
  let visibleCount = 0;

  cards.forEach((card) => {
    const name = (card.dataset.templateName || "").toLowerCase();
    const matchesText = query === "" || name.includes(query);

    let matchesPerm = tplActivePermFilters.length === 0;
    if (!matchesPerm) {
      for (const permId of tplActivePermFilters) {
        const cb = card.querySelector(`.rsp-perm__item[data-permission-id='${permId}'] input[type=checkbox]`);
        if (cb && (cb.checked || cb.disabled)) { matchesPerm = true; break; }
      }
    }

    if (matchesText && matchesPerm) { card.classList.remove("rsp-card--hidden"); visibleCount++; }
    else { card.classList.add("rsp-card--hidden"); }
  });

  const empty = document.getElementById("rsp-template-empty");
  if (empty) empty.hidden = visibleCount > 0 || (query === "" && tplActivePermFilters.length === 0);

  const filterBtn = document.querySelector(".rsp-tpl-filter-btn");
  if (filterBtn) {
    const active = tplActivePermFilters.length > 0;
    filterBtn.classList.toggle("jenkins-button--tertiary", !active);
    filterBtn.classList.toggle("jenkins-!-accent-color", active);
  }
  const resetBtn = document.querySelector(".rsp-tpl-filter-reset");
  if (resetBtn) resetBtn.hidden = tplActivePermFilters.length === 0;

  rspUpdateCardBorders();
};

// ============================================
// Dialog helpers (shared with tableRoles.js pattern)
// ============================================

const tplDialogUpdateImplied = (container) => {
  container.querySelectorAll(".rsp-assign-dialog__role-item[data-implied-by-list]").forEach((item) => {
    const cb = item.querySelector("input[type=checkbox]");
    if (!cb) return;
    const impliedByStr = item.getAttribute("data-implied-by-list");
    if (!impliedByStr || !impliedByStr.trim()) return;
    let isImplied = false;
    for (const permId of impliedByStr.trim().split(" ")) {
      const ref = container.querySelector(`.rsp-assign-dialog__role-item[data-permission-id='${permId}'] input[type=checkbox]`);
      if (ref && ref.checked && !ref.disabled) { isImplied = true; break; }
    }
    const impliedLabel = item.querySelector(".rsp-implied-label");
    if (isImplied) {
      cb.checked = true;
      cb.disabled = true;
      item.dataset.implied = "true";
      if (impliedLabel) impliedLabel.hidden = false;
    } else {
      if (item.dataset.implied === "true") {
        cb.checked = false;
        cb.disabled = false;
        item.dataset.implied = "false";
      }
      if (impliedLabel) impliedLabel.hidden = true;
    }
  });
};

const tplDialogUncheckImplied = (container) => {
  container.querySelectorAll(".rsp-assign-dialog__role-item[data-implied='true'] input[type=checkbox]").forEach((cb) => {
    cb.checked = false;
  });
};

const tplDialogAttachImplied = (container) => {
  container.querySelectorAll(".rsp-assign-dialog__role-item input[type=checkbox]").forEach((cb) => {
    cb.addEventListener("change", () => tplDialogUpdateImplied(container));
  });
  tplDialogUpdateImplied(container);
};

const tplDialogApplyFilter = (form) => {
  const filterEl = form.querySelector(".rsp-perm-dialog-filter input");
  const q = filterEl ? filterEl.value.toLowerCase().trim() : "";
  const permContainer = form.querySelector("[name='permissions']");
  if (!permContainer) return;

  let visibleCount = 0;
  permContainer.querySelectorAll(".rsp-assign-dialog__role-item").forEach((item) => {
    const match = q === "" || (item.dataset.roleName || "").toLowerCase().includes(q);
    item.style.display = match ? "" : "none";
    if (match) visibleCount++;
  });
  permContainer.querySelectorAll(".rsp-assign-dialog__group-title").forEach((title) => {
    let next = title.nextElementSibling;
    let hasVisible = false;
    while (next && !next.classList.contains("rsp-assign-dialog__group-title")) {
      if (next.classList.contains("rsp-assign-dialog__group")) {
        next.querySelectorAll(".rsp-assign-dialog__role-item").forEach((child) => { if (child.style.display !== "none") hasVisible = true; });
      }
      next = next.nextElementSibling;
    }
    title.style.display = hasVisible ? "" : "none";
  });
  const noResults = permContainer.querySelector(".rsp-assign-dialog__no-results");
  if (noResults) noResults.classList.toggle("jenkins-hidden", visibleCount > 0 || q === "");
};

const tplInitDialog = (form, submitBtnId) => {
  if (form.dataset.dialogInit === "true") return;
  form.dataset.dialogInit = "true";

  const validateAndSubmit = () => {
    const nameInput = form.querySelector("input[name='templateName']");
    if (nameInput && !nameInput.disabled) {
      const name = nameInput.value.trim();
      if (!name) {
        nameInput.focus();
        nameInput.style.outline = "2px solid var(--error-color)";
        nameInput.addEventListener("input", () => { nameInput.style.outline = ""; }, { once: true });
        return;
      }
      // Check for duplicate
      const existing = document.querySelector(`#rsp-template-cards .rsp-card[data-template-name='${CSS.escape(name)}']`);
      if (existing) {
        nameInput.focus();
        nameInput.style.outline = "2px solid var(--error-color)";
        nameInput.addEventListener("input", () => { nameInput.style.outline = ""; }, { once: true });
        dialog.alert(`A template named "${name}" already exists.`);
        return;
      }
    }

    // Check at least one permission selected
    const permContainer = form.querySelector("[name='permissions']");
    if (permContainer) {
      const hasChecked = permContainer.querySelector("input[type='checkbox']:checked:not(:disabled)");
      if (!hasChecked) {
        dialog.alert("Please select at least one permission.");
        return;
      }
      tplDialogUncheckImplied(permContainer);
    }

    form.requestSubmit();
  };

  const filterInput = form.querySelector(".rsp-perm-dialog-filter input");
  if (filterInput) {
    filterInput.addEventListener("input", () => tplDialogApplyFilter(form));
  }

  const permContainer = form.querySelector("[name='permissions']");
  if (permContainer) tplDialogAttachImplied(permContainer);

  const submitBtn = form.querySelector(`#${submitBtnId}`);
  if (submitBtn) submitBtn.addEventListener("click", validateAndSubmit);
  form.addEventListener("keydown", (e) => { if (e.key === "Enter") { e.preventDefault(); validateAndSubmit(); } });
};

// ============================================
// Behaviours
// ============================================

// Card toggle — view only, don't expand empty cards
Behaviour.specify("#rsp-template-cards .rsp-card__header", "RoleStrategyTemplates", 0, (header) => {
  if (header.dataset.initialized === "true") return;
  header.dataset.initialized = "true";
  const handleToggle = (e) => {
    if (e.target.closest(".rsp-card__actions") && !e.target.closest(".rsp-card__toggle")) return;
    const card = header.closest(".rsp-card");
    if (!card) return;
    const summary = card.querySelector(".rsp-card__summary");
    if (summary && summary.classList.contains("rsp-card__summary--empty")) return;
    const body = card.querySelector(".rsp-card__body");
    const isExpanded = !body.classList.contains("rsp-card__body--collapsed");
    body.classList.toggle("rsp-card__body--collapsed");
    header.setAttribute("aria-expanded", String(!isExpanded));
    card.setAttribute("aria-expanded", String(!isExpanded));
  };
  header.addEventListener("click", handleToggle);
  header.addEventListener("keydown", (e) => { if (e.key === "Enter" || e.key === " ") { e.preventDefault(); handleToggle(e); } });
});

// Permission checkboxes in card body — view only
Behaviour.specify("#rsp-template-cards .rsp-perm__item input[type=checkbox]", "RoleStrategyTemplates", 0, (cb) => {
  if (cb.dataset.initialized === "true") return;
  cb.dataset.initialized = "true";
  cb.addEventListener("change", () => {
    const card = cb.closest(".rsp-card");
    if (card) { tplUpdateImplied(card); tplUpdateSummary(card); }
  });
});

// Init summaries
Behaviour.specify("#rsp-template-cards .rsp-card", "RoleStrategyTemplates", 1, (card) => {
  if (card.dataset.summaryInitialized === "true") return;
  card.dataset.summaryInitialized = "true";
  tplUpdateImplied(card);
  tplUpdateSummary(card);
  rspUpdateCardBorders();
});

// Delete template
Behaviour.specify(".rsp-template-delete", "RoleStrategyTemplates", 0, (btn) => {
  if (btn.dataset.initialized === "true") return;
  btn.dataset.initialized = "true";
  btn.addEventListener("click", (e) => {
    e.stopPropagation();
    const card = btn.closest(".rsp-card");
    if (!card) return;
    const name = card.dataset.templateName;
    const inUse = card.dataset.inUse === "true";

    const msg = inUse
      ? `Template "${name}" is in use by roles. Are you sure you want to delete it?`
      : `Delete template "${name}"?`;

    dialog.confirm("Delete template", { message: msg, type: "destructive" }).then(() => {
      card.remove();
      rspUpdateCardBorders();
      tplSave().then(() => {
        if (document.querySelectorAll("#rsp-template-cards .rsp-card").length === 0) {
          window.location.reload();
        } else {
          notificationBar.show(`Template "${name}" deleted`, notificationBar.SUCCESS);
        }
      }).catch((err) => { notificationBar.show("Failed to save: " + err.message, notificationBar.ERROR); });
    }).catch(() => {});
  });
});

// Edit template — Jelly dialog
Behaviour.specify(".rsp-template-edit", "RoleStrategyTemplates", 0, (btn) => {
  if (btn.dataset.initialized === "true") return;
  btn.dataset.initialized = "true";
  btn.addEventListener("click", (e) => {
    e.stopPropagation();
    const card = btn.closest(".rsp-card");
    if (!card) return;
    const templateName = card.dataset.templateName;
    const rootUrl = document.querySelector("[data-rooturl]")?.getAttribute("data-rooturl") || "";
    const dialogUrl = `${rootUrl}/manage/role-strategy/edit-template-dialog?templateName=${encodeURIComponent(templateName)}`;
    dialog.wizard(dialogUrl);
    const initDialog = () => {
      const form = document.querySelector("form[name='edit-template']");
      if (!form) { setTimeout(initDialog, 100); return; }
      tplInitDialog(form, "rsp-edit-template-submit-btn");
    };
    setTimeout(initDialog, 200);
  });
});

// Add template — Jelly dialog
Behaviour.specify(".rsp-add-template-btn", "RoleStrategyTemplates", 0, (btn) => {
  if (btn.dataset.initialized === "true") return;
  btn.dataset.initialized = "true";
  btn.addEventListener("click", () => {
    const rootUrl = document.querySelector("[data-rooturl]")?.getAttribute("data-rooturl") || "";
    dialog.wizard(rootUrl + "/manage/role-strategy/add-template-dialog");
    const initDialog = () => {
      const form = document.querySelector("form[name='add-template']");
      if (!form) { setTimeout(initDialog, 100); return; }
      tplInitDialog(form, "rsp-add-template-submit-btn");
    };
    setTimeout(initDialog, 200);
  });
});

// Search
Behaviour.specify(".rsp-template-search input", "RoleStrategyTemplates", 0, (input) => {
  if (input.dataset.initialized === "true") return;
  input.dataset.initialized = "true";
  input.addEventListener("input", tplApplyFilters);
});

// Permission filter dropdown
Behaviour.specify(".rsp-tpl-filter-btn", "RoleStrategyTemplates", 0, (btn) => {
  if (btn.dataset.initialized === "true") return;
  btn.dataset.initialized = "true";

  const dropdown = document.querySelector(".rsp-tpl-filter-dropdown");
  if (!dropdown) return;

  const searchInput = dropdown.querySelector(".rsp-tpl-filter-search input");
  const resetBtn = dropdown.querySelector(".rsp-tpl-filter-reset");

  dropdown.querySelectorAll(".jenkins-dropdown__item").forEach((item) => {
    item.addEventListener("click", () => {
      item.classList.toggle("rsp-filter__item--active");
      tplActivePermFilters = [];
      dropdown.querySelectorAll(".rsp-filter__item--active").forEach((a) => {
        tplActivePermFilters.push(a.dataset.filterPermission);
      });
      tplApplyFilters();
    });
  });

  const applyFilterSearch = () => {
    const q = searchInput ? searchInput.value.toLowerCase().trim() : "";
    dropdown.querySelectorAll(".jenkins-dropdown__item").forEach((item) => {
      const label = (item.dataset.filterLabel || "").toLowerCase();
      item.classList.toggle("rsp-filter__item--filter-hidden", q !== "" && !label.includes(q));
    });
    dropdown.querySelectorAll(".rsp-filter__group-title").forEach((title) => {
      let next = title.nextElementSibling;
      let hasVisible = false;
      while (next && !next.classList.contains("rsp-filter__group-title")) {
        if (next.classList.contains("jenkins-dropdown__item") && !next.classList.contains("rsp-filter__item--filter-hidden")) hasVisible = true;
        next = next.nextElementSibling;
      }
      title.classList.toggle("rsp-filter__group-title--filter-hidden", !hasVisible);
    });
    const noResults = dropdown.querySelector(".rsp-filter__no-results");
    if (noResults) noResults.hidden = !!dropdown.querySelector(".jenkins-dropdown__item:not(.rsp-filter__item--filter-hidden)");
  };

  if (searchInput) { searchInput.addEventListener("input", applyFilterSearch); searchInput.addEventListener("click", (e) => e.stopPropagation()); }
  if (resetBtn) {
    resetBtn.addEventListener("click", (e) => {
      e.preventDefault();
      dropdown.querySelectorAll(".rsp-filter__item--active").forEach((item) => item.classList.remove("rsp-filter__item--active"));
      tplActivePermFilters = [];
      if (searchInput) searchInput.value = "";
      applyFilterSearch();
      tplApplyFilters();
    });
  }

  btn.addEventListener("click", (e) => {
    e.stopPropagation();
    const isOpen = !dropdown.hidden;
    dropdown.hidden = isOpen;
    btn.setAttribute("aria-expanded", String(!isOpen));
    if (!isOpen) {
      const closeDropdown = () => { dropdown.hidden = true; btn.setAttribute("aria-expanded", "false"); document.removeEventListener("click", ch); document.removeEventListener("keydown", eh); };
      const ch = (evt) => { if (!dropdown.contains(evt.target) && evt.target !== btn) closeDropdown(); };
      const eh = (evt) => { if (evt.key === "Escape") { closeDropdown(); btn.focus(); } };
      setTimeout(() => { document.addEventListener("click", ch); document.addEventListener("keydown", eh); }, 0);
    }
  });
});
