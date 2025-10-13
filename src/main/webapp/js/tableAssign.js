/*
 * The MIT License
 *
 * Copyright (c) 2022 - 2025, Markus Winter
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
// number of lines required for the user filter to get enabled
var filterLimit = 10;
// number of lines required for the footer to get displayed
var footerLimit = 20;
var globalTableHighlighter;
var newGlobalRowTemplate;
var itemTableHighlighter;
var newItemRowTemplate;
var agentTableHighlighter;
var newAgentRowTemplate;
let maxRows;

const roleStrategyEntries = {};

function filterUsers(filter, tableId, page) {
  const json = roleStrategyEntries[tableId];
  const table= document.getElementById(tableId);
  const filtered = filter != null ? json.filter((entry) => entry["name"].toUpperCase().indexOf(filter) > -1) : json;
  showEntries(tableId, filtered, page);
  const container = table.closest(".rsp-roles-container");
  const roleInputFilter = container.querySelector(".role-input-filter");
  const roleFilter = roleInputFilter != null ? roleInputFilter.value.toUpperCase() : "";
  filterRoles(roleFilter, table);
}

function filterRoles(filter, table) {
  const rowCount = table.rows.length;
  const startColumn = 2; // column 0 is the delete button, column 1 contains the user/group
  const headerRow = table.rows[0];
  const endColumn = headerRow.cells.length; // last column is the delete button
  for (let c = 0; c < endColumn; c++) {
    let shouldFilter = true;
    if (filter==null||!headerRow.cells[c].classList.contains("rsp-table--header-th") || headerRow.cells[c].textContent.toUpperCase().indexOf(filter) > -1) {
      shouldFilter = false;
    }
    for (let r = 0; r < rowCount; r++) {
      if (shouldFilter) {
        table.rows[r].cells[c].style.display = "none";
      } else {
        table.rows[r].cells[c].style.display = "";
      }
    }
  }
}

Behaviour.specify(".user-input-filter", "RoleBasedAuthorizationStrategy", 0, function(e) {
  e.onkeyup = debounce((event) => {
    if (ignoreKeys(event.code)) {
      return;
    }
    const filter = e.value.toUpperCase();
    const tableId = e.getAttribute("data-table-id");
    filterUsers(filter, tableId, 0);
  });
});


Behaviour.specify(".role-input-filter", "RoleBasedAuthorizationStrategy", 0, function(e) {
  e.onkeyup = debounce((event) => {
    if (ignoreKeys(event.code)) {
      return;
    }
    const filter = e.value.toUpperCase();
    const table = document.getElementById(e.getAttribute("data-table-id"));
    filterRoles(filter, table);
  });
});

Behaviour.specify(
  ".role-strategy-add-button", "RoleBasedAuthorizationStrategy", 0, function(button) {
    button.onclick = function(e) {
      const container = button.closest(".rsp-roles-container");
      const table = container.querySelector("table");
      const tableId = table.id;
      const templateId = container.dataset.template;
      const template = document.getElementById(templateId).content.firstElementChild;
      const highlighter = container.dataset.highlighter;
      addButtonAction(button, template, table, highlighter);
      const tbody = table.tBodies[0];
      if (tbody.children.length >= filterLimit) {
        const userfilters = document.querySelectorAll(".user-filter")
        for (let q=0;q<userfilters.length;++q) {
          const filter = userfilters[q];
          if (filter.getAttribute("data-table-id") === tableId) {
            filter.style.display = "block";
          }
        }
      }
      if (tbody.children.length >= footerLimit) {
        table.tFoot.style.display = "table-footer-group";
      }
    }
  }
);

function insertRow(template, tbody, tableHighlighter, name, type, roles, title, icon) {
  const copy = template.cloneNode(true);
  const removeDeleteButton = title != null;
  const children = copy.childNodes;
  let tooltipDescription = "Group";
  if (type==="USER") {
    tooltipDescription = "User";
  }
  children.forEach(function(item){
    item.outerHTML= item.outerHTML.replace(/{{USER}}/g, doubleEscapeHTML(name)).replace(/{{USERGROUP}}/g, tooltipDescription);
  });

  if (removeDeleteButton) {
    copy.classList.remove("permission-row");
  }
  copy.dataset.name = name;
  copy.dataset.type = type;
  children.forEach(function(item) {
    if (removeDeleteButton) {
      const removeButtons = item.querySelectorAll(".rsp-remove");
      removeButtons.forEach((r) => {
        r.remove();
      });
    }
    const roleName = item.dataset.roleName;
    if (roles !== null && roleName !== null && roles.indexOf(roleName) != -1) {
      const input = item.querySelector("input");
      input.checked = true;
    }
  });

  const nameCell = copy.querySelector(".left-most");
  const nameDiv = document.createElement("div");
  nameDiv.classList.add("rsp-table__cell");
  if (icon != null) {
    nameDiv.appendChild(generateSVGIcon(icon));
  }
  if (type==="EITHER") {
    const migrateButtons = copy.querySelectorAll(".migrate");
    migrateButtons.forEach((b) => {
      b.classList.remove("jenkins-hidden");
    });
  }
  if (title != null) {
    nameDiv.append(title);
  } else {
    nameDiv.append(name);
  }
  nameCell.replaceChildren(nameDiv);
  copy.setAttribute("name",'['+type+':'+name+']');
  tbody.appendChild(copy);
  if (tableHighlighter !== null) {
    highlighter = window[tableHighlighter];
    highlighter.scan(copy);
  }
}

function toggleRole(event) {
  const cb = event.target;
  const roleName = cb.closest("td").dataset.roleName;
  const name = cb.closest("tr").dataset.name;
  const type = cb.closest("tr").dataset.type
  const tableId = cb.closest("table").id;
  const json = roleStrategyEntries[tableId];
  const entry = findPermissionEntry(json, name, type);
  const roles = entry["roles"];
  if (cb.checked) {
    roles.push(roleName)
  } else {
    const index = roles.indexOf(roleName);
    roles.splice(index, 1);
  }
}

Behaviour.specify(".rsp-checkbox", 'RoleBasedAuthorizationStrategy', 0, function(cb) {
  cb.addEventListener("click", toggleRole);
});

function addButtonAction(button, template, table, tableHighlighter) {
  const type = button.getAttribute('data-type');
  const tbody = table.tBodies[0];
  const json = roleStrategyEntries[table.id];

  dialog.prompt(button.getAttribute('data-prompt')).then( (name) => {
    name = name.trim();
    if (findPermissionEntry(json, name, type) != null) {
      dialog.alert(button.getAttribute('data-error-message'))
      return;
    }

    insertRow(template, tbody, tableHighlighter, name, type, [], null, null)
    addPermissionEntry(json, name, type);
    const container = table.closest(".rsp-roles-container");
    const roleInputFilter = container.querySelector(".role-input-filter");
    let roleFilter = "";
    if (roleInputFilter !== null) {
      roleFilter = roleInputFilter.value.toUpperCase();
    }
    filterRoles(roleFilter, table);
    Behaviour.applySubtree(table, true);
  });
}


Behaviour.specify(".role-strategy-table .rsp-remove", 'RoleBasedAuthorizationStrategy', 0, function(e) {
  e.onclick = function() {
    const table = this.closest("TABLE");
    const tableId = table.getAttribute("id");
    const tr = this.closest("TR");
    const parent = tr.parentNode;
    const json = roleStrategyEntries[tableId];
    deletePermissionEntry(json, tr.dataset.name, tr.dataset.type);
    parent.removeChild(tr);
    if (parent.children.length < filterLimit) {
      let userfilters = document.querySelectorAll(".user-filter")
      for (let q=0;q<userfilters.length;++q) {
        let filter = userfilters[q];
        if (filter.getAttribute("data-table-id") === tableId) {
          filter.style.display = "none";
          let inputs = filter.getElementsByTagName("INPUT");
          inputs[0].value=""
          let event = new Event("keyup");
          inputs[0].dispatchEvent(event);
        }
      }
    }
    if (parent.children.length < footerLimit) {
      table.tFoot.style.display = "none";
    }
    const dirtyButton = document.getElementById("rs-dirty-indicator");
    dirtyButton.dispatchEvent(new Event('click'));
    return false;
  }
});

Behaviour.specify(".role-strategy-table TR.permission-row", "RoleBasedAuthorizationStrategy", 0, function(e) {
    if (!e.hasAttribute('data-checked')) {
      const td = e.querySelector(".left-most");
      FormChecker.delayedCheck(e.getAttribute('data-descriptor-url') + "/checkName?value="+encodeURIComponent(e.getAttribute("name")),"POST",td);
      e.setAttribute('data-checked', 'true');
    }
});

/*
 * Behavior for 'Migrate to user' element that exists for each ambiguous row
 */
