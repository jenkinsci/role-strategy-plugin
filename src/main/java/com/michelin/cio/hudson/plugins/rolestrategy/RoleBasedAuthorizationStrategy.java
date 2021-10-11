/*
 * The MIT License
 *
 * Copyright (c) 2010-2017, Manufacture Française des Pneumatiques Michelin,
 * Thomas Maurel, Romain Seguy, Synopsys Inc., Oleg Nenashev and contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.michelin.cio.hudson.plugins.rolestrategy;

import com.synopsys.arc.jenkins.plugins.rolestrategy.RoleType;
import com.synopsys.arc.jenkins.plugins.rolestrategy.UserMacroExtension;
import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.Extension;
import hudson.model.AbstractItem;
import hudson.model.Computer;
import hudson.model.Hudson;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.View;
import hudson.scm.SCM;
import hudson.security.ACL;
import hudson.security.AuthorizationStrategy;
import hudson.security.GlobalMatrixAuthorizationStrategy;
import hudson.security.Permission;
import hudson.security.PermissionGroup;
import hudson.security.SidACL;
import java.io.IOException;
import javax.servlet.ServletException;

import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.regex.Pattern;

import hudson.util.FormValidation;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.acegisecurity.acls.sid.PrincipalSid;
import org.jenkinsci.plugins.rolestrategy.permissions.PermissionHelper;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.interceptor.RequirePOST;

/**
 * Role-based authorization strategy.
 * @author Thomas Maurel
 */
public class RoleBasedAuthorizationStrategy extends AuthorizationStrategy {

  public final static String GLOBAL    = "globalRoles";
  public final static String PROJECT   = "projectRoles";
  public final static String SLAVE     = "slaveRoles";
  public final static String MACRO_ROLE = "roleMacros";
  public final static String MACRO_USER  = "userMacros";

  private final RoleMap agentRoles;
  private final RoleMap globalRoles;
  private final RoleMap itemRoles;

  public RoleBasedAuthorizationStrategy() {
      agentRoles = new RoleMap();
      globalRoles = new RoleMap();
      itemRoles = new RoleMap();
  }

  /**
   * Creates a new {@link RoleBasedAuthorizationStrategy}
   *
   * @param grantedRoles the roles in the strategy
   */
  public RoleBasedAuthorizationStrategy(Map<String, RoleMap> grantedRoles) {
      RoleMap map = grantedRoles.get(SLAVE);
      agentRoles = map == null ? new RoleMap() : map;

      map = grantedRoles.get(GLOBAL);
      globalRoles = map == null ? new RoleMap() : map;

      map = grantedRoles.get(PROJECT);
      itemRoles = map == null ? new RoleMap() : map;
  }

  /**
   * Get the root ACL.
   * @return The global ACL
   */
  @Override
  @NonNull
  public SidACL getRootACL() {
    return globalRoles.getACL(RoleType.Global, null);
  }

  /**
   * Get the {@link RoleMap} corresponding to the {@link RoleType}
   *
   * @param roleType the type of the role
   * @return the {@link RoleMap} corresponding to the {@code roleType}
   * @throws IllegalArgumentException for an invalid {@code roleType}
   */
  @NonNull
  private RoleMap getRoleMap(RoleType roleType) {
      switch (roleType) {
          case Global:
              return globalRoles;
          case Project:
              return itemRoles;
          case Slave:
              return agentRoles;
          default:
              throw new IllegalArgumentException("Unknown RoleType: " + roleType);
      }
  }

    /**
     * Get the specific ACL for projects.
     *
     * @param project The access-controlled project
     * @return The project specific ACL
     */
    @Override
    @NonNull
    public ACL getACL(@NonNull Job<?,?> project) {
      return getACL((AbstractItem) project);
    }

    @Override
    @NonNull
    public ACL getACL(@NonNull AbstractItem project) {
        return itemRoles.newMatchingRoleMap(project.getFullName()).getACL(RoleType.Project, project)
                .newInheritingACL(getRootACL());
    }

