package com.michelin.cio.hudson.plugins.rolestrategy;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.User;
import hudson.security.GroupDetails;
import hudson.security.SecurityRealm;
import hudson.security.UserMayOrMayNotExistException2;
import org.apache.commons.lang3.StringUtils;
import org.jenkins.ui.symbol.Symbol;
import org.jenkins.ui.symbol.SymbolRequest;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

/**
 * Resolves sids against the security realm for the Assign Roles UI.
 */
@Restricted(NoExternalUse.class)
class ValidationUtil {

  private static String userSymbol;
  private static String groupSymbol;

  private ValidationUtil() {
    // do not use
  }

  private static String getSymbol(String symbol, String clazzes) {
    SymbolRequest.Builder builder = new SymbolRequest.Builder();
    return Symbol.get(builder.withRaw("symbol-" + symbol + "-outline plugin-ionicons-api").withClasses(clazzes).build());
  }

  private static String loadUserSymbol() {
    if (userSymbol == null) {
      userSymbol = getSymbol("person", "icon-sm");
    }
    return userSymbol;
  }

  private static String loadGroupSymbol() {
    if (groupSymbol == null) {
      groupSymbol = getSymbol("people", "icon-sm");
    }
    return groupSymbol;
  }

  /**
   * Html snippet with the user/group icon next to the (already escaped) name,
   * as shown below the name field of the Assign Roles dialog.
   */
  static String formatUserGroupValidationResponse(AuthorizationType type, String user, String tooltip) {
    String symbol = type == AuthorizationType.GROUP ? loadGroupSymbol() : loadUserSymbol();
    return String.format("<div tooltip='%s' class='rsp-table__cell'>%s%s</div>", tooltip, symbol, user);
  }

  static String formatNonExistentUserGroupValidationResponse(AuthorizationType type, String user, String tooltip) {
    return formatUserGroupValidationResponse(type, "<span class='rsp-entry-not-found'>" + user + "</span>", tooltip);
  }

  enum SidResolutionKind {
    FOUND,
    NOT_FOUND,
    /** The realm cannot decide whether the sid exists (e.g. it does not support lookups). */
    UNKNOWN
  }

  /**
   * Outcome of a sid lookup: whether it exists and, when it does, a display name differing from the sid.
   */
  static final class SidResolution {
    private final SidResolutionKind kind;
    private final String displayName;

    SidResolution(SidResolutionKind kind, @CheckForNull String displayName) {
      this.kind = kind;
      this.displayName = displayName;
    }

    SidResolutionKind getKind() {
      return kind;
    }

    @CheckForNull
    String getDisplayName() {
      return displayName;
    }
  }

  @NonNull
  static SidResolution resolveUser(String userName, SecurityRealm sr) {
    try {
      sr.loadUserByUsername2(userName);
      User user = User.getById(userName, true);
      String fullName = user != null ? user.getFullName() : userName;
      String displayName = userName.equals(fullName) ? null : StringUtils.abbreviate(fullName, 50);
      return new SidResolution(SidResolutionKind.FOUND, displayName);
    } catch (UserMayOrMayNotExistException2 e) {
      return new SidResolution(SidResolutionKind.UNKNOWN, null);
    } catch (UsernameNotFoundException e) {
      return new SidResolution(SidResolutionKind.NOT_FOUND, null);
    } catch (AuthenticationException e) {
      // an unexpected realm failure says nothing about the sid itself
      return new SidResolution(SidResolutionKind.UNKNOWN, null);
    }
  }

  @NonNull
  static SidResolution resolveGroup(String groupName, SecurityRealm sr) {
    try {
      GroupDetails details = sr.loadGroupByGroupname2(groupName, false);
      String display = details.getDisplayName();
      String displayName = display == null || groupName.equals(display) ? null : StringUtils.abbreviate(display, 50);
      return new SidResolution(SidResolutionKind.FOUND, displayName);
    } catch (UserMayOrMayNotExistException2 e) {
      return new SidResolution(SidResolutionKind.UNKNOWN, null);
    } catch (UsernameNotFoundException e) {
      return new SidResolution(SidResolutionKind.NOT_FOUND, null);
    } catch (AuthenticationException e) {
      return new SidResolution(SidResolutionKind.UNKNOWN, null);
    }
  }
}