Behaviour.specify(".role-strategy-table TD.stop .migrate", 'RoleBasedAuthorizationStrategy', 0, function(e) {
  e.onclick = function() {
    const tr = this.closest("TR");
    const name = tr.getAttribute('name');

    let newName = name.replace('[EITHER:', '[USER:'); // migrate_user behavior
    if (this.classList.contains('migrate_group')) {
      newName = name.replace('[EITHER:', '[GROUP:');
    }

    const table = this.closest("TABLE");
    const tableRows = table.tBodies[0].getElementsByTagName('tr');
    let newNameElement = null;
    for (let i = 0; i < tableRows.length; i++) {
      if (tableRows[i].getAttribute('name') === newName) {
        newNameElement = tableRows[i];
        break;
      }
    }
    if (newNameElement === tr) {
      // uh-oh, we shouldn't be able to find ourselves, so just do nothing
      return false;
    }
    if (newNameElement == null) {
      // no row for this name exists yet, so transform the ambiguous row to unambiguous
      tr.setAttribute('name', newName);
      tr.removeAttribute('data-checked');

      // remove migration buttons from updated row
      const buttonContainer = this.closest("TD");
      const migrateButtons = buttonContainer.getElementsByClassName('migrate');
      for (let i = migrateButtons.length - 1; i >= 0; i--) {
        migrateButtons[i].remove();
      }
    } else {
      // there's already a row for the migrated name (unusual but OK), so merge them

      // migrate permissions from this row
      const ambiguousPermissionInputs = tr.getElementsByTagName("INPUT");
      const unambiguousPermissionInputs = newNameElement.getElementsByTagName("INPUT");
      for (let i = 0; i < ambiguousPermissionInputs.length; i++){
        if (ambiguousPermissionInputs[i].type == "checkbox") {
          unambiguousPermissionInputs[i].checked |= ambiguousPermissionInputs[i].checked;
        }
        newNameElement.classList.add('highlight-entry');
      }

      // remove this row
      tr.parentNode.removeChild(tr);
    }
    Behaviour.applySubtree(table, true);

    let hasAmbiguousRows = false;

    for (let i = 0; i < tableRows.length; i++) {
      if (tableRows[i].getAttribute('name') !== null && tableRows[i].getAttribute('name').startsWith('[EITHER')) {
        hasAmbiguousRows = true;
      }
    }
    if (!hasAmbiguousRows) {
      const alertElements = document.getElementsByClassName("alert");
      for (let i = 0; i < alertElements.length; i++) {
        if (alertElements[i].hasAttribute('data-table-id') && alertElements[i].getAttribute('data-table-id') === table.getAttribute('id')) {
          alertElements[i].style.display = 'none'; // TODO animate this?
        }
      }
    }

    return false;
  };
  e = null; // avoid memory leak
});


