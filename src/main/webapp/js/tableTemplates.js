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

updateTooltip = function(tr, td) {
  let tooltipTemplate = td.getAttribute("data-tooltip-template");
  let impliedByString = td.getAttribute('data-implied-by-list');
  let impliedByList = impliedByString.split(" ");
  let input = td.getElementsByTagName('INPUT')[0];
  input.disabled = false;
  let tooltip = tooltipTemplate.replace("{{GRANTBYOTHER}}", "");
  td.setAttribute("tooltip", tooltip);

  for (let permissionId of impliedByList) {
    let reference = tr.querySelector("td[data-permission-id='" + permissionId + "'] input");
    if (reference !== null) {
      if (reference.checked) {
        input.disabled = true;
        tooltip = tooltipTemplate.replace("{{GRANTBYOTHER}}", " is granted through another permission");;
        td.nextSibling.setAttribute('data-html-tooltip', tooltip); // 2.335+
      }
    }
  }
}

Behaviour.specify(".row-input-filter", "RoleBasedAuthorizationStrategy", 0, function(e) {
  e.onkeyup = function() {
    let filter = e.value.toUpperCase();
    let table = document.getElementById(e.getAttribute("data-table-id"));
    filterRows(filter, table);
  }
});

Behaviour.specify(
  ".template-add-button", "RoleBasedAuthorizationStrategy", 0, function(elem) {
    elem.onclick = function(e) {
      let tableId = elem.getAttribute("data-table-id");
      let table = document.getElementById(tableId);
      let templateId = elem.getAttribute("data-template-id");
      let template = window[templateId].content.firstElementChild.cloneNode(true);
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
    }
  } 
);

addButtonAction = function (e, template, table, tableHighlighter, tableId) {
  let dataReference = e.target;
  let tbody = table.tBodies[0];

  dialog.prompt(dataReference.getAttribute('data-prompt')).then((name) => {
    name = name.trim();
    if (findElementsBySelector(tbody,"TR").find(function(n){return n.getAttribute("name")=='['+name+']';})!=null) {
      dialog.alert("Entry for '"+name+"' already exists");
      return;
    }

    let copy = document.importNode(template,true);
    let child = copy.childNodes[1];
    child.textContent = escapeHTML(name);

    let children = copy.getElementsByClassName("permissionInput");
    for (let child of children) {
      if (child.hasAttribute('data-tooltip-template')) {
        child.setAttribute("data-tooltip-template", child.getAttribute("data-tooltip-template").replace(/{{TEMPLATE}}/g, doubleEscapeHTML(name)));
      }
    }

    if (tableId !== "permissionTemplates") {
      spanElement = copy.childNodes[2].childNodes[0].childNodes[1];
    }

    copy.setAttribute("name",'['+name+']');
    tbody.appendChild(copy);
    if (tableHighlighter != null) {
      tableHighlighter.scan(copy);
    }
    Behaviour.applySubtree(copy.closest("TABLE"), true);
  })
}


Behaviour.specify(".global-matrix-authorization-strategy-table .rsp-remove", 'RoleBasedAuthorizationStrategy', 0, function(e) {
  e.onclick = function() {
    let inuse = e.getAttribute("data-is-used") === "true";
    if (inuse && !confirm("This template is used. Are you sure you want to delete it?")) {
      console.log("Not confirmed");
      return;
    }
    let table = this.closest("TABLE");
    let tableId = table.getAttribute("id");
    let tr = this.closest("TR");
    parent = tr.parentNode;
    parent.removeChild(tr);
    if (parent.children.length < filterLimit) {
      let userfilters = document.querySelectorAll(".row-filter")
      for (let filter of userfilters) {
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
    return false;
  }
});

Behaviour.specify(".global-matrix-authorization-strategy-table td.permissionInput input", 'RoleBasedAuthorizationStrategy', 0, function(e) {
  let table = e.closest("TABLE");
  if (table.hasClassName('read-only')) {
    // if this is a read-only UI (ExtendedRead / SystemRead), do not enable checkboxes
    return;
  }
  let row = e.closest("TR");
  let td = e.closest("TD");
  updateTooltip(row, td);
  e.onchange = function() {
    Behaviour.applySubtree(row.closest("TABLE"),true);
    return true;
  };
});


var templateTableHighlighter;
var newPermissionTemplate;

document.addEventListener('DOMContentLoaded', function() {
  let permissionInputFilter = document.getElementById('permissionInputFilter');
  if (parseInt(permissionInputFilter.getAttribute("data-initial-size")) >= filterLimit) {
    permissionInputFilter.style.display = "block"
  }
  newPermissionTemplate = document.getElementById('newPermissionTemplate');
  templateTableHighlighter = new TableHighlighter('permissionTemplates', 3);
});
