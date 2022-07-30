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

class TableHighlighter {

    constructor(id, decalx, decaly) {
        this.table = $(id);
        this.decalx = decalx;
        this.decaly = decaly;
        var trs = $$('#'+this.table.id+' tr');
        for (var p=this.decaly;p<trs.length;++p){
            this.scan(trs[p]);
        }
    };

    scan(tr) {
        var element = $(tr);
        var descendants = element.getElementsByTagName('input');
        for (var q=0;q<descendants.length;++q) {
            var td = $(descendants[q]);
            // before 2.335 -- TODO remove once baseline is new enough
            td.addEventListener('mouseover', this.highlight);
            td.addEventListener('mouseout', this.highlight);
            // For Jenkins 2.335+
            td.nextSibling.addEventListener('mouseover', this.highlight);
            td.nextSibling.addEventListener('mouseout', this.highlight);
        }
    };

    highlight = e => {
        var td = findAncestor(Event.element(e), "TD")
        var tr = td.parentNode;
        var trs = $$('#'+this.table.id+' tr');
        var position = td.previousSiblings().length;

        for (var p=this.decaly-1;p<trs.length;++p){
            var element = $(trs[p]);
            var num = position;
            if (p==1) num = num - this.decalx;
            element.immediateDescendants()[num ].toggleClassName('highlighted');
        }
        tr.toggleClassName('highlighted');
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

var preventFormSubmit = function(e) {
  var key = e.charCode || e.keyCode || 0;     
  if (key == 13) {
    e.preventDefault();
    e.stopImmediatePropagation();
    var inputNode = e.target.parentNode;
    inputNode.innerHTML = '<a href="#" class="patternAnchor">&quot;' + inputNode.childNodes[0].value.escapeHTML() + '&quot;</a><input type="hidden" name="[pattern]" value="' + inputNode.childNodes[0].value + '"/>';
    bindListenerToPattern(inputNode.children[0]);
  }
};
 