Behaviour.specify(".rsp-navigation__button-entry-down", "RoleBasedAuthorizationStrategy", 0, function(button) {
  button.onclick = function() {
    const container = button.closest(".rsp-roles-container");
    const table = container.querySelector("table");
    const tableId = table.id;
    const navgiationDiv = button.closest(`.rsp-navigation__entries`);
    const select = navgiationDiv.querySelector(".rsp-navigation__select");
    const page = parseInt(select.value) + 1;
    const userInputFilter = container.querySelector(".user-input-filter");
    const userFilter = userInputFilter != null ? userInputFilter.value.toUpperCase() : "";
    filterUsers(userFilter, tableId, page);
  }
});

Behaviour.specify(".rsp-navigation__button-entry-up", "RoleBasedAuthorizationStrategy", 0, function(button) {
  button.onclick = function() {
    const container = button.closest(".rsp-roles-container");
    const table = container.querySelector("table");
    const tableId = table.id;
    const navgiationDiv = button.closest(`.rsp-navigation__entries`);
    const select = navgiationDiv.querySelector(".rsp-navigation__select");
    const page = parseInt(select.value) - 1;
    const userInputFilter = container.querySelector(".user-input-filter");
    const userFilter = userInputFilter != null ? userInputFilter.value.toUpperCase() : "";
    filterUsers(userFilter, tableId, page);
  }
});

