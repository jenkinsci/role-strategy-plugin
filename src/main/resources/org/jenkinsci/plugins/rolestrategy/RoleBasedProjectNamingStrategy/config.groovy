package org.jenkinsci.plugins.rolestrategy.RoleBasedProjectNamingStrategy

def f=namespace(lib.FormTagLib)

f.entry(title:_("forceExistingJobs"), field:"forceExistingJobs") {
    f.checkbox(name:"forceExistingJobs")
}
