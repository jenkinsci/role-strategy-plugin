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


// Form check code
//========================================================
var FastFormChecker = {
    // pending requests
    queue : [],
    parallelChecks : 1,

    // conceptually boolean, but doing so create concurrency problem.
    // that is, during unit tests, the AJAX.send works synchronously, so
    // the onComplete happens before the send method returns. On a real environment,
    // more likely it's the other way around. So setting a boolean flag to true or false
    // won't work.
    inProgress : 0,

    /**
     * Schedules a form field check. Executions are serialized to reduce the bandwidth impact.
     *
     * @param url
     *      Remote doXYZ URL that performs the check. Query string should include the field value.
     * @param method
     *      HTTP method. GET or POST. I haven't confirmed specifics, but some browsers seem to cache GET requests.
     * @param target
     *      HTML element whose innerHTML will be overwritten when the check is completed.
     */
    delayedCheck : function(url, method, target) {
        if(url==null || method==null || target==null)
            return; // don't know whether we should throw an exception or ignore this. some broken plugins have illegal parameters
        this.queue.push({url:url, method:method, target:target});
        this.schedule();
    },

    sendRequest : function(url, params) {
        if (params.method != "get") {
            var idx = url.indexOf('?');
            params.parameters = url.substring(idx + 1);
            url = url.substring(0, idx);
        }
        new Ajax.Request(url, params);
    },

    schedule : function() {
        if (this.inProgress>=this.parallelChecks)  return;
        if (this.queue.length == 0) return;

        var next = this.queue.shift();
        this.sendRequest(next.url, {
            method : next.method,
            onComplete : function(x) {
                // updateValidationArea is only available in Jenkins 2.355+
                if (typeof(updateValidationArea) === typeof(Function)) {
                    updateValidationArea(next.target, x.responseText);
                } else {
                    applyErrorMessage(next.target, x);
                } 
                FastFormChecker.inProgress--;
                FastFormChecker.schedule();
                layoutUpdateCallback.call();
            }
        });
        this.inProgress++;
    }
}