Behaviour.specify(".rsp-navigation__select", "RoleBasedAuthorizationStrategy", 0, function(select) {
  select.onchange = function() {
    const container = select.closest(".rsp-roles-container");
    const table = container.querySelector("table");
    const tableId = table.id;
    const page = parseInt(select.value);
    const userInputFilter = container.querySelector(".user-input-filter");
    const userFilter = userInputFilter != null ? userInputFilter.value.toUpperCase() : "";
    filterUsers(userFilter, tableId, page);
  }
});

function deletePermissionEntry(json, name, type) {
  let index = null;
  for (const [i, line] of json.entries()) {
    if (line["name"] === name && line["type"] === type) {
      index = i;
    }
  }
  if (index !== null) {
    json.splice(index, 1);
  }
}

function addPermissionEntry(json, name, type) {
  const entry = {};
  entry["name"] = name;
  entry["type"] = type;
  entry["roles"] = [];
  json.unshift(entry);
  return entry;
}

function sortJson(json) {
  json.sort(function(a, b) {
    if (a["type"] === "USER" && a["name"] === "anonymous") {
      return -1;
    }
    if ((a["type"] === "GROUP" && a["name"] === "authenticated") &&
    (b["type"] === "USER" && b["name"] === "anonymous")) {
      return 1;
    }
    if ((a["type"] === "GROUP" && a["name"] == "authenticated") &&
    (b["type"] !== "USER" || b["name"] !== "anonymous")) {
      return -1;
    }
    if (a["type"] === "GROUP" && a["name"] === "authenticated") {
      return -1;
    }
    if (b["type"] === "USER" && b["name"] === "anonymous") {
      return 1;
    }
    if ((b["type"] === "GROUP" && b["name"] === "authenticated") &&
    (a["type"] === "USER" && a["name"] === "anonymous")) {
      return 1;
    }
    if ((b["type"] === "GROUP" && b["name"] == "authenticated") &&
    (a["type"] !== "USER" || a["name"] !== "anonymous")) {
      return 1;
    }
    if (b["type"] === "GROUP" && b["name"] === "authenticated") {
      return 1;
    }
    if (a["type"] === b["type"]) {
      return a["name"] < b["name"] ? -1 : 1;
    }
    return a["type"] > b["type"] ? -1 : 1;
  });
}

function findPermissionEntry(json, name, type, create = false) {
  let entry = null;
  for (const line of json) {
    if (line["name"] === name && line["type"] === type) {
      entry = line;
      break;
    }
  }
  if (entry == null && create) {
    entry = addPermissionEntry(json, name, type);
  }
  return entry;
}

// finds the roles for the given entry
// returns null when there is no entry found
function addFixedEntry(json, name, type, title, icon) {
  const entry = findPermissionEntry(json, name, type, true);
  entry["title"] = title;
  entry["icon"] = icon;
  return entry["roles"];
}

function generateSVGIcon(iconName) {
  const icons = document.querySelector("#assign-roles-icons");

  return icons.content.querySelector(`#${iconName}`).cloneNode(true);
}

function updateEntryNavigation(tableId, count, current) {
  const navgiationDiv = document.querySelector(`#${tableId}-container .rsp-navigation__entries`);
  if (navgiationDiv === null) {
    return;
  }
  const totalPages = Math.ceil(count / maxRows);
  if (totalPages == 1) {
    return
  }
  navgiationDiv.classList.toggle("jenkins-hidden", false);
  const select = navgiationDiv.querySelector(".rsp-navigation__select");
  const upButton = navgiationDiv.querySelector(".rsp-navigation__button-entry-up");
  const downButton = navgiationDiv.querySelector(".rsp-navigation__button-entry-down");
  if (current + 1 === 1) {
    upButton.disabled = true;
  } else {
    upButton.disabled = false;
  }
  if (current + 1 === totalPages) {
    downButton.disabled = true;
  } else {
    downButton.disabled = false;
  }
  if (select.options.length != totalPages) {
    if (totalPages > select.options.length) {
      for (let i = select.options.length + 1; i <= totalPages; i++) {
        const option = document.createElement("option");
        option.value = i - 1;
        option.text = i;
        select.add(option);
      }
    }
    if (totalPages < select.options.length) {
      for (let i = select.options.length - 1; i >= totalPages; i--) {
        select.remove(i);
      }
    }
  }
  select.value = current;
}