    @Override
    @NonNull
    public ACL getACL(@NonNull Computer computer) {
        return agentRoles.newMatchingRoleMap(computer.getName()).getACL(RoleType.Slave, computer)
                .newInheritingACL(getRootACL());
    }

  /**
   * Used by the container realm.
   * @return All the sids referenced by the strategy
   */
  @Override
  @NonNull
  public Collection<String> getGroups() {
    Set<String> sids = new HashSet<>();
    sids.addAll(globalRoles.getSids(true));
    sids.addAll(itemRoles.getSids(true));
    sids.addAll(agentRoles.getSids(true));
    return sids;
  }

  /**
   * Get the roles from the global {@link RoleMap}.
   * <p>The returned sorted map is unmodifiable.</p>
   * @param type The object type controlled by the {@link RoleMap}
   * @return All roles from the global {@link RoleMap}.
   * @deprecated Use {@link RoleBasedAuthorizationStrategy#getGrantedRoles(RoleType)}
   */
  @Nullable
  @Deprecated
  public SortedMap<Role, Set<String>> getGrantedRoles(String type) {
    return getGrantedRoles(RoleType.fromString(type));
  }

  /**
   * Get the {@link Role}s and the sids assigned to them for the given {@link RoleType}
   * @param type the type of the role
   * @return roles mapped to the set of user sids assigned to that role
   * @since 2.12
   */
  public SortedMap<Role, Set<String>> getGrantedRoles(@NonNull RoleType type) {
    return getRoleMap(type).getGrantedRoles();
  }

  /**
   * Get all the SIDs referenced by specified {@link RoleMap} type.
   * @param type The object type controlled by the {@link RoleMap}
   * @return All SIDs from the specified {@link RoleMap}.
   */
  @CheckForNull
  public Set<String> getSIDs(String type) {
    return getRoleMap(RoleType.fromString(type)).getSids();
  }

  /**
   * Returns a map associating a {@link RoleType} with each {@link RoleMap}.
   * <p>This method is intended to be used for XML serialization purposes (take
   * a look at the {@link ConverterImpl}) and, as such, must remain private
   * since it exposes all the security config.</p>
   */
  @NonNull
  private Map<RoleType, RoleMap> getRoleMaps() {
    Map<RoleType, RoleMap> roleMaps = new HashMap<>();
    roleMaps.put(RoleType.Global, globalRoles);
    roleMaps.put(RoleType.Slave, agentRoles);
    roleMaps.put(RoleType.Project, itemRoles);
    return Collections.unmodifiableMap(roleMaps);
  }

  /**
   * Add the given {@link Role} to the {@link RoleMap} associated to the provided class.
   * @param roleType The type of the {@link Role} to be added
   * @param role The {@link Role} to add
   */
  private void addRole(RoleType roleType, Role role) {
    getRoleMap(roleType).addRole(role);
  }

  /**
   * Assign a role to a sid
   * @param type The type of role
   * @param role The role to assign
   * @param sid The sid to assign to
   */
  private void assignRole(RoleType type, Role role, String sid) {
    RoleMap roleMap = getRoleMap(type);
    if (roleMap.hasRole(role)) {
      roleMap.assignRole(role, sid);
    }
  }

