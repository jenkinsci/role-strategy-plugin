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


Behaviour.specify("img.icon-pencil", 'RoleBasedAuthorizationStrategy', 0, function (e) {
  e.onclick = function () {
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
  let table = findAncestor(inputNode,"TABLE");
  inputNode.innerHTML = '<a href="#" class="patternAnchor">&quot;' + pattern.escapeHTML() + '&quot;</a><input class="patternEdit" type="hidden" name="[pattern]" value="' + pattern + '"/>';
  if (table.getAttribute('id') === "projectRoles") {
    bindListenerToPattern(inputNode.children[0]);
  } else {
    bindAgentListenerToPattern(inputNode.children[0]);
  }
  let row = findAncestor(inputNode, "TR");
  for (td of row.getElementsByClassName('permissionInput')) {
    updateTooltip(row, td, pattern);
  }
  Behaviour.applySubtree(row,true);
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
        td.setAttribute('tooltip', tooltip); // before 2.335 -- TODO remove once baseline is new enough
        td.nextSibling.setAttribute('tooltip', tooltip); // 2.335+
      }
    }
  }
}


Behaviour.specify(
  ".role-strategy-add-button", "RoleBasedAuthorizationStrategy", 0, function(elem) {
    makeButton(elem, function(e) {
      let tableId = elem.getAttribute("data-table-id");
      let table = document.getElementById(tableId);
      let masterId = elem.getAttribute("data-master-id");
      let master = window[masterId];
      let highlighter = window[elem.getAttribute("data-highlighter")];
      addButtonAction(e, master, table, highlighter, tableId);
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

addButtonAction = function (e, master, table, tableHighlighter, tableId) {
  let tbody = table.tBodies[0];
  let roleInput = document.getElementById(tableId+'text')
  let name = roleInput.value;
  if (name=="") {
    alert("Please enter a role name");
    return;
  }
  if (findElementsBySelector(tbody,"TR").find(function(n){return n.getAttribute("name")=='['+name+']';})!=null) {
    alert("Entry for '"+name+"' already exists");
    return;
  }
  let pattern = "";
  if (tableId !== "globalRoles") {
    let patternInput = document.getElementById(tableId+'pattern')
    pattern = patternInput.value;
    if (pattern=="") {
      alert("Please enter a pattern");
      return;
    }
  }

  let copy = document.importNode(master,true);
  copy.removeAttribute("id");
  copy.removeAttribute("style");
  let child = copy.childNodes[1];
  child.textContent = escapeHTML(name);
  if (tableId !== "globalRoles") {
    let doubleQuote = '"';
    copy.getElementsByClassName("patternAnchor")[0].textContent = doubleQuote + escapeHTML(pattern) + doubleQuote;
    copy.getElementsByClassName("patternEdit")[0].value = pattern;
  }

  let children = copy.getElementsByClassName("permissionInput");
  for (let child of children) {
    if (child.hasAttribute('data-tooltip-template')) {
      child.setAttribute("data-tooltip-template", child.getAttribute("data-tooltip-template").replace(/{{ROLE}}/g, doubleEscapeHTML(name)).replace("{{PATTERN}}", doubleEscapeHTML(pattern)));
    }
  }

  if (tableId !== "globalRoles") {
    spanElement = copy.childNodes[2].childNodes[0].childNodes[1];
    if (tableId === "projectRoles") {
      bindListenerToPattern(spanElement.childNodes[0]);
    } else {
      bindAgentListenerToPattern(spanElement.childNodes[0]);
    }
  }

  copy.setAttribute("name",'['+name+']');
  tbody.appendChild(copy);
  tableHighlighter.scan(copy);
  Behaviour.applySubtree(findAncestor(copy,"TABLE"), true);
}


Behaviour.specify(".global-matrix-authorization-strategy-table A.remove", 'RoleBasedAuthorizationStrategy', 0, function(e) {
  e.onclick = function() {
    let table = findAncestor(this,"TABLE");
    let tableId = table.getAttribute("id");
    let tr = findAncestor(this,"TR");
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
  let row = findAncestor(e,"TR");
  let pattern = getPattern(row);
  let td = findAncestor(e,"TD");
  updateTooltip(row, td, pattern);
  e.onchange = function() {
    Behaviour.applySubtree(findAncestor(row,"TABLE"),true);
    return true;
  };
});
