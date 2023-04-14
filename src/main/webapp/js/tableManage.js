/*
 * The MIT License
 *
 * Copyright (c) 2022, Markus Winter
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

// number of lines required for the role filter to get enabled
var filterLimit = 10;
// number of lines required for the footer to get displayed
var footerLimit = 20;
var globalTableHighlighter;
var newGlobalRoleTemplate;
var projectTableHighlighter;
var newItemRoleTemplate;
var agentTableHighlighter;
var newAgentRoleTemplate;
var modal;
var overlay;
var closeModalBtn;



function filterRows(filter, table) {
  for (let row of table.tBodies[0].rows) {
    let userCell = row.cells[1].textContent.toUpperCase();
    if (userCell.indexOf(filter) > -1) {
      row.style.display = "";
    } else {
      row.style.display = "none";
    }
  }
}


getPattern = function(row) {
  let pattern = "";
  patternEditInputs = row.getElementsByClassName("patternEdit");
  if (patternEditInputs.length > 0) {
    pattern = patternEditInputs[0].value;
  }
  return pattern;
}


Behaviour.specify(".row-input-filter", "RoleBasedAuthorizationStrategy", 0, function(e) {
  e.onkeyup = function() {
    let filter = e.value.toUpperCase();
    let table = document.getElementById(e.getAttribute("data-table-id"));
    filterRows(filter, table);
  }
});


Behaviour.specify("img.icon-pencil", 'RoleBasedAuthorizationStrategy', 0, function(e) {
  e.onclick = function() {
    let inputNode = this.parentNode.childNodes[1];
    if (inputNode.childNodes.length == 2) {
      inputNode.innerHTML = '<input onkeypress="" type="text" name="[pattern]" value="' + inputNode.childNodes[1].value + '" size="' + (inputNode.childNodes[1].value.length + 10) + '"/>';
      inputNode.childNodes[0].onkeypress = preventFormSubmit;
    } else {
      endPatternInput(inputNode);
    }
    return false;
  }
});


preventFormSubmit = function(e) {
  var key = e.charCode || e.keyCode || 0;
  if (key == 13) {
    e.preventDefault();
    e.stopImmediatePropagation();
    var inputNode = e.target.parentNode;
    endPatternInput(inputNode);
  }
};

endPatternInput = function(inputNode) {
  let pattern = inputNode.childNodes[0].value;
  let table = findAncestor(inputNode, "TABLE");
  inputNode.innerHTML = '<div class="patternAnchor">&quot;' + pattern.escapeHTML() + '&quot;</div><input class="patternEdit" type="hidden" name="[pattern]" value="' + pattern + '"/>';
  if (table.getAttribute('id') === "projectRoles") {
    bindListenerToPattern(inputNode.children[0]);
  } else {
    bindAgentListenerToPattern(inputNode.children[0]);
  }
  let row = findAncestor(inputNode, "TR");
  for (td of row.getElementsByClassName('permissionInput')) {
    updateTooltip(row, td, pattern);
  }
  Behaviour.applySubtree(row, true);
}

updateTooltip = function(tr, td, pattern) {
  let tooltipTemplate = td.getAttribute("data-tooltip-template");
  let impliedByString = td.getAttribute('data-implied-by-list');
  let impliedByList = impliedByString.split(" ");
  let input = td.getElementsByTagName('INPUT')[0];
  input.disabled = false;
  let tooltip = tooltipTemplate.replace("{{PATTERNTEMPLATE}}", doubleEscapeHTML(pattern)).replace("{{GRANTBYOTHER}}", "");
  td.setAttribute("tooltip", tooltip);

  for (let permissionId of impliedByList) {
    let reference = tr.querySelector("td[data-permission-id='" + permissionId + "'] input");
    if (reference !== null) {
      if (reference.checked) {
        input.disabled = true;
        tooltip = tooltipTemplate.replace("{{PATTERNTEMPLATE}}", doubleEscapeHTML(pattern)).replace("{{GRANTBYOTHER}}", " is granted through another permission");;
        td.nextSibling.setAttribute('tooltip', tooltip); // 2.335+
      }
    }
  }
}


Behaviour.specify(
  ".role-strategy-add-button", "RoleBasedAuthorizationStrategy", 0,
  function(elem) {
    makeButton(elem, function(e) {
      let tableId = elem.getAttribute("data-table-id");
      let table = document.getElementById(tableId);
      let templateId = elem.getAttribute("data-template-id");
      let template = window[templateId];
      let highlighter = window[elem.getAttribute("data-highlighter")];
      addButtonAction(e, template, table, highlighter, tableId);
      let tbody = table.tBodies[0];
      if (tbody.children.length >= filterLimit) {
        let rolefilters = document.querySelectorAll(".row-filter");
        for (let filter of rolefilters) {
          if (filter.getAttribute("data-table-id") === tableId) {
            filter.style.display = "block";
          }
        }
      }
      if (tbody.children.length >= footerLimit) {
        table.tFoot.style.display = "table-footer-group";
      }
    });
  }
);

addButtonAction = function(e, template, table, tableHighlighter, tableId) {
  let tbody = table.tBodies[0];
  let roleInput = document.getElementById(tableId + 'text')
  let name = roleInput.value;
  if (name == "") {
    alert("Please enter a role name");
    return;
  }
  if (findElementsBySelector(tbody, "TR").find(function(n) {
      return n.getAttribute("name") == '[' + name + ']';
    }) != null) {
    alert("Entry for '" + name + "' already exists");
    return;
  }
  let pattern = "";
  if (tableId !== "globalRoles") {
    let patternInput = document.getElementById(tableId + 'pattern')
    pattern = patternInput.value;
    if (pattern == "") {
      alert("Please enter a pattern");
      return;
    }
  }

  let copy = document.importNode(template, true);
  copy.removeAttribute("id");
  copy.removeAttribute("style");
  let child = copy.childNodes[1];
  child.textContent = escapeHTML(name);
  if (tableId !== "globalRoles") {
    let doubleQuote = '"';
    copy.getElementsByClassName("patternAnchor")[0].textContent = doubleQuote + escapeHTML(pattern) + doubleQuote;
    copy.getElementsByClassName("patternEdit")[0].value = pattern;
  }

  let children = copy.childNodes
  children.forEach(function(item){
    item.outerHTML= item.outerHTML.replace(/{{ROLE}}/g, doubleEscapeHTML(name)).replace("{{PATTERN}}", doubleEscapeHTML(pattern));
  });

  if (tableId !== "globalRoles") {
    spanElement = copy.childNodes[2].childNodes[0].childNodes[1];
    if (tableId === "projectRoles") {
      bindListenerToPattern(spanElement.childNodes[0]);
    } else {
      bindAgentListenerToPattern(spanElement.childNodes[0]);
    }
  }

  copy.setAttribute("name", '[' + name + ']');
  tbody.appendChild(copy);
  tableHighlighter.scan(copy);
  Behaviour.applySubtree(findAncestor(copy, "TABLE"), true);
}


Behaviour.specify(".global-matrix-authorization-strategy-table A.remove", 'RoleBasedAuthorizationStrategy', 0, function(e) {
  e.onclick = function() {
    let table = findAncestor(this, "TABLE");
    let tableId = table.getAttribute("id");
    let tr = findAncestor(this, "TR");
    parent = tr.parentNode;
    parent.removeChild(tr);
    if (parent.children.length < filterLimit) {
      let userfilters = document.querySelectorAll(".row-filter")
      for (let filter of userfilters) {
        if (filter.getAttribute("data-table-id") === tableId) {
          filter.style.display = "none";
          let inputs = filter.getElementsByTagName("INPUT");
          inputs[0].value = ""
          let event = new Event("keyup");
          inputs[0].dispatchEvent(event);
        }
      }
    }
    if (parent.children.length < footerLimit) {
      table.tFoot.style.display = "none";
    }
    return false;
  }
});

Behaviour.specify(".global-matrix-authorization-strategy-table td.permissionInput input", 'RoleBasedAuthorizationStrategy', 0, function(e) {
  let row = findAncestor(e, "TR");
  let pattern = getPattern(row);
  let td = findAncestor(e, "TD");
  updateTooltip(row, td, pattern);
  e.onchange = function() {
    Behaviour.applySubtree(findAncestor(row, "TABLE"), true);
    return true;
  };
});


// methods for item roles
showMatchingProjects = function() {
  let pattern = this.textContent.substring(1, this.textContent.length - 1); // Ignore quotes for the pattern
  let maxItems = 15; // Maximum items to search for
  let url = 'strategy/getMatchingJobs';
  reqParams = {
    'pattern': pattern,
    'maxJobs': maxItems
  }

  fetch(url + toQueryString(reqParams)).then((rsp) => {
    if (rsp.ok) {
      rsp.json().then((responseJson) => {
        let matchingItems = responseJson.matchingJobs;
        let itemCount = responseJson.itemCount;

        if (matchingItems != null) {
          showItemsModal(matchingItems, itemCount, maxItems, pattern);
        } else {
          showErrorMessageModal();
        }
      });
    } else {
      showErrorMessageModal();
    }
  });
}

showItemsModal = function(items, itemCount, maxItems, pattern) {
  let modalTitle = '';

  if (items.length > 0) {
    if (itemCount > items.length) {
      modalTitle += 'First ' + maxItems + ' items (out of ' + itemCount + ') matching';
    } else {
      modalTitle += 'Items matching';
    }
  } else {
    modalTitle = 'No items found matching';
  }
  modalTitle += ' "' + pattern + '"';
  showModal(modalTitle, items)
}

showErrorMessageModal = function() {
  alert('Unable to fetch matching Jobs.');
}

bindListenerToPattern = function(elem) {
  elem.addEventListener('click', showMatchingProjects);
}


// methods for agent roles
showMatchingAgents = function() {
  let pattern = this.textContent.substring(1, this.textContent.length - 1); // Ignore quotes for the pattern
  let maxAgents = 10; // Maximum agents to search for
  let url = 'strategy/getMatchingAgents';
  reqParams = {
    'pattern': pattern,
    'maxAgents': maxAgents
  }

  fetch(url + toQueryString(reqParams)).then((rsp) => {
    if (rsp.ok) {
      rsp.json().then((responseJson) => {
        let matchingAgents = responseJson.matchingAgents;
        let agentCount = responseJson.itemCount;

        if (matchingAgents != null) {
          showAgentsModal(matchingAgents, agentCount, maxAgents, pattern);
        } else {
          showAgentErrorMessageModal();
        }
      });
    } else {
      showAgentErrorMessageModal();
    }
  });
}

showAgentsModal = function(agents, agentCount, maxAgents, pattern) {
  let modalTitle = '';
  if (agents.length > 0) {
    if (agentCount > agents.length) {
      modalTitle += 'First ' + maxAgents + ' agents (out of ' + agentCount + ') matching';
    } else {
      modalTitle += 'Agents matching';
    }
  } else {
    modalTitle += 'No Agents found matching';
  }
  modalTitle += ' "' + pattern + '"';
  showModal(modalTitle, agents)
}

showModal = function(title, itemlist) {
  titleElement=document.getElementById("modaltitle");
  titleElement.textContent = title;

  messageElement=document.getElementById("modalmessage");
  messageElement.textContent = "";
  for (let item of itemlist) {
    line = document.createTextNode("- " + item);
    messageElement.appendChild(line);
    messageElement.appendChild(document.createElement("br"));
  }

  modal.style.display="flex";
  overlay.classList.remove("default-hidden");
}

showAgentErrorMessageModal = function() {
  alert('Unable to fetch matching Agents.');
}

bindAgentListenerToPattern = function(elem) {
  elem.addEventListener('click', showMatchingAgents);
}

closeModal = function () {
  modal.style.display="none";
  overlay.classList.add("default-hidden");
};

document.addEventListener('DOMContentLoaded', function() {
  // global roles initialization
  var globalRoleInputFilter = document.getElementById('globalRoleInputFilter');
  if (parseInt(globalRoleInputFilter.getAttribute("data-initial-size")) >= 10) {
    globalRoleInputFilter.style.display = "block"
  }
  newGlobalRoleTemplate = document.getElementById('newGlobalRoleTemplate');
  tbody = newGlobalRoleTemplate.parentNode;
  tbody.removeChild(newGlobalRoleTemplate);

  globalTableHighlighter = new TableHighlighter('globalRoles', 2);

  // item roles initialization
  var itemRoleInputFilter = document.getElementById('itemRoleInputFilter');
  if (parseInt(itemRoleInputFilter.getAttribute("data-initial-size")) >= 10) {
    itemRoleInputFilter.style.display = "block"
  }

  newItemRoleTemplate = document.getElementById('newItemRoleTemplate');
  var tbody = newItemRoleTemplate.parentNode;
  tbody.removeChild(newItemRoleTemplate);

  projectTableHighlighter = new TableHighlighter('projectRoles', 3);

  // Show jobs matching a pattern on click
  let projectRolesTable = document.getElementById('projectRoles')
  let itemPatterns = projectRolesTable.getElementsByClassName('patternAnchor');
  for (let pattern of itemPatterns) {
    bindListenerToPattern(pattern);
  }

  // agent roles initialization
  newAgentRoleTemplate = document.getElementById('newAgentRoleTemplate');
  tbody = newAgentRoleTemplate.parentNode;
  tbody.removeChild(newAgentRoleTemplate);

  agentTableHighlighter = new TableHighlighter('agentRoles', 3);
  // Show agents matching a pattern on click
  let agentRolesTable = document.getElementById('agentRoles')
  let agentPatterns = agentRolesTable.getElementsByClassName('patternAnchor');
  for (let pattern of agentPatterns) {
    bindAgentListenerToPattern(pattern);
  }

  //
  modal = document.querySelector(".modal");

  overlay = document.querySelector(".overlay");
  overlay.addEventListener("click", closeModal);

  closeModalBtn = document.querySelector(".btn-close");
  closeModalBtn.addEventListener("click", closeModal);

  document.addEventListener("keydown", function (e) {
    if (e.key === "Escape" && !modal.classList.contains("hidden")) {
      closeModal();
    }
  });
});