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

// Update connected card border classes based on visible cards
var rspUpdateCardBorders = function(container) {
  if (!container) container = document;
  container.querySelectorAll(".rsp-cards").forEach((cards) => {
    const visible = cards.querySelectorAll(".rsp-card:not(.rsp-card--hidden)");
    // Reset all
    cards.querySelectorAll(".rsp-card").forEach((c) => {
      c.classList.remove("rsp-card--connected-top", "rsp-card--connected-bottom");
    });
    visible.forEach((card, i) => {
      if (i > 0) card.classList.add("rsp-card--connected-top");
      if (i < visible.length - 1) card.classList.add("rsp-card--connected-bottom");
    });
  });
};

function debounce(func, timeout = 300) {
  let timer;
  return (...args) => {
    clearTimeout(timer);
    timer = setTimeout(() => { func.apply(this, args); }, timeout);
  };
}

var doubleEscapeHTML = function(unsafe) {
  return escapeHTML(escapeHTML(unsafe));
};

var toQueryString = function(params) {
  return '?' + new URLSearchParams(params).toString();
};

function getPreviousSiblings(elem) {
  const sibs = [];
  while (elem = elem.previousSibling) {
    if (elem.nodeType === 3) continue;
    sibs.push(elem);
  }
  return sibs;
}

/* TableHighlighter - used by Permission Templates page */
class TableHighlighter {
  constructor(id, decalx) {
    this.table = document.getElementById(id);
    this.decalx = decalx;
    const trs = this.table.querySelectorAll('tbody tr');
    for (const row of trs) {
      this.scan(row);
    }
  }

  scan(tr) {
    const descendants = tr.querySelectorAll('.rsp-highlight-input');
    for (const td of descendants) {
      td.addEventListener('mouseenter', this.highlight);
      td.addEventListener('mouseleave', this.highlight);
    }
    const stopNodes = tr.querySelectorAll("div.rsp-remove");
    const lastStop = stopNodes[stopNodes.length - 1];
    if (lastStop != null) {
      const td = lastStop.closest('td');
      td.addEventListener('mouseenter', this.highlightRowOnly);
      td.addEventListener('mouseleave', this.highlightRowOnly);
    }
  }

  highlightRowOnly = (e) => {
    const enable = e.type === 'mouseenter';
    const tr = e.target.closest("TR");
    tr.classList.toggle('highlighted', enable);
  }

  highlight = (e) => {
    const enable = e.type === 'mouseenter';
    if (e.target.tagName === 'TD') {
      const td = e.target;
      const tr = td.parentNode;
      const trs = this.table.querySelectorAll('tr.highlight-row');
      const position = getPreviousSiblings(td).length;

      let p = 0;
      for (const row of trs) {
        let num = position;
        if (p === 0) num = num - this.decalx;
        p++;
        const element = row.childNodes[num];
        if (element) element.classList.toggle('highlighted', enable);
      }
      tr.classList.toggle('highlighted', enable);
    }
  }
}
