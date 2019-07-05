"use strict";

// noinspection JSUnusedGlobalSymbols
const addGlobalRole = (postUrl) => {
    const roleName = document.getElementById('roleName').value;
    if (!roleName) {
        alert('Please enter a valid name for the role to be added.');
        return;
    }

    const response = {};
    response.name = roleName;
    response.permissions = [];

    const permissionCheckboxes = document.getElementsByName('globalPermissions');
    for (let i = 0; i < permissionCheckboxes.length; i++) {
        const checkbox = permissionCheckboxes[i];
        if (checkbox.checked) {
            response.permissions.push(checkbox.value);
        }
    }

    const xhr = new XMLHttpRequest();
    xhr.open('POST', postUrl, true);
    xhr.setRequestHeader('Content-Type', 'application/json');

    xhr.onload = () => {
        if (xhr.status === 200) {
            alert('Global role was added successfully');
            location.reload(); // refresh the page
        } else {
            alert('Unable to add the role\n' + xhr.responseText);
        }
    };

    // this is really bad.
    // See https://github.com/jenkinsci/jenkins/blob/75468da366c1d257a51655dcbe952d55b8aeeb9c/war/src/main/js/util/jenkins.js#L22
    const oldPrototype = Array.prototype.toJSON;
    delete Array.prototype.toJSON;

    try {
        xhr.send(JSON.stringify(response));
    } finally {
        Array.prototype.toJSON = oldPrototype;
    }
};
