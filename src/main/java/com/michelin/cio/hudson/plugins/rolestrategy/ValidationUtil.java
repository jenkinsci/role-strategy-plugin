package com.michelin.cio.hudson.plugins.rolestrategy;

import hudson.Functions;
import hudson.Util;
import hudson.model.User;
import hudson.security.SecurityRealm;
import hudson.security.UserMayOrMayNotExistException2;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;
import org.apache.commons.jelly.JellyContext;
import org.apache.commons.lang.StringUtils;
import org.jenkins.ui.icon.Icon;
import org.jenkins.ui.icon.IconSet;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.Stapler;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@Restricted(NoExternalUse.class)
class ValidationUtil {
  private ValidationUtil() {
    // do not use
  }

  static String formatNonExistentUserGroupValidationResponse(String user, String tooltip) {
    return formatUserGroupValidationResponse(null, "<span style='text-decoration: line-through; color: grey;'>" + user + "</span>",
        tooltip);
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
    return String.format("<span title='%s'><img src='%s' style='%s margin-right:0.2em'>%s</span>", tooltip, url, icon.getStyle(), user);
  }

  static FormValidation validateGroup(String groupName, SecurityRealm sr, boolean ambiguous) {
    String escapedSid = Functions.escape(groupName);
    try {
      sr.loadGroupByGroupname2(groupName, false);
      if (ambiguous) {
        return FormValidation.warningWithMarkup(formatUserGroupValidationResponse("user", escapedSid,
            "Group found; but permissions would also be granted to a user of this name"));
      } else {
        return FormValidation.okWithMarkup(formatUserGroupValidationResponse("user", escapedSid, "Group"));
      }
    } catch (UserMayOrMayNotExistException2 e) {
      // undecidable, meaning the group may exist
      if (ambiguous) {
        return FormValidation.warningWithMarkup(
            formatUserGroupValidationResponse("user", escapedSid, "Permissions would also be granted to a user or group of this name"));
      } else {
        return FormValidation.ok(groupName);
      }
    } catch (UsernameNotFoundException e) {
      // fall through next
    } catch (AuthenticationException e) {
      // other seemingly unexpected error.
      return FormValidation.error(e, "Failed to test the validity of the group name " + groupName);
    }
    return null;
  }

  static FormValidation validateUser(String userName, SecurityRealm sr, boolean ambiguous) {
    String escapedSid = Functions.escape(userName);
    try {
      sr.loadUserByUsername2(userName);
      User u = User.getById(userName, true);
      if (userName.equals(u.getFullName())) {
        // Sid and full name are identical, no need for tooltip
        if (ambiguous) {
          return FormValidation.warningWithMarkup(formatUserGroupValidationResponse("person", escapedSid,
              "User found; but permissions would also be granted to a group of this name"));
        } else {
          return FormValidation.okWithMarkup(formatUserGroupValidationResponse("person", escapedSid, "User"));
        }
      }
      if (ambiguous) {
        return FormValidation
            .warningWithMarkup(formatUserGroupValidationResponse("person", Util.escape(StringUtils.abbreviate(u.getFullName(), 50)),
                "User " + escapedSid + " found, but permissions would also be granted to a group of this name"));
      } else {
        return FormValidation.okWithMarkup(
            formatUserGroupValidationResponse("person", Util.escape(StringUtils.abbreviate(u.getFullName(), 50)), "User " + escapedSid));
      }
    } catch (UserMayOrMayNotExistException2 e) {
      // undecidable, meaning the user may exist
      if (ambiguous) {
        return FormValidation.warningWithMarkup(
            formatUserGroupValidationResponse("person", escapedSid, "Permissions would also be granted to a user or group of this name"));
      } else {
        return FormValidation.ok(userName);
      }
    } catch (UsernameNotFoundException e) {
      // fall through next
    } catch (AuthenticationException e) {
      // other seemingly unexpected error.
      return FormValidation.error(e, "Failed to test the validity of the user name " + userName);
    }
    return null;
  }

}
