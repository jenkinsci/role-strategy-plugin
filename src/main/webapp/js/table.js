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
window.rspUpdateCardBorders = function (container) {
  if (!container) container = document;
  container.querySelectorAll(".rsp-cards").forEach((cards) => {
    const visible = cards.querySelectorAll(".rsp-card:not(.rsp-card--hidden)");
    // Reset all
    cards.querySelectorAll(".rsp-card").forEach((c) => {
      c.classList.remove(
        "rsp-card--connected-top",
        "rsp-card--connected-bottom",
      );
    });
    visible.forEach((card, i) => {
      if (i > 0) card.classList.add("rsp-card--connected-top");
      if (i < visible.length - 1)
        card.classList.add("rsp-card--connected-bottom");
    });
  });
};

window.toQueryString = function (params) {
  return "?" + new URLSearchParams(params).toString();
};

var escapeHTML = function (unsafe) {
  return unsafe.replace(/[&<>"']/g, function(m) {
    switch (m) {
      case "&":
        return "&amp;";
      case "<":
        return "&lt;";
      case ">":
        return "&gt;";
      case '"':
        return "&quot;";
      default:
        return "&#039;";
    }
  });
};

// Reconfigure Tippy tooltips that live inside <dialog> elements so they
// (a) attach to the dialog (HTML5 top-layer) instead of <body>, and
// (b) use position:fixed so they're not clipped by the dialog's overflow.
// Core's tooltip registrar runs at Behaviour priority 1000; we run after.
(function () {
  if (typeof Behaviour === "undefined") return;
  const reconfigure = (element) => {
    const dialog = element.closest("dialog");
    if (!dialog || !element._tippy) return false;
    if (element._tippy.props.appendTo === dialog) return true;
    element._tippy.setProps({
      appendTo: dialog,
      popperOptions: { strategy: "fixed" },
    });
    return true;
  };
  Behaviour.specify(
    ".rsp-perm-info[data-html-tooltip]",
    "rsp-dialog-tooltip-fix",
    1001,
    (element) => {
      if (!reconfigure(element)) {
        // Tippy may not be initialised on this pass; retry once on next frame.
        requestAnimationFrame(() => reconfigure(element));
      }
    },
  );
})();
