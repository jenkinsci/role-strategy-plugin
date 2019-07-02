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
import javax.annotation.ParametersAreNonnullByDefault;
import javax.servlet.ServletException;

import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import io.jenkins.plugins.rolestrategy.RegexAuthorizationEngine;
import io.jenkins.plugins.rolestrategy.RoleBasedProjectAuthorizationEngine;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.acegisecurity.acls.sid.PrincipalSid;
import org.jenkinsci.plugins.rolestrategy.permissions.DangerousPermissionHandlingMode;
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
  private RoleBasedProjectAuthorizationEngine projectAuthorizationEngine;

  private static final Logger LOGGER = Logger.getLogger(RoleBasedAuthorizationStrategy.class.getName());

  public RoleBasedAuthorizationStrategy() {
      agentRoles = new RoleMap();
      globalRoles = new RoleMap();
      projectAuthorizationEngine = new RegexAuthorizationEngine();
  }

  @ParametersAreNonnullByDefault
  public RoleBasedAuthorizationStrategy(RoleMap globalRoles, RoleMap agentRoles,
                                        RoleBasedProjectAuthorizationEngine projectAuthorizationEngine) {
      this.globalRoles = globalRoles;
      this.agentRoles = agentRoles;
      this.projectAuthorizationEngine = projectAuthorizationEngine;
  }

  /**
   * Creates a new {@link RoleBasedAuthorizationStrategy}
   *
   * @param grantedRoles the roles in the strategy
   * @deprecated Use {@link #RoleBasedAuthorizationStrategy(RoleMap, RoleMap, RoleBasedProjectAuthorizationEngine)}
   */
  @Deprecated
  public RoleBasedAuthorizationStrategy(Map<String, RoleMap> grantedRoles) {
      RoleMap map = grantedRoles.get(SLAVE);
      agentRoles = map == null ? new RoleMap() : map;

      map = grantedRoles.get(GLOBAL);
      globalRoles = map == null ? new RoleMap() : map;

      map = grantedRoles.get(PROJECT);
      projectAuthorizationEngine = new RegexAuthorizationEngine(map == null ? new RoleMap() : map);
  }

  /**
   * Get the root ACL.
   * @return The global ACL
   */
  @Override
  @Nonnull
  public SidACL getRootACL() {
    return globalRoles.getACL(RoleType.Global, null);
  }

    /**
     * Get the specific ACL for projects.
     *
     * @param project The access-controlled project
     * @return The project specific ACL
     */
    @Override
    @Nonnull
    public ACL getACL(@Nonnull Job<?,?> project) {
      return getACL((AbstractItem) project);
    }

    @Override
    @Nonnull
    public ACL getACL(@Nonnull AbstractItem project) {
        return projectAuthorizationEngine.getACL(project).newInheritingACL(getRootACL());
    }

    @Override
    @Nonnull
    public ACL getACL(@Nonnull Computer computer) {
        return agentRoles.newMatchingRoleMap(computer.getName()).getACL(RoleType.Slave, computer)
                .newInheritingACL(getRootACL());
    }
  
  /**
   * Used by the container realm.
   * @return All the sids referenced by the strategy
   */
  @Override
  @Nonnull
  public Collection<String> getGroups() {
    Set<String> sids = new HashSet<>();
    sids.addAll(globalRoles.getSids(true));
    sids.addAll(projectAuthorizationEngine.getSids(true));
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
     *
     * @param type the type of the role
     * @return roles mapped to the set of user sids assigned to that role
     * @throws UnsupportedOperationException if the {@link RoleType} is {@link RoleType#Project} and the
     *                                       engine does not use {@link Role}
     * @throws IllegalArgumentException      if the {@link RoleType} is not supported
     * @since TODO
     */
    public SortedMap<Role, Set<String>> getGrantedRoles(@Nonnull RoleType type) {
        switch (type) {
            case Global:
                return globalRoles.getGrantedRoles();
            case Slave:
                return agentRoles.getGrantedRoles();
            case Project:
                return projectAuthorizationEngine.getGrantedRoles();
            default:
                throw new IllegalArgumentException("Unsupported RoleType");
        }
    }

  /**
   * Get all the SIDs referenced by specified {@link RoleMap} type.
   * @param type The object type controlled by the {@link RoleMap}
   * @return All SIDs from the specified {@link RoleMap}.
   * @throws IllegalArgumentException when unknown role type is provided
   */
  @Nonnull
  @SuppressWarnings("unused")
  public Set<String> getSIDs(String type) {
      RoleType roleType = RoleType.fromString(type);
      switch (roleType) {
          case Global:
              return globalRoles.getSids();
          case Slave:
              return agentRoles.getSids();
          case Project:
              return new HashSet<>(projectAuthorizationEngine.getSids(false));
          default:
              throw new IllegalArgumentException("Unknown RoleType");
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
                          @QueryParameter String pattern) throws IOException {
        checkAdminPerm();

        final RoleType roleType = RoleType.fromString(type);
        boolean shouldOverwrite = Boolean.parseBoolean(overwrite);
        String pttrn = Role.GLOBAL_ROLE_PATTERN;

        if (roleType != RoleType.Global && pattern != null) {
            pttrn = pattern;
        }

        ArrayList<String> permissionList = new ArrayList<>(Arrays.asList(permissionIds.split(",")));

        Set<Permission> permissionSet = new HashSet<>();
        for (String permissionId : permissionList) {
            Permission permission = Permission.fromId(permissionId);
            if (permission == null) {
                throw new IOException("Cannot find permission for id=" + permissionId + ", role name=" +
                        roleName + " role type=" + type);
            } else {
                permissionSet.add(permission);
            }
        }

        Role role = new Role(roleName, pttrn, permissionSet);
        switch (roleType) {
            case Global:
                globalRoles.addRole(shouldOverwrite, role);
                break;
            case Slave:
                agentRoles.addRole(shouldOverwrite, role);
                break;
            case Project:
                projectAuthorizationEngine.addRole(shouldOverwrite, role);
                break;
            default:
                throw new IllegalArgumentException("Unknown RoleType.");
        }

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
                          @QueryParameter(required = true) String roleName) throws IOException {
        checkAdminPerm();

        @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
        JSONObject responseJson = new JSONObject();

        RoleType roleType = RoleType.fromString(type);

        switch (roleType) {
            case Global:
                globalRoles.marshalRoleToJson(roleName, RoleType.Global, responseJson);
                break;
            case Slave:
                agentRoles.marshalRoleToJson(roleName, RoleType.Slave, responseJson);
                break;
            case Project:
                projectAuthorizationEngine.marshalRoleToJson(roleName, responseJson);
                break;
            default:
                throw new IllegalArgumentException("Unsupported RoleType");
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
     * @throws IOException              in case saving changes fails
     * @throws IllegalArgumentException when unknown RoleType is provided
     * @since 2.5.0
     */
    @RequirePOST
    @Restricted(NoExternalUse.class)
    public void doRemoveRoles(@QueryParameter(required = true) String type,
                              @QueryParameter(required = true) String roleNames) throws IOException {
        checkAdminPerm();

        String[] split = roleNames.split(",");

        switch (RoleType.fromString(type)) {
            case Global:
                globalRoles.removeRoles(split);
                break;
            case Slave:
                agentRoles.removeRoles(split);
                break;
            case Project:
                projectAuthorizationEngine.removeRoles(split);
                break;
            default:
                throw new IllegalArgumentException("Unknown RoleType");
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
     * @throws IllegalArgumentException if the roleType is not known
     * @since 2.5.0
     */
    @RequirePOST
    @Restricted(NoExternalUse.class)
    public void doAssignRole(@QueryParameter(required = true) String type,
                             @QueryParameter(required = true) String roleName,
                             @QueryParameter(required = true) String sid) throws IOException {
        checkAdminPerm();

        final RoleType roleType = RoleType.fromString(type);
        final Role role;

        switch (roleType) {
            case Global:
                role = globalRoles.getRole(roleName);
                if (Objects.nonNull(role)) {
                    globalRoles.assignRole(role, sid);
                }
                break;
            case Slave:
                role = agentRoles.getRole(roleName);
                if (Objects.nonNull(role)) {
                    agentRoles.assignRole(role, sid);
                }
                break;
            case Project:
                projectAuthorizationEngine.assignRole(roleName, sid);
                break;
            default:
                throw new IllegalArgumentException("Unknown RoleType");
        }

        persistChanges();
    }

    private static void persistChanges() throws IOException {
        instance().save();
    }

    private static Jenkins instance() {
        return Jenkins.getInstance();
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
        switch (RoleType.fromString(type)) {
            case Global:
                globalRoles.deleteSids(sid);
                break;
            case Slave:
                agentRoles.deleteSids(sid);
                break;
            case Project:
                projectAuthorizationEngine.deleteSids(sid);
                break;
        }
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

        switch (RoleType.fromString(type)) {
            case Global:
                globalRoles.deleteRoleSid(sid, roleName);
                break;
            case Slave:
                agentRoles.deleteRoleSid(sid, roleName);
                break;
            case Project:
                projectAuthorizationEngine.unassignSidFromRole(sid, roleName);
                break;
            default:
                throw new IllegalArgumentException("Unsupported RoleType");
        }

        persistChanges();
    }

    /**
     * Gets the sids assigned to each role.
     * <p>
     * Example: {@code curl -X GET localhost:8080/role-strategy/strategy/getAllRoles?type=projectRoles}
     *
     * @param type (globalRoles by default, projectRoles, slaveRoles)
     * @throws IllegalArgumentException when unknown type is provided
     * @since 2.6.0
     */
    @Restricted(NoExternalUse.class)
    public void doGetAllRoles(@QueryParameter(fixEmpty = true) String type) throws IOException {
        checkAdminPerm();

        @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
        JSONObject responseJson = new JSONObject();

        RoleType roleType = type == null ? RoleType.Global : RoleType.fromString(type);

        switch (roleType) {
            case Global:
                globalRoles.marshalAssignedSidsToJson(responseJson);
                break;
            case Slave:
                agentRoles.marshalAssignedSidsToJson(responseJson);
                break;
            case Project:
                projectAuthorizationEngine.marshalAssignedSidsToJson(responseJson);
                break;
            default:
                throw new IllegalArgumentException("Unsupported RoleType");
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
        Writer writer = Stapler.getCurrentResponse().getCompressedWriter(Stapler.getCurrentRequest());
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
   * Project) will be introduced.
   */
  @SuppressWarnings("unused")
  public static class ConverterImpl implements Converter {
      @Override
      public boolean canConvert(Class type) {
        return type == RoleBasedAuthorizationStrategy.class;
      }

      @Override
      public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
        RoleBasedAuthorizationStrategy strategy = (RoleBasedAuthorizationStrategy) source;
        strategy.globalRoles.marshal(writer, RoleType.Global);
        strategy.agentRoles.marshal(writer, RoleType.Slave);
        strategy.projectAuthorizationEngine.marshal(writer);
      }

      @Override
      public RoleBasedAuthorizationStrategy unmarshal(HierarchicalStreamReader reader,
                                                      final UnmarshallingContext context) {
        RoleMap agentRoles = null;
        RoleMap globalRoles = null;
        RoleMap projectRoles = null;

        RoleBasedProjectAuthorizationEngine projectAuthorizationEngine = null;

        while(reader.hasMoreChildren()) {
            reader.moveDown();

            // roleMaps
            String nodeName = reader.getNodeName();
            if(nodeName.equals("roleMap")) {
                RoleType roleType = RoleType.fromString(reader.getAttribute("type"));
                RoleMap roleMap = RoleMap.unmarshal(reader);
                switch (roleType) {
                    case Global:
                        globalRoles = roleMap;
                        break;
                    case Slave:
                        agentRoles = roleMap;
                        break;
                    case Project:
                        projectRoles = roleMap;
                        break;
                }
            } else if (nodeName.equals("projectAuthorizationEngine")) {
                String type = reader.getAttribute("class");
                try {
                    @SuppressWarnings("unchecked")
                    Class<? extends RoleBasedProjectAuthorizationEngine> clazz =
                            (Class<? extends RoleBasedProjectAuthorizationEngine>) Class.forName(type);
                    RoleBasedProjectAuthorizationEngine engine = clazz.newInstance();
                    projectAuthorizationEngine = engine.configure(reader);
                } catch (IllegalAccessException | InstantiationException e) {
                    LOGGER.log(Level.WARNING, "The class should have public no-arg constructor.", e);
                } catch (ClassNotFoundException | ClassCastException e) {
                    LOGGER.log(Level.WARNING, "Unable to instantiate projectAuthorizationEngine.", e);
                }
          }

          reader.moveUp();
        }

        if (projectAuthorizationEngine == null) {
            // compatibility mode: use the format before RoleBasedProjectAuthorizationEngine existed
            LOGGER.log(Level.INFO, "Entering compatibility mode, no projectAuthorizationEngine detected.");
            projectAuthorizationEngine = new RegexAuthorizationEngine(projectRoles == null ? new RoleMap() : projectRoles);
        }

        if (agentRoles == null) {
            agentRoles = new RoleMap();
        }

        if (globalRoles == null) {
            globalRoles = new RoleMap();
        }

        return new RoleBasedAuthorizationStrategy(globalRoles, agentRoles, projectAuthorizationEngine);
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
    void renewMacroRoles() {
        //TODO: add mandatory roles

        // Check role extensions
        for (UserMacroExtension userExt : UserMacroExtension.all()) {
            if (userExt.IsApplicable(RoleType.Global)) {
                globalRoles.getSids().contains(userExt.getName());
            }
        }
    }

    /**
     * Control job create using {@link org.jenkinsci.plugins.rolestrategy.RoleBasedProjectNamingStrategy}.
     * @since 2.2.0
     * @deprecated Always available since 1.566
     */
    @Deprecated
    public static boolean isCreateAllowed() {
        return true;
    }

  /**
   * Descriptor used to bind the strategy to the Web forms.
   */
  public static final class DescriptorImpl extends GlobalMatrixAuthorizationStrategy.DescriptorImpl {

    @Override
    @Nonnull
    public  String getDisplayName() {
      return Messages.RoleBasedAuthorizationStrategy_DisplayName();
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
        RoleBasedAuthorizationStrategy strategy = this.newInstance(req, json);
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
            strategy.agentRoles.assignSidsFromJson(json, RoleType.Slave);
            strategy.globalRoles.assignSidsFromJson(json, RoleType.Global);
            strategy.projectAuthorizationEngine.assignRolesFromJson(json);

            // Persist the data
            persistChanges();
        }
    }

    /**
     * Method called on Jenkins Manage panel submission, and plugin specific forms
     * to create the {@link AuthorizationStrategy} object.
     */
    @Override
    public RoleBasedAuthorizationStrategy newInstance(StaplerRequest req, JSONObject formData) {
      AuthorizationStrategy oldStrategy = instance().getAuthorizationStrategy();
      RoleBasedAuthorizationStrategy strategy;

      // If the form contains data, it means the method has been called by plugin
      // specifics forms, and we need to handle it.
      if (formData.has(GLOBAL) && formData.has(PROJECT) && formData.has(SLAVE) && oldStrategy instanceof RoleBasedAuthorizationStrategy) {
        strategy = new RoleBasedAuthorizationStrategy();
        RoleBasedAuthorizationStrategy oldRbas = (RoleBasedAuthorizationStrategy) oldStrategy;

        JSONObject globalRoles = formData.getJSONObject(GLOBAL);
        for (Map.Entry<String, JSONObject> r : (Set<Map.Entry<String, JSONObject>>) globalRoles.getJSONObject("data").entrySet()) {
          String roleName = r.getKey();
          Set<Permission> permissions = getPermissionsFromJson(r);

          Role role = new Role(roleName, permissions);
          strategy.globalRoles.addRole(role);

          Set<String> sids = oldRbas.globalRoles.getSidsForRole(roleName);
          if (sids != null) {
            for (String sid : sids) {
              strategy.globalRoles.assignRole(role, sid);
            }
          }
        }

        RoleMap.addRolesAndCopySids(oldRbas.agentRoles, strategy.agentRoles, formData, SLAVE);
        strategy.projectAuthorizationEngine = RoleBasedProjectAuthorizationEngine.
                newFromFormData(formData, oldRbas.projectAuthorizationEngine);
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
        strategy.globalRoles.addRole(createAdminRole());
        strategy.globalRoles.assignRole(adminRole, getCurrentUser());
      }
      
      strategy.renewMacroRoles();
      return strategy;
    }

    @Restricted(NoExternalUse.class)
    public static Set<Permission> getPermissionsFromJson(@Nonnull Map.Entry<String, JSONObject> r) {
      HashSet<Permission> permissions = new HashSet<>();
      for (Map.Entry<String, Boolean> e : (Set<Map.Entry<String,Boolean>>)r.getValue().entrySet()) {
          if (e.getValue()) {
              Permission p = Permission.fromId(e.getKey());
              permissions.add(p);
          }
      }
      return permissions;
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
    public List<PermissionGroup> getGroups(@Nonnull String type) {
        List<PermissionGroup> groups;
        if (type.equals(GLOBAL)) {
            groups = new ArrayList<>(PermissionGroup.getAll());
            groups.remove(PermissionGroup.get(Permission.class));
        }
        else if (type.equals(PROJECT)) {
            groups = new ArrayList<>(PermissionGroup.getAll());
            groups.remove(PermissionGroup.get(Permission.class));
            groups.remove(PermissionGroup.get(Hudson.class));
            groups.remove(PermissionGroup.get(Computer.class));
            groups.remove(PermissionGroup.get(View.class));
        }
        else if (type.equals(SLAVE)) {
            groups = new ArrayList<>(PermissionGroup.getAll());
            groups.remove(PermissionGroup.get(Permission.class));
            groups.remove(PermissionGroup.get(Hudson.class));
            groups.remove(PermissionGroup.get(View.class));
            
            // Project, SCM and Run permissions 
            groups.remove(PermissionGroup.get(Item.class));
            groups.remove(PermissionGroup.get(SCM.class));
            groups.remove(PermissionGroup.get(Run.class));
        }
        else {
            groups = null;
        }
        return groups;
    }

    @Restricted(NoExternalUse.class)
    public boolean hasDangerousPermissions() {
        RoleBasedAuthorizationStrategy instance = RoleBasedAuthorizationStrategy.getInstance();
        if (instance == null) {
            // Should never happen
            return false;
        }
        return PermissionHelper.hasDangerousPermissions(instance);
    }
    
    @Restricted(NoExternalUse.class)
    public boolean showPermission(String type, Permission p) {
        return showPermission(type, p, false);
    }
    
    /**
     * Check if the permission should be displayed.
     * For Stapler only.
     */
    @Restricted(NoExternalUse.class)
    public boolean showPermission(String type, Permission p, boolean showDangerous) {
      if(type.equals(GLOBAL)) {
        if (PermissionHelper.isDangerous(p)) {
            // Consult with the Security strategy
            RoleBasedAuthorizationStrategy instance = RoleBasedAuthorizationStrategy.getInstance();
            if (instance == null) {
                // Should never happen
                return false;
            }
            
            // When disabled, never show the permissions
            return showDangerous && DangerousPermissionHandlingMode.getCurrent() != DangerousPermissionHandlingMode.DISABLED;
        }
        return p.getEnabled();
      }
      else if (type.equals(PROJECT)) {
        return p == Item.CREATE && p.getEnabled() || p != Item.CREATE && p.getEnabled();
      }
      else if (type.equals(SLAVE)) {
          return p!=Computer.CREATE && p.getEnabled();
      }
      else {
        return false;
      }
    }

    /**
     * Returns the projectAuthorizationEngine for this {@link RoleBasedAuthorizationStrategy}
     *
     * @return the projectAuthorizationEngine if the {@link AuthorizationStrategy} is
     * {@link RoleBasedAuthorizationStrategy}; null otherwise
     */
    @Nullable
    static RoleBasedProjectAuthorizationEngine getProjectAuthorizationEngine() {
        RoleBasedAuthorizationStrategy strategy = getInstance();
        if (strategy != null) {
            return strategy.projectAuthorizationEngine;
        } else {
            return null;
        }
    }
  }
}