    /**
     * API method to add roles
     * <p>
     * example: {@code curl -X POST localhost:8080/role-strategy/strategy/addRole --data "type=globalRoles&amp;roleName=ADM&amp;
     * permissionIds=hudson.model.Item.Discover,hudson.model.Item.ExtendedRead&amp;overwrite=true"}
     *
     * @param type          (globalRoles, projectRoles)
     * @param roleName      Name of role
     * @param permissionIds Comma separated list of IDs for given roleName
     * @param overwrite     Overwrite existing role
     * @param pattern       Role pattern
     * @throws IOException  In case saving changes fails
     * @since 2.5.0
     */
    @RequirePOST
    @Restricted(NoExternalUse.class)
    public void doAddRole(@QueryParameter(required = true) String type,
                          @QueryParameter(required = true) String roleName,
                          @QueryParameter(required = true) String permissionIds,
                          @QueryParameter(required = true) String overwrite,
                          @QueryParameter(required = false) String pattern) throws IOException {
        checkAdminPerm();

        boolean overwriteb = Boolean.parseBoolean(overwrite);
        String pttrn = ".*";

        if (!type.equals(RoleBasedAuthorizationStrategy.GLOBAL) && pattern != null) {
            pttrn = pattern;
        }

        ArrayList<String> permissionList = new ArrayList<>(Arrays.asList(permissionIds.split(",")));

        Set<Permission> permissionSet = new HashSet<>();
        for (String p : permissionList) {
            Permission temp=Permission.fromId(p);
            if (temp == null) {
                throw new IOException("Cannot find permission for id=" + p + ", role name=" + roleName + " role type=" + type);
            } else {
                permissionSet.add(temp);
            }
        }
        Role role = new Role(roleName, pttrn, permissionSet);
        RoleType roleType = RoleType.fromString(type);
        if (overwriteb) {
            RoleMap roleMap = getRoleMap(roleType);
            Role role2 = roleMap.getRole(roleName);
            if (role2 != null) {
                roleMap.removeRole(role2);
            }
        }
        addRole(roleType, role);
        persistChanges();
    }

    /**
     * API method to get role.
     * Example: {@code curl -XGET 'http://localhost:8080/jenkins/role-strategy/strategy/getRole
     * ?type=globalRoles&roleName=admin'}
     *
     * @param type (globalRoles, projectRoles, slaveRoles)
     * @param roleName name of role (single, no list)
     * @throws IOException In case write response failed
     * @since 2.8.3
     */
    @Restricted(NoExternalUse.class)
    public void doGetRole(@QueryParameter(required = true) String type,
                          @QueryParameter(required = true) String roleName) throws IOException{
        checkAdminPerm();
        JSONObject responseJson = new JSONObject();
        RoleMap roleMap = getRoleMap(RoleType.fromString(type));
        Role role = roleMap.getRole(roleName);
        if (role != null){
            Set<Permission> permissions = role.getPermissions();
            Map<String, Boolean> permissionsMap = new HashMap<>();
            for (Permission permission : permissions) {
                permissionsMap.put(permission.getId(), permission.getEnabled());
            }
            responseJson.put("permissionIds", permissionsMap);
            if (!type.equals(RoleBasedAuthorizationStrategy.GLOBAL)) {
                responseJson.put("pattern", role.getPattern().pattern());
            }
            Map<Role,Set<String>> grantedRoleMap = roleMap.getGrantedRoles();
            responseJson.put("sids", grantedRoleMap.get(role));
        }

        Stapler.getCurrentResponse().setContentType("application/json;charset=UTF-8");
        Writer writer = Stapler.getCurrentResponse().getCompressedWriter(Stapler.getCurrentRequest());
        responseJson.write(writer);
        writer.close();
    }

    /**
     * API method to remove roles.
     * Example: {@code curl -X POST localhost:8080/role-strategy/strategy/removeRoles --data "type=globalRoles&amp;
     * roleNames=ADM,DEV"}
     *
     * @param type      (globalRoles, projectRoles, slaveRoles)
     * @param roleNames comma separated list of roles to remove from type
     * @throws IOException in case saving changes fails
     * @since 2.5.0
     */
    @RequirePOST
    @Restricted(NoExternalUse.class)
    public void doRemoveRoles(@QueryParameter(required = true) String type,
                              @QueryParameter(required = true) String roleNames) throws IOException {
        checkAdminPerm();

        RoleMap roleMap = getRoleMap(RoleType.fromString(type));
        String[] split = roleNames.split(",");
        for (String aSplit : split) {
            Role role = roleMap.getRole(aSplit);
            if (role != null) {
                roleMap.removeRole(role);
            }
        }
        persistChanges();
    }

