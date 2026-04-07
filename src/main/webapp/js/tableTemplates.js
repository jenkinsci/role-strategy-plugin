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
// Save
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

let tplAutoSaveTimer = null;
const tplAutoSave = () => {
  if (tplAutoSaveTimer) clearTimeout(tplAutoSaveTimer);
  tplAutoSaveTimer = setTimeout(() => {
    tplSave().catch((err) => { notificationBar.show("Failed to save: " + err.message, notificationBar.ERROR); });
  }, 1000);
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
// Behaviours
// ============================================

// Card toggle
Behaviour.specify("#rsp-template-cards .rsp-card__header", "RoleStrategyTemplates", 0, (header) => {
  if (header.dataset.initialized === "true") return;
  header.dataset.initialized = "true";
  const handleToggle = (e) => {
    if (e.target.closest(".rsp-card__actions") && !e.target.closest(".rsp-card__toggle")) return;
    const card = header.closest(".rsp-card");
    if (!card || card.classList.contains("rsp-card--read-only")) return;
    const body = card.querySelector(".rsp-card__body");
    const isExpanded = !body.classList.contains("rsp-card__body--collapsed");
    body.classList.toggle("rsp-card__body--collapsed");
    header.setAttribute("aria-expanded", String(!isExpanded));
    card.setAttribute("aria-expanded", String(!isExpanded));
  };
  header.addEventListener("click", handleToggle);
  header.addEventListener("keydown", (e) => { if (e.key === "Enter" || e.key === " ") { e.preventDefault(); handleToggle(e); } });
});

// Permission checkbox
Behaviour.specify("#rsp-template-cards .rsp-perm__item input[type=checkbox]", "RoleStrategyTemplates", 0, (cb) => {
  if (cb.dataset.initialized === "true") return;
  cb.dataset.initialized = "true";
  cb.addEventListener("change", () => {
    const card = cb.closest(".rsp-card");
    if (card) { tplUpdateImplied(card); tplUpdateSummary(card); tplAutoSave(); }
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
        // Reload if last template was deleted to show empty state
        if (document.querySelectorAll("#rsp-template-cards .rsp-card").length === 0) {
          window.location.reload();
        } else {
          notificationBar.show(`Template "${name}" deleted`, notificationBar.SUCCESS);
        }
      }).catch((err) => { notificationBar.show("Failed to save: " + err.message, notificationBar.ERROR); });
    }).catch(() => {});
  });
});

