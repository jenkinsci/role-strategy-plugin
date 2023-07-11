/*
 * The MIT License
 *
 * Copyright (c) 2010, Manufacture Française des Pneumatiques Michelin, Thomas Maurel
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

function getPreviousSiblings(elem, filter) {
    let sibs = [];
    while (elem = elem.previousSibling) {
        if (elem.nodeType === 3) continue; // text node
        sibs.push(elem);
    }
    return sibs;
}

class TableHighlighter {

  constructor(id, decalx) {
    this.table = document.getElementById(id);
    this.decalx = decalx;
    let trs = this.table.querySelectorAll('tbody tr');
    for (let row of trs){
        this.scan(row);
    }
  };

  scan(tr) {
    let descendants = tr.querySelectorAll('.rsp-highlight-input');
    for (let td of descendants) {
        td.addEventListener('mouseenter', this.highlight);
        td.addEventListener('mouseleave', this.highlight);
    }
    let stopNodes = tr.querySelectorAll("div.rsp-remove");
    let lastStop = stopNodes[stopNodes.length - 1];
    if (lastStop != null) {
      let td = lastStop.closest('td');
      td.addEventListener('mouseenter', this.highlightRowOnly);
      td.addEventListener('mouseleave', this.highlightRowOnly);
    }
  };

  highlightRowOnly = e => {
    let enable = e.type === 'mouseenter';
    let tr = findAncestor(e.target, "TR")
    if (enable) {
      tr.classList.add('highlighted');
    } else {
      tr.classList.remove('highlighted');
    }
  }

  highlight = e => {
    let enable = e.type === 'mouseenter';
    if (e.target.tagName === 'TD') {
      let td = e.target;
      let tr = td.parentNode;
      let trs = this.table.querySelectorAll('tr.highlight-row');
      let position = getPreviousSiblings(td).length;

      let p = 0;
      for (let row of trs) {
        let num = position;
        if (p==0) num = num - this.decalx;
        p++;
        let element = row.childNodes[num];
        if (enable) {
          element.classList.add('highlighted');
        } else {
          element.classList.remove('highlighted');
        }
      }
      if (enable) {
        tr.classList.add('highlighted');
      } else {
        tr.classList.remove('highlighted');
      }
    }
  };
};

var doubleEscapeHTML = function(unsafe) {
  return escapeHTML(escapeHTML(unsafe));
};

var escapeHTML = function(unsafe) {
  return unsafe.replace(/[&<>"']/g, function(m) {
    switch (m) {
      case '&':
        return '&amp;';
      case '<':
        return '&lt;';
      case '>':
        return '&gt;';
      case '"':
        return '&quot;';
      default:
        return '&#039;';
    }
  });
};
