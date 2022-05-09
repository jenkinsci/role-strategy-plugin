package com.michelin.cio.hudson.plugins.rolestrategy;

import org.kohsuke.stapler.Stapler;

import hudson.util.VersionNumber;
import jenkins.model.Jenkins;

class ValidationUtil {
  private ValidationUtil() {
      // do not use
  }

  private static final VersionNumber jenkinsVersion = Jenkins.getVersion();
  
  static String formatNonExistentUserGroupValidationResponse(String user, String tooltip) {
      return formatUserGroupValidationResponse(null, "<span style='text-decoration: line-through; color: grey;'>" + user + "</span>", tooltip);
  }

  static String formatUserGroupValidationResponse(String img, String user, String tooltip) {
      if (img == null) {
          return String.format("<span title='%s'>%s</span>", tooltip, user);
      }

      String imageFormat = String.format("svgs/%s.svg' width='16'", img);
      if (jenkinsVersion.isOlderThan(new VersionNumber("2.308"))) {
        imageFormat = String.format("16x16/%s.png'", img);
      }
      return String.format("<span title='%s'><img src='%s%s/images/%s style='margin-right:0.2em'>%s</span>", tooltip, Stapler.getCurrentRequest().getContextPath(), Jenkins.RESOURCE_PATH, imageFormat, user);
  }
}
