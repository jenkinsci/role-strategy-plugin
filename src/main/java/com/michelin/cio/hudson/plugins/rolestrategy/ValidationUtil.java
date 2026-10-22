package com.michelin.cio.hudson.plugins.rolestrategy;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.User;
import hudson.security.GroupDetails;
import hudson.security.SecurityRealm;
import hudson.security.UserMayOrMayNotExistException2;
import io.jenkins.plugins.authcommons.Principal;
import io.jenkins.plugins.authcommons.PrincipalSearch;
import io.jenkins.plugins.authcommons.PrincipalType;
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
   * Outcome of a sid lookup: whether it exists and an optional display name differing from the sid.
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
      // Do not create a User record as a side effect of resolving a sid for
      // display; the null fallback below already covers sids with no local
      // user record.
      User user = User.getById(userName, false);
      String fullName = user != null ? user.getFullName() : userName;
      String displayName = userName.equals(fullName) ? lookupDisplayName(userName, PrincipalType.USER) : fullName;
      return new SidResolution(SidResolutionKind.FOUND, StringUtils.abbreviate(displayName, 50));
    } catch (UserMayOrMayNotExistException2 e) {
      SidResolution resolution = fromDirectory(userName, PrincipalType.USER, SidResolutionKind.UNKNOWN);
      if (resolution.getKind() == SidResolutionKind.FOUND) {
        return resolution;
      }
      // A local display name does not establish whether the user still exists in the realm.
      User user = User.getById(userName, false);
      String fullName = user != null ? user.getFullName() : userName;
      String displayName = userName.equals(fullName) ? null : StringUtils.abbreviate(fullName, 50);
      return new SidResolution(SidResolutionKind.UNKNOWN, displayName);
    } catch (UsernameNotFoundException e) {
      return fromDirectory(userName, PrincipalType.USER, SidResolutionKind.NOT_FOUND);
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
      String displayName = display == null || groupName.equals(display)
          ? lookupDisplayName(groupName, PrincipalType.GROUP) : display;
      return new SidResolution(SidResolutionKind.FOUND, StringUtils.abbreviate(displayName, 50));
    } catch (UserMayOrMayNotExistException2 e) {
      return fromDirectory(groupName, PrincipalType.GROUP, SidResolutionKind.UNKNOWN);
    } catch (UsernameNotFoundException e) {
      return fromDirectory(groupName, PrincipalType.GROUP, SidResolutionKind.NOT_FOUND);
    } catch (AuthenticationException e) {
      return new SidResolution(SidResolutionKind.UNKNOWN, null);
    }
  }

  /**
   * For sids the security realm cannot resolve, e.g. directory users who never logged in to Jenkins, asks the
   * principal search providers whether the directory knows them.
   */
  @NonNull
  private static SidResolution fromDirectory(String sid, PrincipalType type, SidResolutionKind otherwise) {
    Principal principal = PrincipalSearch.lookup(sid, type);
    if (principal == null) {
      return new SidResolution(otherwise, null);
    }
    String displayName = sid.equals(principal.displayName()) ? null : principal.displayName();
    return new SidResolution(SidResolutionKind.FOUND, StringUtils.abbreviate(displayName, 50));
  }

  /**
   * Asks the principal search providers for a friendly name, for realms whose sids are not meaningful by
   * themselves (e.g. object ids).
   */
  @CheckForNull
  private static String lookupDisplayName(String sid, PrincipalType type) {
    Principal principal = PrincipalSearch.lookup(sid, type);
    if (principal == null || sid.equals(principal.displayName())) {
      return null;
    }
    return principal.displayName();
  }
}
