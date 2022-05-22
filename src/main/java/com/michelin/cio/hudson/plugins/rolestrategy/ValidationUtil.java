package com.michelin.cio.hudson.plugins.rolestrategy;

import org.apache.commons.jelly.JellyContext;
import org.jenkins.ui.icon.Icon;
import org.jenkins.ui.icon.IconSet;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.Stapler;

import jenkins.model.Jenkins;

@Restricted(NoExternalUse.class)
class ValidationUtil {
  private ValidationUtil() {
      // do not use
  }

  static String formatNonExistentUserGroupValidationResponse(String user, String tooltip) {
      return formatUserGroupValidationResponse(null, "<span style='text-decoration: line-through; color: grey;'>" + user + "</span>", tooltip);
  }

  static String formatUserGroupValidationResponse(String img, String user, String tooltip) {
      if (img == null) {
          return String.format("<span title='%s'>%s</span>", tooltip, user);
      }

      String imageFormat = String.format("icon-%s icon-sm", img);
      Icon icon = IconSet.icons.getIconByClassSpec(imageFormat);
      JellyContext ctx = new JellyContext();
      ctx.setVariable("resURL", Stapler.getCurrentRequest().getContextPath() + Jenkins.RESOURCE_PATH);
      String url = icon.getQualifiedUrl(ctx);
      return String.format("<span title='%s'><img src='%s' style='%s margin-right:0.2em'>%s</span>",
            tooltip, url, icon.getStyle(), user);
  }
}
