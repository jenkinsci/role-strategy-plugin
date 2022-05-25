package org.jenkinsci.plugins.rolestrategy;

import com.michelin.cio.hudson.plugins.rolestrategy.Messages;
import com.michelin.cio.hudson.plugins.rolestrategy.RoleBasedAuthorizationStrategy;

import hudson.Extension;
import hudson.model.AdministrativeMonitor;
import jenkins.model.Jenkins;

@Extension
public class NamingStrategyAdministrativeMonitor extends AdministrativeMonitor
{
  
  @Override
  public String getDisplayName() {
      return Messages.RoleBasedProjectNamingStrategy_NotConfigured();
  }


  @Override
  public boolean isActivated()
  {
      Jenkins jenkins = Jenkins.get();
      return  (jenkins.getAuthorizationStrategy() instanceof RoleBasedAuthorizationStrategy
            && !(jenkins.getProjectNamingStrategy() instanceof RoleBasedProjectNamingStrategy));
      
  }
}