// Add template button
Behaviour.specify(".rsp-add-template-btn", "RoleStrategyTemplates", 0, (btn) => {
  if (btn.dataset.initialized === "true") return;
  btn.dataset.initialized = "true";

  btn.addEventListener("click", () => {
    const content = document.createElement("div");
    content.classList.add("rsp-dialog-content");

    const nameGroup = document.createElement("div");
    nameGroup.classList.add("jenkins-form-item");
    nameGroup.innerHTML = `
      <label class="jenkins-form-label">Template name</label>
      <div class="jenkins-form-item__control">
        <input type="text" class="jenkins-input" id="rsp-dialog-template-name" autofocus />
      </div>
    `;
    content.appendChild(nameGroup);

    // Clone permissions from an existing card
    let permClone = null;
    const existingCard = document.querySelector("#rsp-template-cards .rsp-card");
    if (existingCard) {
      const permSection = existingCard.querySelector(".rsp-perm");
      if (permSection) {
        permClone = permSection.cloneNode(true);
        permClone.querySelectorAll("input[type=checkbox]").forEach((cb) => {
          cb.checked = false; cb.disabled = false; cb.removeAttribute("data-initialized");
        });
        permClone.querySelectorAll(".rsp-perm__item--implied").forEach((el) => el.classList.remove("rsp-perm__item--implied"));
        permClone.querySelectorAll(".rsp-perm__item-implied").forEach((el) => { el.hidden = true; });

        const permWrapper = document.createElement("div");
        permWrapper.classList.add("jenkins-form-item");
        const permTitle = document.createElement("label");
        permTitle.classList.add("jenkins-form-label");
        permTitle.textContent = "Permissions";
        permWrapper.appendChild(permTitle);
        permWrapper.appendChild(permClone);
        content.appendChild(permWrapper);

        // Implied logic in dialog
        const updateImplied = () => {
          permClone.querySelectorAll(".rsp-perm__item[data-permission-id]").forEach((label) => {
            const innerCb = label.querySelector("input[type=checkbox]");
            if (!innerCb) return;
            const impliedByStr = label.getAttribute("data-implied-by-list");
            if (!impliedByStr || !impliedByStr.trim()) return;
            let isImplied = false;
            for (const pid of impliedByStr.trim().split(" ")) {
              const ref = permClone.querySelector(`.rsp-perm__item[data-permission-id='${pid}'] input[type=checkbox]`);
              if (ref && ref.checked) { isImplied = true; break; }
            }
            const il = label.querySelector(".rsp-perm__item-implied");
            if (isImplied) { innerCb.checked = false; innerCb.disabled = true; label.classList.add("rsp-perm__item--implied"); if (il) il.hidden = false; }
            else { innerCb.disabled = false; label.classList.remove("rsp-perm__item--implied"); if (il) il.hidden = true; }
          });
        };
        permClone.querySelectorAll("input[type=checkbox]").forEach((cb) => cb.addEventListener("change", updateImplied));
      }
    }

    const dlg = document.createElement("dialog");
    dlg.classList.add("jenkins-dialog");
    dlg.style.cssText = "max-width:550px;min-width:450px;";

    const titleBar = document.createElement("div");
    titleBar.classList.add("jenkins-dialog__title");
    titleBar.innerHTML = `<span>Add Template</span>
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

    document.body.appendChild(dlg);
    dlg.showModal();

    const closeDialog = () => { dlg.close(); dlg.remove(); };
    dlg.querySelectorAll("[data-id='cancel']").forEach((b) => b.addEventListener("click", closeDialog));
    dlg.addEventListener("cancel", closeDialog);

    dlg.querySelector("[data-id='ok']").addEventListener("click", () => {
      const nameInput = dlg.querySelector("#rsp-dialog-template-name");
      const name = nameInput?.value?.trim();
      if (!name) { nameInput?.focus(); return; }

      const existing = document.querySelector(`#rsp-template-cards .rsp-card[data-template-name='${CSS.escape(name)}']`);
      if (existing) { closeDialog(); dialog.alert(`A template named "${name}" already exists.`); return; }

      // Create hidden card for save
      const cards = document.getElementById("rsp-template-cards");
      const card = document.createElement("div");
      card.classList.add("rsp-card");
      card.style.display = "none";
      card.dataset.templateName = name;
      if (permClone) {
        dlg.querySelectorAll(".rsp-perm__item input[type=checkbox]:checked").forEach((cb) => {
          const permId = cb.getAttribute("data-permission-id");
          if (permId) {
            const label = document.createElement("label");
            label.classList.add("rsp-perm__item");
            const input = document.createElement("input");
            input.type = "checkbox";
            input.checked = true;
            input.setAttribute("data-permission-id", permId);
            label.appendChild(input);
            card.appendChild(label);
          }
        });
      }
      cards.appendChild(card);

      closeDialog();
      tplSave().then(() => { window.location.reload(); }).catch((err) => {
        card.remove();
        notificationBar.show("Failed to save: " + err.message, notificationBar.ERROR);
      });
    });
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
  const clearBtn = dropdown.querySelector(".rsp-tpl-filter-clear");
  const resetBtn = dropdown.querySelector(".rsp-tpl-filter-reset");

  // Filter items click
  dropdown.querySelectorAll(".rsp-filter__item").forEach((item) => {
    item.addEventListener("click", () => {
      item.classList.toggle("rsp-filter__item--active");
      tplActivePermFilters = [];
      dropdown.querySelectorAll(".rsp-filter__item--active").forEach((a) => {
        tplActivePermFilters.push(a.dataset.filterPermission);
      });
      tplApplyFilters();
    });
  });

  // Search within dropdown
  const applyFilterSearch = () => {
    const q = searchInput ? searchInput.value.toLowerCase().trim() : "";
    dropdown.querySelectorAll(".rsp-filter__item").forEach((item) => {
      const label = (item.dataset.filterLabel || "").toLowerCase();
      item.classList.toggle("rsp-filter__item--filter-hidden", q !== "" && !label.includes(q));
    });
    dropdown.querySelectorAll(".rsp-filter__group-title").forEach((title) => {
      let next = title.nextElementSibling;
      let hasVisible = false;
      while (next && !next.classList.contains("rsp-filter__group-title")) {
        if (next.classList.contains("rsp-filter__item") && !next.classList.contains("rsp-filter__item--filter-hidden")) hasVisible = true;
        next = next.nextElementSibling;
      }
      title.classList.toggle("rsp-filter__group-title--filter-hidden", !hasVisible);
    });
    if (clearBtn) clearBtn.classList.toggle("rsp-filter__search-clear--visible", q !== "");
    const noResults = dropdown.querySelector(".rsp-filter__no-results");
    if (noResults) noResults.hidden = !!dropdown.querySelector(".rsp-filter__item:not(.rsp-filter__item--filter-hidden)");
  };

  if (searchInput) { searchInput.addEventListener("input", applyFilterSearch); searchInput.addEventListener("click", (e) => e.stopPropagation()); }
  if (clearBtn) { clearBtn.addEventListener("click", (e) => { e.stopPropagation(); searchInput.value = ""; applyFilterSearch(); searchInput.focus(); }); }
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

  // Toggle
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