function showEntries(tableId, json, startPage) {
  const dataHolder = document.getElementById("assign-roles");
  const container = document.getElementById(`${tableId}-container`);
  const table = container.querySelector("table");
  const tableHighLighter = container.dataset.highlighter;
  const tbody = document.createElement("tbody");
  const template = document.getElementById(container.dataset.template).content.firstElementChild;

  const start = startPage * maxRows;
  const end = Math.min(start + maxRows, json.length);
  for (let i = start; i < end; i++) {
    const line = json[i];
    const name = line["name"];
    const type = line["type"];
    const roles = line["roles"];
    const title = line["title"];
    const icon = line["icon"];
    insertRow(template, tbody, tableHighLighter, name, type, roles, title, icon);
  }
  table.replaceChild(tbody, table.tBodies[0]);
  Behaviour.applySubtree(table, true);
  updateEntryNavigation(tableId, json.length, startPage);
  if (tbody.children.length >= footerLimit) {
    table.tFoot.classList.remove("jenkins-hidden");
  }
}

function loadTable(tableId, param, globalVarName) {
  const dataHolder = document.getElementById("assign-roles");
  const fetchUrl = dataHolder.dataset.fetchUrl;

  const params = new URLSearchParams({ type: param });
  fetch(fetchUrl + "?" + params)
  .then((rsp) => rsp.json())
  .then((json) => {
    roleStrategyEntries[tableId] = json;
    addFixedEntry(json, "anonymous", "USER", dataHolder.dataset.textAnonymous, "rsp-person-icon");
    addFixedEntry(json, "authenticated", "GROUP", dataHolder.dataset.textAuthenticated, "rsp-people-icon");
    sortJson(json);
    showEntries(tableId, json, 0);
  });
}


Behaviour.specify("#rsp-roles-save", "RoleBasedAuthorizationStrategy", 0, function(button) {
  button.onclick = function(event) {
    const form = document.getElementById("rsp-roles-form");
    const input = form.querySelector("input");
    input.value = JSON.stringify(roleStrategyEntries);
    form.requestSubmit();
  }
});

Behaviour.specify("#rsp-roles-apply", "RoleBasedAuthorizationStrategy", 0, function(button) {
  button.onclick = function(event) {
    const form = document.getElementById("rsp-roles-form");
    const url = form.action;
    const formData = new FormData();
    const rolesMapping = {
      "rolesMapping": roleStrategyEntries
    }
    formData.append("json", JSON.stringify(rolesMapping));
    fetch(url, {
      method: "POST",
      headers: crumb.wrap({}),
      body: formData
    }).then((rsp => {
      if (rsp.ok) {
        notificationBar.show(button.dataset.message, notificationBar.SUCCESS)
      } else {
        notificationBar.error("Failed to apply changes.", notificationBar.ERROR)
      }
    }));
  }
});


document.addEventListener('DOMContentLoaded', function() {

  const dataHolder = document.getElementById("assign-roles");
  maxRows = parseInt(dataHolder.dataset.maxRows);

  // global roles initialization
  const globalRoleInputFilter = document.getElementById('globalRoleInputFilter');
  if (globalRoleInputFilter) {
    if (parseInt(globalRoleInputFilter.getAttribute("data-initial-size")) >= 10) {
        globalRoleInputFilter.style.display = "block"
    }
    const globalUserInputFilter = document.getElementById('globalUserInputFilter');
    if (globalUserInputFilter && parseInt(globalUserInputFilter.getAttribute("data-initial-size")) >= 10) {
        globalUserInputFilter.style.display = "block"
    }

    globalTableHighlighter = new TableHighlighter('globalRoles', 0);
    loadTable("globalRoles", "globalRoles");
  }

  // item roles initialization
  const itemRoleInputFilter = document.getElementById('itemRoleInputFilter');
  if (itemRoleInputFilter) {
    if (parseInt(itemRoleInputFilter.getAttribute("data-initial-size")) >= 10) {
        itemRoleInputFilter.style.display = "block"
    }
    const itemUserInputFilter = document.getElementById('itemUserInputFilter');
    if (itemUserInputFilter && parseInt(itemUserInputFilter.getAttribute("data-initial-size")) >= 10) {
        itemUserInputFilter.style.display = "block"
    }

    itemTableHighlighter = new TableHighlighter('projectRoles', 0);
    loadTable("projectRoles", "projectRoles");
  }

  // agent roles initialization
  const agentRolesTable = document.getElementById('agentRoles');
  if (agentRolesTable) {
    agentTableHighlighter = new TableHighlighter('agentRoles', 0);
    loadTable("agentRoles", "slaveRoles");
  }
});