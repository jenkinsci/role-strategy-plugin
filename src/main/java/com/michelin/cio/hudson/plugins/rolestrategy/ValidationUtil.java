package com.michelin.cio.hudson.plugins.rolestrategy;

import hudson.Functions;
import hudson.Util;
import hudson.model.User;
import hudson.security.SecurityRealm;
import hudson.security.UserMayOrMayNotExistException2;
import hudson.util.FormValidation;
import org.apache.commons.lang.StringUtils;
import org.jenkins.ui.symbol.Symbol;
import org.jenkins.ui.symbol.SymbolRequest;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@Restricted(NoExternalUse.class)
class ValidationUtil {

  private static String userSymbol;
  private static String groupSymbol;

  private static String warningSymbol;

  private ValidationUtil() {
    // do not use
  }

  static String formatNonExistentUserGroupValidationResponse(AuthorizationType type, String user, String tooltip) {
    return formatNonExistentUserGroupValidationResponse(type, user, tooltip, false);
  }

  static String formatNonExistentUserGroupValidationResponse(AuthorizationType type, String user, String tooltip, boolean alert) {
    return formatUserGroupValidationResponse(type, "<span class='rsp-entry-not-found'>" + user + "</span>",
            tooltip, alert);
  }

  private static String getSymbol(String symbol, String clazzes) {
    SymbolRequest.Builder builder = new SymbolRequest.Builder();

    return Symbol.get(builder.withRaw("symbol-" + symbol + "-outline plugin-ionicons-api").withClasses(clazzes).build());
  }

  private static void loadUserSymbol() {
    if (userSymbol == null) {
      userSymbol = getSymbol("person", "icon-sm");
    }
  }

  private static void loadGroupSymbol() {
    if (groupSymbol == null) {
      groupSymbol = getSymbol("people", "icon-sm");
    }
  }

  private static void loadWarningSymbol() {
    if (warningSymbol == null) {
      warningSymbol = getSymbol("warning", "icon-md rsp-table__icon-alert");
    }
  }

  static String formatUserGroupValidationResponse(AuthorizationType type, String user, String tooltip) {
    return formatUserGroupValidationResponse(type, user, tooltip, false);
  }

  static String formatUserGroupValidationResponse(AuthorizationType type, String user, String tooltip, boolean alert) {
    String symbol;
    switch (type) {
      case GROUP:
        loadGroupSymbol();
        symbol = groupSymbol;
        break;
      case EITHER:
      case USER:
      default:
        loadUserSymbol();
        symbol = userSymbol;
        break;
    }
    if (alert) {
      loadWarningSymbol();
      return String.format("<div tooltip='%s' class='rsp-table__cell'>%s%s%s</div>", tooltip, warningSymbol, symbol, user);
    }
    return String.format("<div tooltip='%s' class='rsp-table__cell'>%s%s</div>", tooltip, symbol, user);
  }

  static FormValidation validateGroup(String groupName, SecurityRealm sr, boolean ambiguous) {
    String escapedSid = Functions.escape(groupName);
    try {
      sr.loadGroupByGroupname2(groupName, false);
      if (ambiguous) {
        return FormValidation.respond(FormValidation.Kind.WARNING,
                formatUserGroupValidationResponse(AuthorizationType.GROUP, escapedSid,
            "Group found; but permissions would also be granted to a user of this name", true));
      } else {
        return FormValidation.respond(FormValidation.Kind.OK, formatUserGroupValidationResponse(AuthorizationType.GROUP,
                escapedSid, "Group"));
      }
    } catch (UserMayOrMayNotExistException2 e) {
      // undecidable, meaning the group may exist
      if (ambiguous) {
        return FormValidation.respond(FormValidation.Kind.WARNING,
            formatUserGroupValidationResponse(AuthorizationType.GROUP, escapedSid,
                    "Permissions would also be granted to a user or group of this name", true));
      } else {
        return FormValidation.ok(escapedSid);
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
          return FormValidation.respond(FormValidation.Kind.WARNING,
                  formatUserGroupValidationResponse(AuthorizationType.EITHER, escapedSid,
              "User found; but permissions would also be granted to a group of this name", true));
        } else {
          return FormValidation.respond(FormValidation.Kind.OK,
                  formatUserGroupValidationResponse(AuthorizationType.USER, escapedSid, "User"));
        }
      }
      if (ambiguous) {
        return FormValidation.respond(FormValidation.Kind.WARNING,
                formatUserGroupValidationResponse(AuthorizationType.EITHER, Util.escape(StringUtils.abbreviate(u.getFullName(), 50)),
                "User " + escapedSid + " found, but permissions would also be granted to a group of this name", true));
      } else {
        return FormValidation.respond(FormValidation.Kind.OK,
            formatUserGroupValidationResponse(AuthorizationType.USER, Util.escape(StringUtils.abbreviate(u.getFullName(), 50)),
                    "User " + escapedSid));
      }
    } catch (UserMayOrMayNotExistException2 e) {
      // undecidable, meaning the user may exist
      if (ambiguous) {
        return FormValidation.respond(FormValidation.Kind.WARNING,
            formatUserGroupValidationResponse(AuthorizationType.EITHER, escapedSid,
                    "Permissions would also be granted to a user or group of this name", true));
      } else {
        return FormValidation.ok(escapedSid);
      }
    } catch (UsernameNotFoundException e) {
      // fall through next
    } catch (AuthenticationException e) {
      // other seemingly unexpected error.
      return FormValidation.error(e, "Failed to test the validity of the user name " + escapedSid);
    }
    return null;
  }
}
