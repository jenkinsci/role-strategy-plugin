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
// Card expand/collapse
// ============================================

const rspToggleCard = (card) => {
  const body = card.querySelector(".rsp-card__body");
  const header = card.querySelector(".rsp-card__header");
  if (!body || !header) return;
  const isExpanded = !body.classList.contains("rsp-card__body--collapsed");

  // Lazy-load: move content from <template> into body on first expand
  if (!isExpanded && body.dataset.lazy === "true") {
    body.dataset.lazy = "false";
    const tmpl = card.querySelector("template.rsp-card__lazy-content");
    if (tmpl) {
      body.appendChild(tmpl.content.cloneNode(true));
      tmpl.remove();
      Behaviour.applySubtree(body, true);
      rspUpdateImplied(card);
      rspUpdateSummary(card);
    }
  }

  body.classList.toggle("rsp-card__body--collapsed");
  header.setAttribute("aria-expanded", String(!isExpanded));
  card.setAttribute("aria-expanded", String(!isExpanded));
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
        const nameEl = label
          ? label.querySelector(".rsp-perm__item-name")
          : null;
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
    summaryEl.textContent =
      summaryEl.getAttribute("data-empty-text") || "No permissions";
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
    if (label.closest(".rsp-card[data-template-name]")?.dataset.templateName)
      return;

    const impliedByStr = label.getAttribute("data-implied-by-list");
    if (!impliedByStr || impliedByStr.trim() === "") return;
    const impliedByList = impliedByStr.trim().split(" ");
    let isImplied = false;
    for (const permId of impliedByList) {
      const ref = card.querySelector(
        `.rsp-perm__item[data-permission-id='${permId}'] input[type=checkbox]`,
      );
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
    const pattern = (
      card.getAttribute("data-role-pattern") || ""
    ).toLowerCase();
    const matchesText =
      query === "" || roleName.includes(query) || pattern.includes(query);

    let matchesPermission = activePermissions.length === 0;
    if (!matchesPermission) {
      for (const permId of activePermissions) {
        const cb = card.querySelector(
          `.rsp-perm__item[data-permission-id='${permId}'] input[type=checkbox]`,
        );
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
    emptyState.hidden =
      visibleCount > 0 || (query === "" && activePermissions.length === 0);
  }
  const hasActiveFilters = activePermissions.length > 0;
  const filterBtn = container.querySelector(".rsp-filter__button");
  if (filterBtn) {
    filterBtn.classList.toggle("jenkins-button--tertiary", !hasActiveFilters);
    filterBtn.classList.toggle("jenkins-!-accent-color", hasActiveFilters);
  }
  const resetBtn = container.querySelector(".rsp-filter__reset-button");
  if (resetBtn) resetBtn.hidden = !hasActiveFilters;
  rspUpdateCardBorders(container);
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
      const pattern = card.dataset.rolePattern;
      if (pattern !== undefined && pattern !== "") roleData.pattern = pattern;
      const templateName = card.dataset.templateName;
      if (templateName) roleData.templateName = templateName;

      card
        .querySelectorAll(".rsp-perm__item input[type=checkbox]")
        .forEach((cb) => {
          const permId = cb.getAttribute("data-permission-id");
          if (permId && cb.checked) roleData[permId] = true;
        });
      roles[roleName] = roleData;
    });
    result[roleType] = { data: roles };
  });
  return result;
};

const rspSaveRoles = () => {
  const dataHolder = document.getElementById("role-strategy-data");
  const rolesData = rspCollectRolesData();
  const formData = new FormData();
  formData.append("json", JSON.stringify(rolesData));
  return fetch(dataHolder.dataset.rolesSubmitUrl, {
    method: "POST",
    headers: crumb.wrap({}),
    body: formData,
  }).then((rsp) => {
    if (!rsp.ok) throw new Error("Failed to save roles");
  });
};

let rspAutoSaveTimer = null;
const rspAutoSave = () => {
  if (rspAutoSaveTimer) clearTimeout(rspAutoSaveTimer);
  rspAutoSaveTimer = setTimeout(() => {
    rspSaveRoles().catch((err) => {
      notificationBar.show(
        "Failed to save: " + err.message,
        notificationBar.ERROR,
      );
    });
  }, 1000);
};

// ============================================
// Pattern matching (show matching jobs/agents)
// ============================================