    /**
     * API method to assign SID to role.
     * Example: {@code curl -X POST localhost:8080/role-strategy/strategy/assignRole --data "type=globalRoles&amp;roleName=ADM
     * &amp;sid=username"}
     *
     * @param type     (globalRoles, projectRoles, slaveRoles)
     * @param roleName name of role (single, no list)
     * @param sid      user ID (single, no list)
     * @throws IOException in case saving changes fails
     * @since 2.5.0
     */
    @RequirePOST
    @Restricted(NoExternalUse.class)
    public void doAssignRole(@QueryParameter(required = true) String type,
                             @QueryParameter(required = true) String roleName,
                             @QueryParameter(required = true) String sid) throws IOException {
        checkAdminPerm();
        final RoleType roleType = RoleType.fromString(type);
        Role role = getRoleMap(roleType).getRole(roleName);
        if (role != null) {
            assignRole(roleType, role, sid);
        }
        persistChanges();
    }

    private static void persistChanges() throws IOException {
        instance().save();
    }

    private static Jenkins instance() {
        return Jenkins.get();
    }

    private static void checkAdminPerm() {
        instance().checkPermission(Jenkins.ADMINISTER);
    }

    /**
     * API method to delete a SID from all granted roles.
     * Example: curl -X POST localhost:8080/role-strategy/strategy/deleteSid --data "type=globalRoles&amp;sid=username"
     *
     * @param type (globalRoles, projectRoles, slaveRoles)
     * @param sid  user ID to remove
     * @throws IOException in case saving changes fails
     * @since 2.4.1
     */
    @RequirePOST
    @Restricted(NoExternalUse.class)
    public void doDeleteSid(@QueryParameter(required = true) String type,
                            @QueryParameter(required = true) String sid) throws IOException {
        checkAdminPerm();
        getRoleMap(RoleType.fromString(type)).deleteSids(sid);
        persistChanges();
    }

    /**
     * API method to unassign group/user with a role
     * Example: curl -X POST localhost:8080/role-strategy/strategy/unassignRole --data "type=globalRoles&amp;roleName=AMD&amp;sid=username"
     *
     * @param type (globalRoles, projectRoles, slaveRoles)
     * @param roleName unassign role with sid
     * @param sid  user ID to remove
     * @throws IOException in case saving changes fails
     * @since 2.6.0
     */
    @RequirePOST
    @Restricted(NoExternalUse.class)
    public void doUnassignRole(@QueryParameter(required = true) String type,
                            @QueryParameter(required = true) String roleName,
                            @QueryParameter(required = true) String sid) throws IOException {
      checkAdminPerm();
      RoleMap roleMap = getRoleMap(RoleType.fromString(type));
      Role role = roleMap.getRole(roleName);
      if (role != null) {
        roleMap.deleteRoleSid(sid, role.getName());
      }
      persistChanges();
    }

    /**
     * API method to get all groups/users with their role in any role type
     * Example: curl -X GET localhost:8080/role-strategy/strategy/getAllRoles?type=projectRoles
     *
     * @param type (globalRoles by default, projectRoles, slaveRoles)
     *
     * @since 2.6.0
     */
    @Restricted(NoExternalUse.class)
    public void doGetAllRoles(@QueryParameter(fixEmpty = true) String type) throws IOException {
        checkAdminPerm();
        JSONObject responseJson = new JSONObject();
        RoleMap roleMap = getRoleMap(RoleType.Global);
        if (type != null) {
            roleMap = getRoleMap(RoleType.fromString(type));
        }

        for (Map.Entry<Role, Set<String>> grantedRole : roleMap.getGrantedRoles().entrySet()) {
            responseJson.put(grantedRole.getKey().getName(), grantedRole.getValue());
        }

        Stapler.getCurrentResponse().setContentType("application/json;charset=UTF-8");
        Writer writer = Stapler.getCurrentResponse().getCompressedWriter(Stapler.getCurrentRequest());
        responseJson.write(writer);
        writer.close();
    }

