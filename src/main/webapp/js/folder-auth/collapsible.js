'use strict';

const cols = document.getElementsByClassName("collapsible");
for (let i = 0; i < cols.length; i++) {
    cols[i].addEventListener("click", function () {
        this.classList.toggle("active");
        const content = this.nextElementSibling;
        if (content.style.display === "block") {
            content.style.display = "none";
        } else {
            content.style.display = "block";
        }
    });
}
