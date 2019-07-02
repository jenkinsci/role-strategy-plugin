package io.jenkins.plugins.rolestrategy;

import hudson.Extension;
import hudson.model.ManagementLink;

import javax.annotation.CheckForNull;

@Extension
public class FolderAuthorizationStrategyManagementLink extends ManagementLink {
    @CheckForNull
    @Override
    public String getIconFileName() {
        return "secure.gif";
    }

    @CheckForNull
    @Override
    public String getUrlName() {
        return "folder-auth";
    }

    @CheckForNull
    @Override
    public String getDisplayName() {
        return "Folder AuthorizationLevel Strategy";
    }
}