    /**
     * API method to get a list of jobs matching a pattern
     * Example: curl -X GET localhost:8080/role-strategy/strategy/getMatchingJobs?pattern=^staging.*
     *
     * @param pattern Pattern to match against
     * @param maxJobs Maximum matching jobs to search for
     * @throws IOException when unable to write response
     */
    @Restricted(NoExternalUse.class)
    public void doGetMatchingJobs(@QueryParameter(required = true) String pattern,
                                  @QueryParameter() int maxJobs) throws IOException {
        checkAdminPerm();
        List<String> matchingJobs = RoleMap.getMatchingJobNames(Pattern.compile(pattern), maxJobs);
        JSONObject responseJson = new JSONObject();
        responseJson.put("matchingJobs", matchingJobs);
        StaplerResponse response = Stapler.getCurrentResponse();
        response.setContentType("application/json;charset=UTF-8");
        Writer writer = response.getCompressedWriter(Stapler.getCurrentRequest());
        responseJson.write(writer);
        writer.close();
    }

  @Extension
  public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

  /**
   * Converter used to persist and retrieve the strategy from disk.
   *
   * <p>This converter is there to manually handle the marshalling/unmarshalling
   * of this strategy: Doing so is a little bit dirty but allows to easily update
   * the plugin when new access controlled object (for the moment: Job and
   * Project) will be introduced. If it's the case, there's only the need to
   * update the getRoleMaps() method.</p>
   */
  public static class ConverterImpl implements Converter {
      public boolean canConvert(Class type) {
        return type==RoleBasedAuthorizationStrategy.class;
      }

      public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
        RoleBasedAuthorizationStrategy strategy = (RoleBasedAuthorizationStrategy)source;

        // Role maps
        Map<RoleType, RoleMap> maps = strategy.getRoleMaps();
        for (Map.Entry<RoleType, RoleMap> map : maps.entrySet()) {
          RoleMap roleMap = map.getValue();
          writer.startNode("roleMap");
          writer.addAttribute("type", map.getKey().getStringType());

          for (Map.Entry<Role, Set<String>> grantedRole : roleMap.getGrantedRoles().entrySet()) {
            Role role = grantedRole.getKey();
            if (role != null) {
              writer.startNode("role");
              writer.addAttribute("name", role.getName());
              writer.addAttribute("pattern", role.getPattern().pattern());

              writer.startNode("permissions");
              for (Permission permission : role.getPermissions()) {
                writer.startNode("permission");
                writer.setValue(permission.getId());
                writer.endNode();
              }
              writer.endNode();

              writer.startNode("assignedSIDs");
              for (String sid : grantedRole.getValue()) {
                writer.startNode("sid");
                writer.setValue(sid);
                writer.endNode();
              }
              writer.endNode();

              writer.endNode();
            }
          }
          writer.endNode();
        }
      }

      public Object unmarshal(HierarchicalStreamReader reader, final UnmarshallingContext context) {
        final Map<String, RoleMap> roleMaps = new HashMap<>();
        while(reader.hasMoreChildren()) {
          reader.moveDown();

          // roleMaps
          if(reader.getNodeName().equals("roleMap")) {
            String type = reader.getAttribute("type");
            RoleMap map = new RoleMap();
            while(reader.hasMoreChildren()) {
              reader.moveDown();
              String name = reader.getAttribute("name");
              String pattern = reader.getAttribute("pattern");
              Set<Permission> permissions = new HashSet<>();

              String next = reader.peekNextChild();
              if (next != null && next.equals("permissions")) {
                reader.moveDown();
                while(reader.hasMoreChildren()) {
                  reader.moveDown();
                  Permission p = Permission.fromId(reader.getValue());
                  if (p != null) {
                    permissions.add(p);
                  }
                  reader.moveUp();
                }
                reader.moveUp();
              }

              Role role = new Role(name, pattern, permissions);
              map.addRole(role);

              next = reader.peekNextChild();
              if (next != null && next.equals("assignedSIDs")) {
                reader.moveDown();
                while(reader.hasMoreChildren()) {
                  reader.moveDown();
                  map.assignRole(role, reader.getValue());
                  reader.moveUp();
                }
                reader.moveUp();
              }
              reader.moveUp();
            }
            roleMaps.put(type, map);
          }
          reader.moveUp();
        }

        return new RoleBasedAuthorizationStrategy(roleMaps);
      }

