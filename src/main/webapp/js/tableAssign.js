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

function filterUsers(filter, table) {
  for (var i = 1; i < table.rows.length; i++) {
    var row = table.rows[i];
    if (row.classList.contains("group-row")) {
      continue;
    }
    var userCell = row.cells[1].textContent.toUpperCase();
    if (userCell.indexOf(filter) > -1) {
      row.style.display = "";
    } else {
      row.style.display = "none";
    }
  }
}

function filterRoles(filter, table) {
  var rowCount = table.rows.length;
  var startColumn = 2; // column 0 is the delete button, column 1 contains the user/group
  var endColumn = table.rows[0].cells.length - 2; // last column is the delete button
  for (var c = startColumn; c <= endColumn; c++) {
    var shouldFilter = true;
    if (table.rows[0].cells[c].textContent.toUpperCase().indexOf(filter) > -1) {
      shouldFilter = false;
    }
    for (var r = 0; r < rowCount; r++) {
      if (shouldFilter) {
        table.rows[r].cells[c].style.display = "none";
      } else {
        table.rows[r].cells[c].style.display = "";
      }
    }
  }
}

Behaviour.specify(".user-input-filter", "RoleBasedAuthorizationStrategy", 0, function(e) {
  e.onkeyup = function() {
      let filter = e.value.toUpperCase();
      let table = document.getElementById(e.getAttribute("data-table-id"));
      filterUsers(filter, table);
  }
});


Behaviour.specify(".role-input-filter", "RoleBasedAuthorizationStrategy", 0, function(e) {
  e.onkeyup = function() {
      let filter = e.value.toUpperCase();
      let table = document.getElementById(e.getAttribute("data-table-id"));
      filterRoles(filter, table);
  }
});

Behaviour.specify(
  ".role-strategy-add-button", "RoleBasedAuthorizationStrategy", 0, function(elem) {
    makeButton(elem, function(e) {
      let tableId = elem.getAttribute("data-table-id");
      let table = document.getElementById(tableId);
      let templateId = elem.getAttribute("data-template-id");
      let template = window[templateId];
      let highlighter = window[elem.getAttribute("data-highlighter")];
      addButtonAction(e, template, table, highlighter, tableId);
      let tbody = table.tBodies[0];
      if (tbody.children.length >= filterLimit) {
        let userfilters = document.querySelectorAll(".user-filter")
        for (var q=0;q<userfilters.length;++q) {
          let filter = userfilters[q];
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


addButtonAction = function (e, template, table, tableHighlighter, tableId) {
    let tbody = table.tBodies[0];
    let userInput = document.getElementById(tableId+'text')
    let name = userInput.value;
    if (name=="") {
      alert("Please enter a user/group name");
      return;
    }
    if (findElementsBySelector(table,"TR").find(function(n){return n.getAttribute("name")=='['+name+']';})!=null) {
      alert("Entry for '"+name+"' already exists");
      return;
    }

    copy = document.importNode(template,true);
    copy.removeAttribute("id");
    copy.removeAttribute("style");

    let children = copy.childNodes;
    children.forEach(function(item){
      item.outerHTML= item.outerHTML.replace(/{{USER}}/g, doubleEscapeHTML(name));
    });

    copy.childNodes[1].innerHTML = escapeHTML(name);
    copy.setAttribute("name",'['+name+']');
    tbody.appendChild(copy);
    Behaviour.applySubtree(table, true);
    tableHighlighter.scan(copy);
}

Behaviour.specify(".global-matrix-authorization-strategy-table A.remove", 'RoleBasedAuthorizationStrategy', 0, function(e) {
  e.onclick = function() {
    let table = findAncestor(this,"TABLE");
    let tableId = table.getAttribute("id");
    let tr = findAncestor(this,"TR");
    parent = tr.parentNode;
    parent.removeChild(tr);
    if (parent.children.length < filterLimit) {
      let userfilters = document.querySelectorAll(".user-filter")
      for (var q=0;q<userfilters.length;++q) {
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
    let dirtyButton = document.getElementById("rs-dirty-indicator");
    dirtyButton.dispatchEvent(new Event('click'));
    return false;
  }
});

Behaviour.specify(".global-matrix-authorization-strategy-table TR.permission-row", "RoleBasedAuthorizationStrategy", 0, function(e) {
    if (e.getAttribute('name') === '__unused__') {
          return;
    }
    if (!e.hasAttribute('data-checked')) {
      FormChecker.delayedCheck(e.getAttribute('data-descriptor-url') + "/checkName?value="+encodeURIComponent(e.getAttribute("name")),"POST",e.childNodes[1]);
      e.setAttribute('data-checked', 'true');
    }
});


document.addEventListener('DOMContentLoaded', function() {
    // global roles initialization
    var globalRoleInputFilter = document.getElementById('globalRoleInputFilter');
    if (parseInt(globalRoleInputFilter.getAttribute("data-initial-size")) >= 20) {
        globalRoleInputFilter.style.display = "block"
    }
    var globalUserInputFilter = document.getElementById('globalUserInputFilter');
    if (parseInt(globalUserInputFilter.getAttribute("data-initial-size")) >= 10) {
        globalUserInputFilter.style.display = "block"
    }

    newGlobalRowTemplate = document.getElementById('newGlobalRowTemplate');
    var tbody = newGlobalRowTemplate.parentNode;
    tbody.removeChild(newGlobalRowTemplate);

    globalTableHighlighter = new TableHighlighter('globalRoles', 0);

    // item roles initialization
    var itemRoleInputFilter = document.getElementById('itemRoleInputFilter');
    if (parseInt(itemRoleInputFilter.getAttribute("data-initial-size")) >= 20) {
        itemRoleInputFilter.style.display = "block"
    }
    var itemUserInputFilter = document.getElementById('itemUserInputFilter');
    if (parseInt(itemUserInputFilter.getAttribute("data-initial-size")) >= 10) {
        itemUserInputFilter.style.display = "block"
    }

    newItemRowTemplate = document.getElementById('newItemRowTemplate');

    tbody = newItemRowTemplate.parentNode;
    tbody.removeChild(newItemRowTemplate);

    itemTableHighlighter = new TableHighlighter('projectRoles', 0);

    // agent roles initialization
    newAgentRowTemplate = document.getElementById('newAgentRowTemplate');

    tbody = newAgentRowTemplate.parentNode;
    tbody.removeChild(newAgentRowTemplate);

    agentTableHighlighter = new TableHighlighter('agentRoles', 0);
});