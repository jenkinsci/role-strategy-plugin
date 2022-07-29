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


addButtonAction = function (e, master, table, tableHighlighter) {
    var dataReference = e.target;
    var type = dataReference.getAttribute('data-type');
    var size = parseInt(dataReference.getAttribute('data-size'));
    
    var name = prompt(dataReference.getAttribute('data-prompt')).trim();
    if (name=="") {
      alert(dataReference.getAttribute('data-empty-message'));
      return;
    }
    if (findElementsBySelector(table,"TR").find(function(n){return n.getAttribute("name")=='['+type+':'+name+']';})!=null) {
      alert(dataReference.getAttribute('data-error-message'));
      return;
    }
    
    copy = document.importNode(master,true);
    copy.removeAttribute("id");
    copy.removeAttribute("style");
    
    var children = copy.childNodes;
    children.forEach(function(item){
      item.outerHTML= item.outerHTML.replace("{{USER}}", doubleEscapeHTML(name));
    });
    
    copy.childNodes[1].innerHTML = escapeHTML(name);
    copy.setAttribute("name",'['+type+':'+name+']');
    if (size < 20) {
      table.appendChild(copy);
    } else {
      table.insertBefore(copy,table.childNodes[table.rows.length-1]);
    }
    Behaviour.applySubtree(findAncestor(table,"TABLE"), true);
    tableHighlighter.scan(copy);
}


Behaviour.specify(".user-input-filter", "RoleBasedAuthorizationStrategy", 0, function(e) {
  e.onkeyup = function() {
      var filter = e.value.toUpperCase();
      var table = document.getElementById(e.getAttribute("data-table-id"));
      filterUsers(filter, table);
  }
});


Behaviour.specify(".role-input-filter", "RoleBasedAuthorizationStrategy", 0, function(e) {
  e.onkeyup = function() {
      var filter = e.value.toUpperCase();
      var table = document.getElementById(e.getAttribute("data-table-id"));
      filterRoles(filter, table);
  }
});


Behaviour.specify(
  ".global-matrix-authorization-strategy-table TR.permission-row", "RoleBasedAuthorizationStrategy", 0, function(e) {
    if (e.getAttribute('name') === '__unused__') {
          return;
    }
    if (!e.hasAttribute('data-checked')) {
      FormChecker.delayedCheck(e.getAttribute('data-descriptor-url') + "/checkName?value="+encodeURIComponent(e.getAttribute("name")),"POST",e.childNodes[1]);
      e.setAttribute('data-checked', 'true');
    }
  });