      protected RoleBasedAuthorizationStrategy create() {
          return new RoleBasedAuthorizationStrategy();
      }
  }

    /**
     * Retrieves instance of the strategy.
     * @return Strategy instance or {@code null} if it is disabled.
     */
    @CheckForNull
    public static RoleBasedAuthorizationStrategy getInstance() {
        final Jenkins jenkins = Jenkins.getInstanceOrNull();
        final AuthorizationStrategy authStrategy= jenkins != null ? jenkins.getAuthorizationStrategy() : null;
        if (authStrategy instanceof RoleBasedAuthorizationStrategy) {
            return (RoleBasedAuthorizationStrategy)authStrategy;
        }

        // Nothing to do here, not a Role strategy
        return null;
    }

   /**
     * Updates macro roles
     * @since 2.1.0
     */
    void renewMacroRoles()
    {
        //TODO: add mandatory roles

        // Check role extensions
        for (UserMacroExtension userExt : UserMacroExtension.all())
        {
            if (userExt.IsApplicable(RoleType.Global))
            {
                getRoleMap(RoleType.Global).getSids().contains(userExt.getName());
            }
        }
    }

    /**
     * Control job create using {@link org.jenkinsci.plugins.rolestrategy.RoleBasedProjectNamingStrategy}.
     * @since 2.2.0
     * @deprecated Always available since 1.566
     */
    @Deprecated
    public static boolean isCreateAllowed(){
        return true;
    }

  /**
   * Descriptor used to bind the strategy to the Web forms.
   */
  public static final class DescriptorImpl extends GlobalMatrixAuthorizationStrategy.DescriptorImpl {

    @Override
    @NonNull
    public  String getDisplayName() {
      return Messages.RoleBasedAuthorizationStrategy_DisplayName();
    }


    public FormValidation doCheckForWhitespace(@QueryParameter String value) {
      if (value==null || value.trim().equals(value)) {
        return FormValidation.ok();
      } else {
        return FormValidation.warning(Messages.RoleBasedProjectNamingStrategy_WhiteSpaceWillBeTrimmed());
      }
    }

    /**
     * Called on role management form's submission.
     */
    @RequirePOST
    @Restricted(NoExternalUse.class)
    public void doRolesSubmit(StaplerRequest req, StaplerResponse rsp) throws ServletException, IOException {
        checkAdminPerm();

        req.setCharacterEncoding("UTF-8");
      JSONObject json = req.getSubmittedForm();
      AuthorizationStrategy strategy = this.newInstance(req, json);
      instance().setAuthorizationStrategy(strategy);
      // Persist the data
        persistChanges();
    }

    /**
     * Called on role assignment form's submission.
     */
    @RequirePOST
    @Restricted(NoExternalUse.class)
    public void doAssignSubmit(StaplerRequest req, StaplerResponse rsp) throws ServletException, IOException {
        checkAdminPerm();

        req.setCharacterEncoding("UTF-8");
      JSONObject json = req.getSubmittedForm();
      AuthorizationStrategy oldStrategy = instance().getAuthorizationStrategy();

      if (json.has(GLOBAL) && json.has(PROJECT) && oldStrategy instanceof RoleBasedAuthorizationStrategy) {
        RoleBasedAuthorizationStrategy strategy = (RoleBasedAuthorizationStrategy) oldStrategy;
        Map<RoleType, RoleMap> maps = strategy.getRoleMaps();

        for (Map.Entry<RoleType, RoleMap> map : maps.entrySet()) {
          // Get roles and skip non-existent role entries (backward-comp)
          RoleMap roleMap = map.getValue();
          roleMap.clearSids();
          JSONObject roles = json.getJSONObject(map.getKey().getStringType());
          if (roles.isNullObject()) {
              continue;
          }

          for (Map.Entry<String,JSONObject> r : (Set<Map.Entry<String,JSONObject>>)roles.getJSONObject("data").entrySet()) {
            String sid = r.getKey();
            for (Map.Entry<String,Boolean> e : (Set<Map.Entry<String,Boolean>>)r.getValue().entrySet()) {
              if (e.getValue()) {
                Role role = roleMap.getRole(e.getKey());
                if (role != null && sid != null && !sid.equals("")) {
                  roleMap.assignRole(role, sid);
                }
              }
            }
          }
        }
        // Persist the data
          persistChanges();
      }
    }

    /**
     * Method called on Jenkins Manage panel submission, and plugin specific forms
     * to create the {@link AuthorizationStrategy} object.
     */
    @Override
    public AuthorizationStrategy newInstance(StaplerRequest req, JSONObject formData) {
      AuthorizationStrategy oldStrategy = instance().getAuthorizationStrategy();
      RoleBasedAuthorizationStrategy strategy;


      // If the form contains data, it means the method has been called by plugin
      // specifics forms, and we need to handle it.
      if (formData.has(GLOBAL) && formData.has(PROJECT) && formData.has(SLAVE) && oldStrategy instanceof RoleBasedAuthorizationStrategy) {
        strategy = new RoleBasedAuthorizationStrategy();

        JSONObject globalRoles = formData.getJSONObject(GLOBAL);
        for (Map.Entry<String,JSONObject> r : (Set<Map.Entry<String,JSONObject>>)globalRoles.getJSONObject("data").entrySet()) {
          String roleName = r.getKey();
          Set<Permission> permissions = new HashSet<>();
          for (Map.Entry<String,Boolean> e : (Set<Map.Entry<String,Boolean>>)r.getValue().entrySet()) {
              if (e.getValue()) {
                  Permission p = Permission.fromId(e.getKey());
                  permissions.add(p);
              }
          }

          Role role = new Role(roleName, permissions);
          strategy.addRole(RoleType.Global, role);
          RoleMap roleMap = ((RoleBasedAuthorizationStrategy) oldStrategy).getRoleMap(RoleType.Global);
          if (roleMap != null) {
            Set<String> sids = roleMap.getSidsForRole(roleName);
            if (sids != null) {
              for (String sid : sids) {
                strategy.assignRole(RoleType.Global, role, sid);
              }
            }
          }
        }

        readRoles(formData, RoleType.Project, strategy, (RoleBasedAuthorizationStrategy)oldStrategy);
        readRoles(formData, RoleType.Slave, strategy, (RoleBasedAuthorizationStrategy)oldStrategy);
      }
      // When called from Hudson Manage panel, but was already on a role-based strategy
      else if (oldStrategy instanceof RoleBasedAuthorizationStrategy) {
        // Do nothing, keep the same strategy
        strategy = (RoleBasedAuthorizationStrategy) oldStrategy;
      }
      // When called from Hudson Manage panel, but when the previous strategy wasn't
      // role-based, it means we need to create an admin role, and assign it to the
      // current user to not throw him out of the webapp
      else {
        strategy = new RoleBasedAuthorizationStrategy();
        Role adminRole = createAdminRole();
        strategy.addRole(RoleType.Global, adminRole);
        strategy.assignRole(RoleType.Global, adminRole, getCurrentUser());
      }

      strategy.renewMacroRoles();
      return strategy;
    }

    private void readRoles(JSONObject formData, final RoleType roleType,
                           RoleBasedAuthorizationStrategy targetStrategy, RoleBasedAuthorizationStrategy oldStrategy) {
        final String roleTypeAsString = roleType.getStringType();
        if (!formData.has(roleTypeAsString)) {
            assert false : "Unexistent Role type " + roleTypeAsString;
            return;
        }
        JSONObject projectRoles = formData.getJSONObject(roleTypeAsString);
        if (!projectRoles.containsKey("data")) {
            assert false : "No data at role description";
            return;
        }

        for (Map.Entry<String,JSONObject> r : (Set<Map.Entry<String,JSONObject>>)projectRoles.getJSONObject("data").entrySet()) {
          String roleName = r.getKey();
          Set<Permission> permissions = new HashSet<>();
          String pattern = r.getValue().getString("pattern");
          if (pattern != null) {
            r.getValue().remove("pattern");
          }
          else {
            pattern = ".*";
          }
          for (Map.Entry<String,Boolean> e : (Set<Map.Entry<String,Boolean>>)r.getValue().entrySet()) {
              if (e.getValue()) {
                  Permission p = Permission.fromId(e.getKey());
                  permissions.add(p);
              }
          }

          Role role = new Role(roleName, pattern, permissions);
          targetStrategy.addRole(roleType, role);

          RoleMap roleMap = oldStrategy.getRoleMap(roleType);
          if (roleMap != null) {
            Set<String> sids = roleMap.getSidsForRole(roleName);
            if (sids != null) {
              for (String sid : sids) {
                targetStrategy.assignRole(roleType, role, sid);
              }
            }
          }
        }
    }

    /**
     * Create an admin role.
     */
    private Role createAdminRole() {
      Set<Permission> permissions = new HashSet<>();
      for (PermissionGroup group : getGroups(GLOBAL)) {
        for (Permission permission : group) {
          permissions.add(permission);
        }
      }
        return new Role("admin", permissions);
    }

    /**
     * Get the current user ({@code Anonymous} if not logged-in).
     * @return Sid of the current user
     */
    private String getCurrentUser() {
      PrincipalSid currentUser = new PrincipalSid(Hudson.getAuthentication());
      return currentUser.getPrincipal();
    }

    /**
     * Get the needed permissions groups.
     *
     * @param type Role type
     * @return Groups, which should be displayed for a specific role type.
     *         {@code null} if an unsupported type is defined.
     */
    @Nullable
    public List<PermissionGroup> getGroups(@NonNull String type) {
        List<PermissionGroup> groups;
        switch (type) {
            case GLOBAL:
                groups = new ArrayList<>(PermissionGroup.getAll());
                groups.remove(PermissionGroup.get(Permission.class));
                break;
            case PROJECT:
                groups = new ArrayList<>(PermissionGroup.getAll());
                groups.remove(PermissionGroup.get(Permission.class));
                groups.remove(PermissionGroup.get(Hudson.class));
                groups.remove(PermissionGroup.get(Computer.class));
                groups.remove(PermissionGroup.get(View.class));
                break;
            case SLAVE:
                groups = new ArrayList<>(PermissionGroup.getAll());
                groups.remove(PermissionGroup.get(Permission.class));
                groups.remove(PermissionGroup.get(Hudson.class));
                groups.remove(PermissionGroup.get(View.class));

                // Project, SCM and Run permissions
                groups.remove(PermissionGroup.get(Item.class));
                groups.remove(PermissionGroup.get(SCM.class));
                groups.remove(PermissionGroup.get(Run.class));
                break;
            default:
                groups = null;
                break;
        }
        return groups;
    }
    
    @Restricted(NoExternalUse.class)
    public boolean showPermission(String type, Permission p) {
        switch (type) {
            case GLOBAL:
                if (PermissionHelper.isDangerous(p)) {
                    return false;
                }
                return p.getEnabled();
            case PROJECT:
                return p == Item.CREATE && p.getEnabled() || p != Item.CREATE && p.getEnabled();
            case SLAVE:
                return p != Computer.CREATE && p.getEnabled();
            default:
                return false;
        }
    }
  }
}