const rspShowMatchingItems = (pattern, roleType) => {
  const dataHolder = document.getElementById("role-strategy-data");
  const isAgent = roleType === "slaveRoles";
  const url = isAgent
    ? dataHolder.dataset.matchingAgentsUrl
    : dataHolder.dataset.matchingJobsUrl;
  const maxItems = isAgent ? 10 : 15;
  const paramKey = isAgent ? "maxAgents" : "maxJobs";
  const params = { pattern, [paramKey]: maxItems };

  fetch(url + toQueryString(params))
    .then((rsp) => (rsp.ok ? rsp.json() : Promise.reject()))
    .then((json) => {
      const items = isAgent ? json.matchingAgents : json.matchingJobs;
      const count = json.itemCount;
      if (items == null) {
        dialog.alert("Unable to fetch matching items.");
        return;
      }
      let title =
        items.length > 0
          ? count > items.length
            ? `First ${maxItems} items (out of ${count}) matching`
            : "Items matching"
          : "No items found matching";
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

  const content = document.createElement("div");
  content.classList.add("rsp-dialog-content");

  const nameGroup = document.createElement("div");
  nameGroup.classList.add("jenkins-form-item");
  nameGroup.innerHTML = `
    <label class="jenkins-form-label">Role name</label>
    <div class="jenkins-form-item__control">
      <input type="text" class="jenkins-input" id="rsp-dialog-role-name" autofocus />
    </div>
  `;
  content.appendChild(nameGroup);

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

  if (isProject) {
    const templateStr = container.dataset.templates || "";
    const templateNames = templateStr
      ? templateStr
          .split(",")
          .map((s) => s.trim())
          .filter(Boolean)
      : [];
    if (templateNames.length > 0) {
      const templateGroup = document.createElement("div");
      templateGroup.classList.add("jenkins-form-item");
      const options = templateNames
        .map(
          (n) => `<option value="${escapeHTML(n)}">${escapeHTML(n)}</option>`,
        )
        .join("");
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
      permClone
        .querySelectorAll(".rsp-perm__item--implied")
        .forEach((el) => el.classList.remove("rsp-perm__item--implied"));
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

      const updateDialogImplied = () => {
        permClone
          .querySelectorAll(".rsp-perm__item[data-permission-id]")
          .forEach((label) => {
            const innerCb = label.querySelector("input[type=checkbox]");
            if (!innerCb) return;
            const impliedByStr = label.getAttribute("data-implied-by-list");
            if (!impliedByStr || impliedByStr.trim() === "") return;
            const impliedByList = impliedByStr.trim().split(" ");
            let isImplied = false;
            for (const permId of impliedByList) {
              const ref = permClone.querySelector(
                `.rsp-perm__item[data-permission-id='${permId}'] input[type=checkbox]`,
              );
              if (ref && ref.checked) {
                isImplied = true;
                break;
              }
            }
            const il = label.querySelector(".rsp-perm__item-implied");
            if (isImplied) {
              innerCb.checked = false;
              innerCb.disabled = true;
              label.classList.add("rsp-perm__item--implied");
              if (il) il.hidden = false;
            } else {
              innerCb.disabled = false;
              label.classList.remove("rsp-perm__item--implied");
              if (il) il.hidden = true;
            }
          });
      };
      permClone
        .querySelectorAll("input[type=checkbox]")
        .forEach((cb) => cb.addEventListener("change", updateDialogImplied));
    }
  }

  const templateSelect = content.querySelector("#rsp-dialog-role-template");
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
  footer.classList.add(
    "jenkins-dialog__footer",
    "jenkins-buttons-row",
    "jenkins-buttons-row--equal-width",
  );
  footer.innerHTML = `
    <button class="jenkins-button" data-id="cancel">Cancel</button>
    <button class="jenkins-button jenkins-button--primary" data-id="ok">Add</button>
  `;
  dlg.appendChild(footer);

  document.body.appendChild(dlg);
  dlg.showModal();

  const closeDialog = () => {
    dlg.close();
    dlg.remove();
  };
  dlg
    .querySelectorAll("[data-id='cancel']")
    .forEach((btn) => btn.addEventListener("click", closeDialog));
  dlg.addEventListener("cancel", closeDialog);

  dlg.querySelector("[data-id='ok']").addEventListener("click", () => {
    const nameInput = dlg.querySelector("#rsp-dialog-role-name");
    const name = nameInput?.value?.trim();
    if (!name) {
      nameInput?.focus();
      return;
    }

    const existing = container.querySelector(
      `.rsp-card[data-role-name='${CSS.escape(name)}']`,
    );
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

    let templateName = "";
    const tmplSelect = dlg.querySelector("#rsp-dialog-role-template");
    if (tmplSelect) templateName = tmplSelect.value;

    const selectedPermissions = new Set();
    dlg
      .querySelectorAll(".rsp-perm__item input[type=checkbox]:checked")
      .forEach((cb) => {
        const permId = cb.getAttribute("data-permission-id");
        if (permId) selectedPermissions.add(permId);
      });

    closeDialog();
    rspAddRoleAndSave(
      container,
      name,
      pattern,
      templateName,
      selectedPermissions,
    );
  });
};

// Add role data to DOM (hidden) and save immediately, then reload
const rspAddRoleAndSave = (
  container,
  name,
  pattern,
  templateName,
  permissions,
) => {
  // Create a hidden card with the right data so rspCollectRolesData picks it up
  const cardsContainer = container.querySelector(".rsp-cards");
  const card = document.createElement("div");
  card.classList.add("rsp-card");
  card.style.display = "none";
  card.dataset.roleName = name;
  card.dataset.rolePattern = pattern;
  card.dataset.templateName = templateName;

  // Add permission checkboxes as hidden checked inputs
  permissions.forEach((permId) => {
    const label = document.createElement("label");
    label.classList.add("rsp-perm__item");
    const cb = document.createElement("input");
    cb.type = "checkbox";
    cb.checked = true;
    cb.setAttribute("data-permission-id", permId);
    label.appendChild(cb);
    card.appendChild(label);
  });

  cardsContainer.appendChild(card);

  rspSaveRoles()
    .then(() => {
      window.location.reload();
    })
    .catch((err) => {
      card.remove();
      notificationBar.show(
        "Failed to save role: " + err.message,
        notificationBar.ERROR,
      );
    });
};

// ============================================
// Behaviours
// ============================================

Behaviour.specify(".rsp-card__header", "RoleStrategyRoles", 0, (header) => {
  if (header.dataset.initialized === "true") return;
  header.dataset.initialized = "true";
  const handleToggle = (e) => {
    if (
      e.target.closest(".rsp-card__actions") &&
      !e.target.closest(".rsp-card__toggle")
    )
      return;
    const card = header.closest(".rsp-card");
    if (!card) return;
    // Don't expand cards with no permissions
    const summary = card.querySelector(".rsp-card__summary");
    if (summary && summary.classList.contains("rsp-card__summary--empty"))
      return;
    rspToggleCard(card);
  };
  header.addEventListener("click", handleToggle);
  header.addEventListener("keydown", (e) => {
    if (e.key === "Enter" || e.key === " ") {
      e.preventDefault();
      handleToggle(e);
    }
  });
});

Behaviour.specify(
  ".rsp-perm__item input[type=checkbox]",
  "RoleStrategyRoles",
  0,
  (cb) => {
    if (cb.dataset.initialized === "true") return;
    cb.dataset.initialized = "true";
    cb.addEventListener("change", () => {
      const card = cb.closest(".rsp-card");
      if (card) {
        rspUpdateImplied(card);
        rspUpdateSummary(card);
      }
    });
  },
);

Behaviour.specify(".rsp-card", "RoleStrategyRoles", 1, (card) => {
  if (card.dataset.summaryInitialized === "true") return;
  card.dataset.summaryInitialized = "true";

  const body = card.querySelector(".rsp-card__body");
  if (body && body.dataset.lazy === "true") {
    // Lazy card — compute summary from <template> content without adding to DOM
    const tmpl = card.querySelector("template.rsp-card__lazy-content");
    if (tmpl) {
      const frag = tmpl.content;
      const groups = frag.querySelectorAll(".rsp-perm__group");
      const parts = [];
      groups.forEach((group) => {
        const title = group.querySelector(".rsp-perm__group-title");
        if (!title) return;
        const checked = [];
        group.querySelectorAll("input[type=checkbox]").forEach((cb) => {
          if (cb.checked) {
            const nameEl = cb
              .closest(".rsp-perm__item")
              ?.querySelector(".rsp-perm__item-name");
            if (nameEl) checked.push(nameEl.textContent.trim());
          }
        });
        if (checked.length > 0)
          parts.push(title.textContent.trim() + ": " + checked.join(", "));
      });
      const summaryEl = card.querySelector(".rsp-card__summary");
      if (summaryEl) {
        const summary = parts.join(" \u00B7 ");
        if (summary) {
          summaryEl.textContent = summary;
          summaryEl.classList.remove("rsp-card__summary--empty");
        } else {
          summaryEl.textContent =
            summaryEl.dataset.emptyText || "No permissions";
          summaryEl.classList.add("rsp-card__summary--empty");
        }
      }
    }
  } else {
    rspUpdateImplied(card);
    rspUpdateSummary(card);
  }
  rspUpdateCardBorders();
});

Behaviour.specify(".rsp-card__delete", "RoleStrategyRoles", 0, (btn) => {
  if (btn.dataset.initialized === "true") return;
  btn.dataset.initialized = "true";
  btn.addEventListener("click", (e) => {
    e.stopPropagation();
    const card = btn.closest(".rsp-card");
    if (!card) return;
    const roleName = card.dataset.roleName;
    dialog
      .confirm("Delete role", {
        message: `Are you sure you want to delete the role "${roleName}"?`,
        type: "destructive",
      })
      .then(() => {
        card.remove();
        rspUpdateCardBorders();
        rspSaveRoles()
          .then(() => {
            notificationBar.show(
              `Role "${roleName}" deleted`,
              notificationBar.SUCCESS,
            );
          })
          .catch((err) => {
            notificationBar.show(
              "Failed to save: " + err.message,
              notificationBar.ERROR,
            );
          });
      })
      .catch(() => {});
  });
});

// ============================================
// Implied permissions for role dialogs (add/edit)
// ============================================

const rspDialogUpdateImplied = (container) => {
  container
    .querySelectorAll(".rsp-assign-dialog__role-item[data-implied-by-list]")
    .forEach((item) => {
      const cb = item.querySelector("input[type=checkbox]");
      if (!cb) return;
      const impliedByStr = item.getAttribute("data-implied-by-list");
      if (!impliedByStr || !impliedByStr.trim()) return;
      const impliedByList = impliedByStr.trim().split(" ");
      let isImplied = false;
      for (const permId of impliedByList) {
        const ref = container.querySelector(
          `.rsp-assign-dialog__role-item[data-permission-id='${permId}'] input[type=checkbox]`,
        );
        if (ref && ref.checked && !ref.disabled) {
          isImplied = true;
          break;
        }
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

const rspDialogUncheckImplied = (container) => {
  container
    .querySelectorAll(
      ".rsp-assign-dialog__role-item[data-implied='true'] input[type=checkbox]",
    )
    .forEach((cb) => {
      cb.checked = false;
    });
};

const rspDialogAttachImplied = (container) => {
  container
    .querySelectorAll(".rsp-assign-dialog__role-item input[type=checkbox]")
    .forEach((cb) => {
      cb.addEventListener("change", () => rspDialogUpdateImplied(container));
    });
  rspDialogUpdateImplied(container);
};

// ============================================
// Edit role dialog (Jelly-based, matching Add Role)
// ============================================

const rspInitEditRoleDialog = (form) => {
  if (form.dataset.dialogInit === "true") return;
  form.dataset.dialogInit = "true";

  let savedManualSelections = null;

  const updateTemplate = () => {
    const templateSelect = form.querySelector("[name='templateName']");
    if (!templateSelect) return;
    const permContainer = form.querySelector("[name='permissions']");
    if (!permContainer) return;
    const checkboxes = permContainer.querySelectorAll("input[type='checkbox']");
    const templateName = templateSelect.value;
    if (templateName) {
      if (!savedManualSelections) {
        savedManualSelections = {};
        checkboxes.forEach((cb) => {
          if (cb.name) savedManualSelections[cb.name] = cb.checked;
        });
      }
      const dataEl = form.querySelector(
        `.rsp-template-perm-data[data-template-name='${CSS.escape(templateName)}']`,
      );
      let templatePerms = new Set();
      if (dataEl) {
        try {
          templatePerms = new Set(JSON.parse(dataEl.textContent));
        } catch (e) {}
      }
      checkboxes.forEach((cb) => {
        const pid = cb.name.replace(/^\[|]$/g, "");
        cb.checked = templatePerms.has(pid);
        cb.disabled = true;
      });
      const permEntry = permContainer.closest(".jenkins-form-item");
      if (permEntry) permEntry.style.opacity = "0.6";
    } else {
      checkboxes.forEach((cb) => {
        cb.disabled = false;
        if (
          savedManualSelections &&
          savedManualSelections[cb.name] !== undefined
        )
          cb.checked = savedManualSelections[cb.name];
      });
      savedManualSelections = null;
      const permEntry = permContainer.closest(".jenkins-form-item");
      if (permEntry) permEntry.style.opacity = "1";
    }
  };

  const validateAndSubmit = () => {
    const scope = form.querySelector("input[name='scope']")?.value;
    if (scope !== "globalRoles") {
      const patternInput = form.querySelector("input[name='pattern']");
      if (!patternInput || !patternInput.value.trim()) {
        patternInput?.focus();
        if (patternInput) {
          patternInput.style.outline = "2px solid var(--error-color)";
          patternInput.addEventListener(
            "input",
            () => {
              patternInput.style.outline = "";
            },
            { once: true },
          );
        }
        return;
      }
    }
    const pc = form.querySelector("[name='permissions']");
    if (pc) rspDialogUncheckImplied(pc);
    form.requestSubmit();
  };

  const applyPermFilter = () => {
    const filterEl = form.querySelector(".rsp-perm-dialog-filter input");
    const q = filterEl ? filterEl.value.toLowerCase().trim() : "";
    const permContainer = form.querySelector("[name='permissions']");
    if (!permContainer) return;

    let visibleCount = 0;
    permContainer
      .querySelectorAll(".rsp-assign-dialog__role-item")
      .forEach((item) => {
        const match =
          q === "" || (item.dataset.roleName || "").toLowerCase().includes(q);
        item.style.display = match ? "" : "none";
        if (match) visibleCount++;
      });
    permContainer
      .querySelectorAll(".rsp-assign-dialog__group-title")
      .forEach((title) => {
        let next = title.nextElementSibling;
        let hasVisible = false;
        while (
          next &&
          !next.classList.contains("rsp-assign-dialog__group-title")
        ) {
          if (next.classList.contains("rsp-assign-dialog__group")) {
            next
              .querySelectorAll(".rsp-assign-dialog__role-item")
              .forEach((child) => {
                if (child.style.display !== "none") hasVisible = true;
              });
          }
          next = next.nextElementSibling;
        }
        title.style.display = hasVisible ? "" : "none";
      });

    const noResults = permContainer.querySelector(
      ".rsp-assign-dialog__no-results",
    );
    if (noResults)
      noResults.classList.toggle(
        "jenkins-hidden",
        visibleCount > 0 || q === "",
      );
  };

  const filterInput = form.querySelector(".rsp-perm-dialog-filter input");
  if (filterInput) {
    filterInput.addEventListener("input", applyPermFilter);
  }

  const templateSelect = form.querySelector("[name='templateName']");
  if (templateSelect) templateSelect.addEventListener("change", updateTemplate);
  const submitBtn = form.querySelector("#rsp-edit-role-submit-btn");
  if (submitBtn) submitBtn.addEventListener("click", validateAndSubmit);
  form.addEventListener("keydown", (e) => {
    if (e.key === "Enter") {
      e.preventDefault();
      validateAndSubmit();
    }
  });

  const permContainer = form.querySelector("[name='permissions']");
  if (permContainer) rspDialogAttachImplied(permContainer);
};

Behaviour.specify(".rsp-card__edit", "RoleStrategyRoles", 0, (btn) => {
  if (btn.dataset.initialized === "true") return;
  btn.dataset.initialized = "true";
  btn.addEventListener("click", (e) => {
    e.stopPropagation();
    const card = btn.closest(".rsp-card");
    if (!card) return;
    const container = card.closest(".rsp-container");
    if (!container) return;
    const roleName = card.dataset.roleName;
    const scope = container.dataset.roleType;
    const rootUrl =
      document.querySelector("[data-rooturl]")?.getAttribute("data-rooturl") ||
      "";
    const dialogUrl = `${rootUrl}/manage/role-strategy/edit-role-dialog?roleName=${encodeURIComponent(roleName)}&scope=${encodeURIComponent(scope)}`;
    dialog.wizard(dialogUrl);
    const initDialog = () => {
      const form = document.querySelector("form[name='edit-role']");
      if (!form) {
        setTimeout(initDialog, 100);
        return;
      }
      rspInitEditRoleDialog(form);
    };
    setTimeout(initDialog, 200);
  });
});

Behaviour.specify(".rsp-card__pattern", "RoleStrategyRoles", 0, (span) => {
  if (span.dataset.initialized === "true") return;
  span.dataset.initialized = "true";
  span.addEventListener("click", (e) => {
    if (e.target.closest(".rsp-card__actions")) return;
    e.stopPropagation();
    const pattern = span.dataset.pattern;
    const roleType = span.closest(".rsp-container")?.dataset.roleType;
    if (pattern && roleType) rspShowMatchingItems(pattern, roleType);
  });
});

// Global Add Role button — opens Jelly-based dialog via wizard
Behaviour.specify(".rsp-add-role-global", "RoleStrategyRoles", 0, (btn) => {
  if (btn.dataset.initialized === "true") return;
  btn.dataset.initialized = "true";
  btn.addEventListener("click", () => {
    const rootUrl =
      document.querySelector("[data-rooturl]")?.getAttribute("data-rooturl") ||
      "";
    dialog.wizard(rootUrl + "/manage/role-strategy/add-role-dialog");
    // Wait for wizard to load content, then init the form (without applySubtree which causes double-toggle)
    const initDialog = () => {
      const form = document.querySelector("form[name='add-role']");
      if (!form) {
        setTimeout(initDialog, 100);
        return;
      }
      rspInitAddRoleDialog(form);
    };
    setTimeout(initDialog, 200);
  });
});

// Legacy JS Add Role button (unused — kept for reference)
Behaviour.specify(
  ".rsp-add-role-global-js-legacy",
  "RoleStrategyRoles",
  0,
  (btn) => {
    if (btn.dataset.initialized === "true") return;
    btn.dataset.initialized = "true";
    btn.addEventListener("click", () => {
      const containers = Array.from(
        document.querySelectorAll(".rsp-container"),
      ).filter((c) => c.dataset.roleType);
      if (containers.length === 0) return;

      // If only one section, use it directly
      if (containers.length === 1) {
        rspAddNewRole(containers[0]);
        return;
      }

      const scopeLabels = {
        globalRoles: "Global role",
        projectRoles: "Item role",
        slaveRoles: "Agent role",
      };

      // Wrap rspAddNewRole to prepend a scope selector
      const content = document.createElement("div");
      content.classList.add("rsp-dialog-content");

      const scopeGroup = document.createElement("div");
      scopeGroup.classList.add("jenkins-form-item");
      let radioHtml = "";
      containers.forEach((c, i) => {
        const type = c.dataset.roleType;
        const label = scopeLabels[type] || type;
        radioHtml += `<label class="jenkins-radio" style="margin-right:1rem;"><input type="radio" name="rsp-scope" value="${c.id}" ${i === 0 ? "checked" : ""} /> ${label}</label>`;
      });
      scopeGroup.innerHTML = `
      <label class="jenkins-form-label">Scope</label>
      <div class="jenkins-form-item__control" style="display:flex;flex-wrap:wrap;gap:0.5rem;">
        ${radioHtml}
      </div>
    `;
      content.appendChild(scopeGroup);

      const nameGroup = document.createElement("div");
      nameGroup.classList.add("jenkins-form-item");
      nameGroup.innerHTML = `
      <label class="jenkins-form-label">Role name</label>
      <div class="jenkins-form-item__control">
        <input type="text" class="jenkins-input" id="rsp-dialog-role-name" />
      </div>
    `;
      content.appendChild(nameGroup);

      // Pattern field — shown/hidden based on scope
      const patternGroup = document.createElement("div");
      patternGroup.classList.add("jenkins-form-item");
      patternGroup.id = "rsp-dialog-pattern-group";
      patternGroup.innerHTML = `
      <label class="jenkins-form-label">Pattern</label>
      <div class="jenkins-form-item__control">
        <input type="text" class="jenkins-input" id="rsp-dialog-role-pattern" />
      </div>
    `;
      content.appendChild(patternGroup);

      // Template field — shown only for item roles
      const templateGroup = document.createElement("div");
      templateGroup.classList.add("jenkins-form-item");
      templateGroup.id = "rsp-dialog-template-group";
      templateGroup.style.display = "none";
      content.appendChild(templateGroup);

      // Permission section — cloned from the selected scope's first card
      const permWrapper = document.createElement("div");
      permWrapper.classList.add("jenkins-form-item");
      permWrapper.id = "rsp-dialog-perm-wrapper";
      const permTitle = document.createElement("label");
      permTitle.classList.add("jenkins-form-label");
      permTitle.textContent = "Permissions";
      permWrapper.appendChild(permTitle);
      content.appendChild(permWrapper);

      const updateForScope = () => {
        const selectedId = content.querySelector(
          "input[name='rsp-scope']:checked",
        )?.value;
        const container = document.getElementById(selectedId);
        if (!container) return;

        const isGlobal = selectedId === "globalRoles";
        const isProject = selectedId === "projectRoles";

        // Pattern visibility
        patternGroup.style.display = isGlobal ? "none" : "";

        // Template dropdown
        if (isProject) {
          const templateStr = container.dataset.templates || "";
          const templateNames = templateStr
            ? templateStr
                .split(",")
                .map((s) => s.trim())
                .filter(Boolean)
            : [];
          if (templateNames.length > 0) {
            const options = templateNames
              .map(
                (n) =>
                  `<option value="${escapeHTML(n)}">${escapeHTML(n)}</option>`,
              )
              .join("");
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
            templateGroup.style.display = "";
          } else {
            templateGroup.innerHTML = "";
            templateGroup.style.display = "none";
          }
        } else {
          templateGroup.innerHTML = "";
          templateGroup.style.display = "none";
        }

        // Permission pills — clone from first card in scope
        const existingPerm = permWrapper.querySelector(".rsp-perm");
        if (existingPerm) existingPerm.remove();

        const firstCard = container.querySelector(".rsp-card");
        if (firstCard) {
          const permSection = firstCard.querySelector(".rsp-perm");
          if (permSection) {
            const permClone = permSection.cloneNode(true);
            permClone.querySelectorAll("input[type=checkbox]").forEach((cb) => {
              cb.checked = false;
              cb.disabled = false;
              cb.removeAttribute("data-initialized");
            });
            permClone
              .querySelectorAll(".rsp-perm__item--implied")
              .forEach((el) => el.classList.remove("rsp-perm__item--implied"));
            permClone
              .querySelectorAll(".rsp-perm__item-implied")
              .forEach((el) => {
                el.hidden = true;
              });
            permWrapper.appendChild(permClone);

            // Implied logic
            const updateImplied = () => {
              permClone
                .querySelectorAll(".rsp-perm__item[data-permission-id]")
                .forEach((label) => {
                  const innerCb = label.querySelector("input[type=checkbox]");
                  if (!innerCb) return;
                  const impliedByStr = label.getAttribute(
                    "data-implied-by-list",
                  );
                  if (!impliedByStr || !impliedByStr.trim()) return;
                  let isImplied = false;
                  for (const pid of impliedByStr.trim().split(" ")) {
                    const ref = permClone.querySelector(
                      `.rsp-perm__item[data-permission-id='${pid}'] input[type=checkbox]`,
                    );
                    if (ref && ref.checked) {
                      isImplied = true;
                      break;
                    }
                  }
                  const il = label.querySelector(".rsp-perm__item-implied");
                  if (isImplied) {
                    innerCb.checked = false;
                    innerCb.disabled = true;
                    label.classList.add("rsp-perm__item--implied");
                    if (il) il.hidden = false;
                  } else {
                    innerCb.disabled = false;
                    label.classList.remove("rsp-perm__item--implied");
                    if (il) il.hidden = true;
                  }
                });
            };
            permClone
              .querySelectorAll("input[type=checkbox]")
              .forEach((cb) => cb.addEventListener("change", updateImplied));

            // Template select disable
            const tmplSelect = content.querySelector(
              "#rsp-dialog-role-template",
            );
            if (tmplSelect) {
              tmplSelect.addEventListener("change", () => {
                const isTmpl = tmplSelect.value !== "";
                permClone
                  .querySelectorAll("input[type=checkbox]")
                  .forEach((cb) => {
                    if (isTmpl) {
                      cb.checked = false;
                      cb.disabled = true;
                    } else {
                      cb.disabled = false;
                    }
                  });
                permClone.style.opacity = isTmpl ? "0.5" : "1";
                permClone.style.pointerEvents = isTmpl ? "none" : "";
              });
            }
          }
        }
      };

      // Scope change handler
      content.querySelectorAll("input[name='rsp-scope']").forEach((r) => {
        r.addEventListener("change", updateForScope);
      });
      updateForScope();

      // Dialog
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

      const dlgBody = document.createElement("div");
      dlgBody.classList.add("jenkins-dialog__contents");
      dlgBody.appendChild(content);
      dlg.appendChild(dlgBody);

      const footer = document.createElement("div");
      footer.classList.add(
        "jenkins-dialog__footer",
        "jenkins-buttons-row",
        "jenkins-buttons-row--equal-width",
      );
      footer.innerHTML = `
      <button class="jenkins-button" data-id="cancel">Cancel</button>
      <button class="jenkins-button jenkins-button--primary" data-id="ok">Add</button>
    `;
      dlg.appendChild(footer);

      document.body.appendChild(dlg);
      dlg.showModal();

      const closeDialog = () => {
        dlg.close();
        dlg.remove();
      };
      dlg
        .querySelectorAll("[data-id='cancel']")
        .forEach((b) => b.addEventListener("click", closeDialog));
      dlg.addEventListener("cancel", closeDialog);

      dlg.querySelector("[data-id='ok']").addEventListener("click", () => {
        const selectedId = dlg.querySelector(
          "input[name='rsp-scope']:checked",
        )?.value;
        const container = document.getElementById(selectedId);
        if (!container) return;

        const nameInput = dlg.querySelector("#rsp-dialog-role-name");
        const name = nameInput?.value?.trim();
        if (!name) {
          nameInput?.focus();
          return;
        }

        const existing = container.querySelector(
          `.rsp-card[data-role-name='${CSS.escape(name)}']`,
        );
        if (existing) {
          closeDialog();
          dialog.alert(`A role named "${name}" already exists.`);
          return;
        }

        const isGlobal = selectedId === "globalRoles";
        let pattern = "";
        if (!isGlobal) {
          const patternInput = dlg.querySelector("#rsp-dialog-role-pattern");
          pattern = patternInput?.value?.trim();
          if (!pattern) {
            patternInput?.focus();
            return;
          }
        }

        let templateName = "";
        const tmplSelect = dlg.querySelector("#rsp-dialog-role-template");
        if (tmplSelect) templateName = tmplSelect.value;

        const selectedPermissions = new Set();
        dlg
          .querySelectorAll(".rsp-perm__item input[type=checkbox]:checked")
          .forEach((cb) => {
            const permId = cb.getAttribute("data-permission-id");
            if (permId) selectedPermissions.add(permId);
          });

        closeDialog();
        rspAddRoleAndSave(
          container,
          name,
          pattern,
          templateName,
          selectedPermissions,
        );
      });
    });
  },
);

// Add Role dialog — all setup done imperatively after wizard loads
const rspInitAddRoleDialog = (form) => {
  if (form.dataset.dialogInit === "true") return;
  form.dataset.dialogInit = "true";

  let savedManualSelections = null;

  const getScope = () => {
    // Radio buttons (multiple scopes) or hidden input (single scope)
    return (
      form.querySelector("input[name='scope']:checked")?.value ||
      form.querySelector("input[name='scope']")?.value
    );
  };

  const updateScope = () => {
    const scope = getScope();
    const isGlobal = scope === "globalRoles";
    const patternEntry = form
      .querySelector("input[name='pattern']")
      ?.closest(".jenkins-form-item");
    if (patternEntry) patternEntry.classList.toggle("jenkins-hidden", isGlobal);
    const templateEntry = form
      .querySelector("[name='templateName']")
      ?.closest(".jenkins-form-item");
    if (templateEntry)
      templateEntry.classList.toggle(
        "jenkins-hidden",
        scope !== "projectRoles",
      );
    const templateSelect = form.querySelector("[name='templateName']");
    if (templateSelect && scope !== "projectRoles") {
      templateSelect.value = "";
      updateTemplate();
    }
    const permContainer = form.querySelector("[name='permissions']");
    if (permContainer) {
      permContainer.querySelectorAll(":scope > div[name]").forEach((div) => {
        div.classList.toggle(
          "jenkins-hidden",
          div.getAttribute("name") !== scope,
        );
      });
    }
    applyPermFilter();
  };

  const updateTemplate = () => {
    const templateSelect = form.querySelector("[name='templateName']");
    if (!templateSelect) return;
    const permContainer = form.querySelector("[name='permissions']");
    if (!permContainer) return;
    const scope = getScope() || "projectRoles";
    const scopeDiv = permContainer.querySelector(`div[name='${scope}']`);
    if (!scopeDiv) return;
    const checkboxes = scopeDiv.querySelectorAll("input[type='checkbox']");
    const templateName = templateSelect.value;
    if (templateName) {
      if (!savedManualSelections) {
        savedManualSelections = {};
        checkboxes.forEach((cb) => {
          if (cb.name) savedManualSelections[cb.name] = cb.checked;
        });
      }
      const dataEl = form.querySelector(
        `.rsp-template-perm-data[data-template-name='${CSS.escape(templateName)}']`,
      );
      let templatePerms = new Set();
      if (dataEl) {
        try {
          templatePerms = new Set(JSON.parse(dataEl.textContent));
        } catch (e) {}
      }
      checkboxes.forEach((cb) => {
        const pid = cb.name.replace(/^\[|]$/g, "");
        cb.checked = templatePerms.has(pid);
        cb.disabled = true;
      });
      const permEntry = permContainer.closest(".jenkins-form-item");
      if (permEntry) permEntry.style.opacity = "0.6";
    } else {
      checkboxes.forEach((cb) => {
        cb.disabled = false;
        if (
          savedManualSelections &&
          savedManualSelections[cb.name] !== undefined
        )
          cb.checked = savedManualSelections[cb.name];
      });
      savedManualSelections = null;
      const permEntry = permContainer.closest(".jenkins-form-item");
      if (permEntry) permEntry.style.opacity = "1";
    }
  };

  const showFieldError = (input) => {
    if (!input) return;
    input.style.outline = "2px solid var(--error-color)";
    input.addEventListener(
      "input",
      () => {
        input.style.outline = "";
      },
      { once: true },
    );
  };

  const validateAndSubmit = () => {
    const nameInput = form.querySelector("input[name='roleName']");
    const roleName = nameInput?.value?.trim();
    if (!roleName) {
      nameInput?.focus();
      showFieldError(nameInput);
      return;
    }

    const scope = getScope();

    // Check for duplicate role name in the selected scope
    const scopeContainer = document.getElementById(scope);
    if (
      scopeContainer &&
      scopeContainer.querySelector(
        `.rsp-card[data-role-name='${CSS.escape(roleName)}']`,
      )
    ) {
      nameInput?.focus();
      showFieldError(nameInput);
      dialog.alert(`A role named "${roleName}" already exists.`);
      return;
    }

    if (scope !== "globalRoles") {
      const patternInput = form.querySelector("input[name='pattern']");
      if (!patternInput || !patternInput.value.trim()) {
        patternInput?.focus();
        showFieldError(patternInput);
        return;
      }
    }

    // Check at least one permission is selected (excluding implied/disabled)
    const permContainer = form.querySelector("[name='permissions']");
    const scopeDiv = permContainer?.querySelector(`div[name='${scope}']`);
    const templateSelect = form.querySelector("[name='templateName']");
    const hasTemplate = templateSelect && templateSelect.value;
    if (!hasTemplate && scopeDiv) {
      const hasChecked = scopeDiv.querySelector(
        "input[type='checkbox']:checked:not(:disabled)",
      );
      if (!hasChecked) {
        dialog.alert("Please select at least one permission.");
        return;
      }
    }

    if (permContainer) rspDialogUncheckImplied(permContainer);
    form.requestSubmit();
  };

  function applyPermFilter() {
    const filterEl = form.querySelector(".rsp-perm-dialog-filter input");
    const q = filterEl ? filterEl.value.toLowerCase().trim() : "";
    const permContainer = form.querySelector("[name='permissions']");
    if (!permContainer) return;

    const scope = getScope();
    const scopeDiv = permContainer.querySelector(`div[name='${scope}']`);
    if (!scopeDiv) return;

    let visibleCount = 0;
    scopeDiv
      .querySelectorAll(".rsp-assign-dialog__role-item")
      .forEach((item) => {
        const match =
          q === "" || (item.dataset.roleName || "").toLowerCase().includes(q);
        item.style.display = match ? "" : "none";
        if (match) visibleCount++;
      });
    scopeDiv
      .querySelectorAll(".rsp-assign-dialog__group-title")
      .forEach((title) => {
        let next = title.nextElementSibling;
        let hasVisible = false;
        while (
          next &&
          !next.classList.contains("rsp-assign-dialog__group-title")
        ) {
          if (next.classList.contains("rsp-assign-dialog__group")) {
            next
              .querySelectorAll(".rsp-assign-dialog__role-item")
              .forEach((child) => {
                if (child.style.display !== "none") hasVisible = true;
              });
          }
          next = next.nextElementSibling;
        }
        title.style.display = hasVisible ? "" : "none";
      });

    const noResults = permContainer.querySelector(
      ".rsp-assign-dialog__no-results",
    );
    if (noResults)
      noResults.classList.toggle(
        "jenkins-hidden",
        visibleCount > 0 || q === "",
      );
  }

  const filterInput = form.querySelector(".rsp-perm-dialog-filter input");
  if (filterInput) {
    filterInput.addEventListener("input", applyPermFilter);
  }

  form.querySelectorAll("input[name='scope']").forEach((r) =>
    r.addEventListener("change", () => {
      updateScope();
      const permContainer = form.querySelector("[name='permissions']");
      if (permContainer) rspDialogAttachImplied(permContainer);
    }),
  );
  const templateSelect = form.querySelector("[name='templateName']");
  if (templateSelect) templateSelect.addEventListener("change", updateTemplate);
  const submitBtn = form.querySelector("#rsp-add-role-submit-btn");
  if (submitBtn) submitBtn.addEventListener("click", validateAndSubmit);
  form.addEventListener("keydown", (e) => {
    if (e.key === "Enter") {
      e.preventDefault();
      validateAndSubmit();
    }
  });

  updateScope();
  const permContainer = form.querySelector("[name='permissions']");
  if (permContainer) rspDialogAttachImplied(permContainer);
};

// Per-section search (kept for backward compat)
Behaviour.specify(".rsp-search input", "RoleStrategyRoles", 0, (input) => {
  if (input.dataset.searchInitialized === "true") return;
  input.dataset.searchInitialized = "true";
  input.addEventListener("input", () => {
    const container = input.closest(".rsp-container");
    if (container) rspApplyFilters(container);
  });
});

// Global search across all sections
// Active permission filters for role management page
let rspRolesActivePermFilters = [];

// Check if a card has a given permission (in template or live body)
const rspCardHasPermission = (card, permId) => {
  // Check live body first
  const liveCb = card.querySelector(
    `.rsp-perm__item[data-permission-id='${permId}'] input[type=checkbox]`,
  );
  if (liveCb) return liveCb.checked || liveCb.disabled;
  // Check template content
  const tmpl = card.querySelector("template.rsp-card__lazy-content");
  if (tmpl) {
    const cb = tmpl.content.querySelector(
      `[data-permission-id='${permId}'] input[type=checkbox]`,
    );
    if (cb) return cb.checked;
  }
  return false;
};

const rspApplyRolesFilter = () => {
  const input = document.querySelector(".rsp-roles-search input");
  const query = input ? input.value.toLowerCase().trim() : "";
  const hasFilters = query !== "" || rspRolesActivePermFilters.length > 0;

  let totalVisible = 0;
  let totalAll = 0;

  document.querySelectorAll(".rsp-container").forEach((container) => {
    let visibleCount = 0;
    let sectionTotal = 0;
    container.querySelectorAll(".rsp-card").forEach((card) => {
      sectionTotal++;
      const roleName = (card.dataset.roleName || "").toLowerCase();
      const pattern = (card.dataset.rolePattern || "").toLowerCase();
      const matchesText =
        query === "" || roleName.includes(query) || pattern.includes(query);

      let matchesPerm = rspRolesActivePermFilters.length === 0;
      if (!matchesPerm) {
        for (const permId of rspRolesActivePermFilters) {
          if (rspCardHasPermission(card, permId)) {
            matchesPerm = true;
            break;
          }
        }
      }

      const visible = matchesText && matchesPerm;
      card.classList.toggle("rsp-card--hidden", !visible);
      if (visible) visibleCount++;
    });

    totalVisible += visibleCount;
    totalAll += sectionTotal;

    const section = container.closest(".jenkins-section");
    if (section) {
      section.style.display = hasFilters && visibleCount === 0 ? "none" : "";
      // Update section count badge
      const titleEl = section.querySelector(".jenkins-section__title");
      if (titleEl) {
        let badge = titleEl.querySelector(".rsp-section-count");
        if (!badge) {
          badge = document.createElement("span");
          badge.classList.add("rsp-section-count");
          badge.style.cssText =
            "font-size:0.8125rem;color:var(--text-color-secondary);font-weight:normal;margin-left:0.375rem;";
          titleEl.appendChild(badge);
        }
        badge.textContent = hasFilters
          ? visibleCount + " / " + sectionTotal
          : sectionTotal.toString();
      }
    }
  });

  rspUpdateCardBorders();

  // Update result count
  let countEl = document.getElementById("rsp-roles-result-count");
  if (!countEl) {
    countEl = document.createElement("div");
    countEl.id = "rsp-roles-result-count";
    countEl.style.cssText =
      "color:var(--text-color-secondary);font-size:0.875rem;margin:0.75rem 0 0.5rem 0;";
    const wrapper = document.querySelector(".rsp-search-wrapper");
    if (wrapper) wrapper.closest("div").after(countEl);
  }
  countEl.textContent = hasFilters
    ? totalVisible.toLocaleString() +
      " of " +
      totalAll.toLocaleString() +
      " roles"
    : totalAll.toLocaleString() + " roles";

  // Update filter button state
  const filterBtn = document.querySelector(".rsp-roles-perm-filter-btn");
  if (filterBtn) {
    const active = rspRolesActivePermFilters.length > 0;
    filterBtn.classList.toggle("jenkins-button--tertiary", !active);
    filterBtn.classList.toggle("jenkins-!-accent-color", active);
  }
  const resetBtn = document.querySelector(".rsp-roles-perm-filter-reset");
  if (resetBtn) resetBtn.hidden = rspRolesActivePermFilters.length === 0;
};

// Search input triggers unified filter
Behaviour.specify(
  ".rsp-roles-search input",
  "RoleStrategyRoles",
  0,
  (input) => {
    if (input.dataset.searchInitialized === "true") return;
    input.dataset.searchInitialized = "true";
    input.addEventListener("input", rspApplyRolesFilter);
  },
);

// Permission filter dropdown for roles page
Behaviour.specify(
  ".rsp-roles-perm-filter-btn",
  "RoleStrategyRoles",
  0,
  (btn) => {
    if (btn.dataset.initialized === "true") return;
    btn.dataset.initialized = "true";

    const dropdown = document.querySelector(".rsp-roles-perm-filter-dropdown");
    if (!dropdown) return;

    // Item click — toggle filter
    dropdown.querySelectorAll(".jenkins-dropdown__item").forEach((item) => {
      item.addEventListener("click", () => {
        item.classList.toggle("rsp-filter__item--active");
        rspRolesActivePermFilters = [];
        dropdown.querySelectorAll(".rsp-filter__item--active").forEach((a) => {
          rspRolesActivePermFilters.push(a.dataset.filterPermission);
        });
        rspApplyRolesFilter();
      });
    });

    // Search within dropdown
    const searchInput = dropdown.querySelector(
      ".rsp-roles-perm-filter-search input",
    );
    const applySearch = () => {
      const q = searchInput ? searchInput.value.toLowerCase().trim() : "";
      dropdown.querySelectorAll(".jenkins-dropdown__item").forEach((item) => {
        item.classList.toggle(
          "rsp-filter__item--filter-hidden",
          q !== "" &&
            !(item.dataset.filterLabel || "").toLowerCase().includes(q),
        );
      });
      dropdown.querySelectorAll(".rsp-filter__group-title").forEach((title) => {
        let next = title.nextElementSibling;
        let hasVisible = false;
        while (next && !next.classList.contains("rsp-filter__group-title")) {
          if (
            next.classList.contains("jenkins-dropdown__item") &&
            !next.classList.contains("rsp-filter__item--filter-hidden")
          )
            hasVisible = true;
          next = next.nextElementSibling;
        }
        title.classList.toggle(
          "rsp-filter__group-title--filter-hidden",
          !hasVisible,
        );
      });
      const noResults = dropdown.querySelector(".rsp-filter__no-results");
      if (noResults)
        noResults.hidden = !!dropdown.querySelector(
          ".jenkins-dropdown__item:not(.rsp-filter__item--filter-hidden)",
        );
    };
    if (searchInput) {
      searchInput.addEventListener("input", applySearch);
      searchInput.addEventListener("click", (e) => e.stopPropagation());
    }

    // Reset
    const resetBtn = dropdown.querySelector(".rsp-roles-perm-filter-reset");
    if (resetBtn) {
      resetBtn.addEventListener("click", (e) => {
        e.preventDefault();
        dropdown
          .querySelectorAll(".rsp-filter__item--active")
          .forEach((item) => item.classList.remove("rsp-filter__item--active"));
        rspRolesActivePermFilters = [];
        if (searchInput) searchInput.value = "";
        applySearch();
        rspApplyRolesFilter();
      });
    }

    // Toggle dropdown
    btn.addEventListener("click", (e) => {
      e.stopPropagation();
      const isOpen = !dropdown.hidden;
      dropdown.hidden = isOpen;
      btn.setAttribute("aria-expanded", String(!isOpen));
      if (!isOpen) {
        const close = () => {
          dropdown.hidden = true;
          btn.setAttribute("aria-expanded", "false");
          document.removeEventListener("click", ch);
          document.removeEventListener("keydown", eh);
        };
        const ch = (evt) => {
          if (!dropdown.contains(evt.target) && evt.target !== btn) close();
        };
        const eh = (evt) => {
          if (evt.key === "Escape") {
            close();
            btn.focus();
          }
        };
        setTimeout(() => {
          document.addEventListener("click", ch);
          document.addEventListener("keydown", eh);
        }, 0);
      }
    });
  },
);

Behaviour.specify(".rsp-filter__button", "RoleStrategyRoles", 0, (btn) => {
  if (btn.dataset.filterInitialized === "true") return;
  // Skip the global roles-page permission filter — it has its own dedicated behaviour
  if (btn.classList.contains("rsp-roles-perm-filter-btn")) return;
  btn.dataset.filterInitialized = "true";
  const filterEl = btn.parentElement;
  const dropdown = filterEl.querySelector(".rsp-filter__dropdown");
  if (!dropdown) return;
  const container = filterEl.closest(".rsp-container");

  dropdown.querySelectorAll(".jenkins-dropdown__item").forEach((item) => {
    item.addEventListener("click", () => {
      item.classList.toggle("rsp-filter__item--active");
      if (container) rspApplyFilters(container);
    });
  });

  const filterSearchInput = dropdown.querySelector(
    ".rsp-filter__search-bar input",
  );
  const applyFilterSearch = () => {
    const q = filterSearchInput
      ? filterSearchInput.value.toLowerCase().trim()
      : "";
    dropdown.querySelectorAll(".jenkins-dropdown__item").forEach((item) => {
      const label = (
        item.getAttribute("data-filter-label") || ""
      ).toLowerCase();
      item.classList.toggle(
        "rsp-filter__item--filter-hidden",
        q !== "" && !label.includes(q),
      );
    });
    dropdown.querySelectorAll(".rsp-filter__group-title").forEach((title) => {
      let next = title.nextElementSibling;
      let hasVisible = false;
      while (next && !next.classList.contains("rsp-filter__group-title")) {
        if (
          next.classList.contains("jenkins-dropdown__item") &&
          !next.classList.contains("rsp-filter__item--filter-hidden")
        )
          hasVisible = true;
        next = next.nextElementSibling;
      }
      title.classList.toggle(
        "rsp-filter__group-title--filter-hidden",
        !hasVisible,
      );
    });
    const noResults = dropdown.querySelector(".rsp-filter__no-results");
    if (noResults)
      noResults.hidden = !!dropdown.querySelector(
        ".jenkins-dropdown__item:not(.rsp-filter__item--filter-hidden)",
      );
  };
  if (filterSearchInput) {
    filterSearchInput.addEventListener("input", applyFilterSearch);
    filterSearchInput.addEventListener("click", (e) => e.stopPropagation());
  }

  const resetBtn = dropdown.querySelector(".rsp-filter__reset-button");
  if (resetBtn) {
    resetBtn.addEventListener("click", (e) => {
      e.preventDefault();
      dropdown
        .querySelectorAll(".rsp-filter__item--active")
        .forEach((item) => item.classList.remove("rsp-filter__item--active"));
      if (filterSearchInput) filterSearchInput.value = "";
      applyFilterSearch();
      if (container) rspApplyFilters(container);
    });
  }

  btn.addEventListener("click", (e) => {
    e.stopPropagation();
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
        if (!dropdown.contains(evt.target) && evt.target !== btn)
          closeDropdown();
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

// ============================================
// Collapsible sections
// ============================================

Behaviour.specify(
  ".rsp-container",
  "RoleStrategyCollapsible",
  0,
  (container) => {
    const section = container.closest(".jenkins-section");
    if (!section || section.dataset.collapsibleInit === "true") return;
    section.dataset.collapsibleInit = "true";
    section.classList.add("rsp-section-collapsible");
    const title = section.querySelector(".jenkins-section__title");
    if (!title) return;
    const chevron = document.createElement("span");
    chevron.classList.add("rsp-section-chevron");
    chevron.innerHTML =
      '<svg viewBox="0 0 512 512" fill="none" stroke="currentColor" stroke-width="48" stroke-linecap="round" stroke-linejoin="round"><path d="M112 184l144 144 144-144"/></svg>';
    title.appendChild(chevron);
    title.addEventListener("click", () => {
      section.classList.toggle("rsp-section--collapsed");
    });

    // Trigger initial counts (deferred so all containers are processed)
    if (!window.rspRolesInitCountDone) {
      window.rspRolesInitCountDone = true;
      setTimeout(rspApplyRolesFilter, 0);
    }
  },
);
