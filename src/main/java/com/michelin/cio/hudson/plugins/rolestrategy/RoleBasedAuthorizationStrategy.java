/*
 * The MIT License
 *
 * Copyright (c) 2010, Manufacture Française des Pneumatiques Michelin, Thomas Maurel
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

import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import hudson.Extension;
import hudson.model.Hudson;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.Run;
import hudson.security.ACL;
import hudson.security.AccessControlled;
import hudson.security.AuthorizationStrategy;
import hudson.security.GlobalMatrixAuthorizationStrategy;
import hudson.security.Permission;
import hudson.security.PermissionGroup;
import hudson.security.SidACL;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import javax.servlet.ServletException;
import net.sf.json.JSONObject;
import org.acegisecurity.acls.sid.PrincipalSid;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 * Role-based authorization strategy.
 * @author Thomas Maurel
 */
public class RoleBasedAuthorizationStrategy extends AuthorizationStrategy {

  public final static String GLOBAL    = "globalRoles";
  public final static String PROJECT   = "projectRoles";

  /** {@link RoleMap}s associated to each {@link AccessControlled} class */
  private final Map <String, RoleMap> grantedRoles = new HashMap < String, RoleMap >();

  /**
   * Get the root ACL.
   * @return The global ACL
   */
  @Override
  public SidACL getRootACL() {
    RoleMap root = getRoleMap(GLOBAL);
    return root.getACL();
  }

  /**
   * Get the specific ACL for projects.
   * @param project The access-controlled project
   * @return The project specific ACL
   */
  @Override
  public ACL getACL(Job<?,?> project) {
    SidACL acl;
    RoleMap roleMap = grantedRoles.get(PROJECT);
    if(roleMap == null) {
      acl = getRootACL();
    }
    else {
      // Create a sub-RoleMap matching the project name, and create an inheriting from root ACL
      acl = roleMap.newMatchingRoleMap(project.getName()).getACL().newInheritingACL(getRootACL());
    }
    return acl;
  }

  /**
   * Used by the container realm.
   * @return All the sids referenced by the strategy
   */
  @Override
  public Collection<String> getGroups() {
    Set<String> sids = new HashSet<String>();
    for(Map.Entry entry : this.grantedRoles.entrySet()) {
      RoleMap roleMap = (RoleMap) entry.getValue();
      sids.addAll(roleMap.getSids(true));
    }
    return sids;
  }

  /**
   * Get the roles from the global {@link RoleMap}.
   * <p>The returned sorted map is unmodifiable.</p>
   * @param type The object type controlled by the {@link RoleMap}
   * @return All roles from the global {@link RoleMap}
   */
  public SortedMap<Role, Set<String>> getGrantedRoles(String type) {
    RoleMap roleMap = this.getRoleMap(type);
    if(roleMap != null) {
      return roleMap.getGrantedRoles();
    }
    return null;
  }

  /**
   * Get all the SIDs referenced by specified {@link RoleMap} type.
   * @param type The object type controlled by the {@link RoleMap}
   * @return All SIDs from the specified {@link RoleMap}.
   */
  public Set<String> getSIDs(String type) {
    RoleMap roleMap = this.getRoleMap(type);
    if(roleMap != null) {
      return roleMap.getSids();
    }
    return null;
  }

  /**
   * Get the {@link RoleMap} associated to the given class.
   * @param type The object type controlled by the {@link RoleMap}
   * @return The associated {@link RoleMap}
   */
  private RoleMap getRoleMap(String type) {
    RoleMap map;
    if(grantedRoles.containsKey(type)) {
       map = grantedRoles.get(type);
    }
    else {
      // Create it if it doesn't exist
      map = new RoleMap();
      grantedRoles.put(type, map);
    }
    return map;
  }

  /**
   * Returns a map associating a string representation with each {@link RoleMap}.
   * <p>This method is intended to be used for XML serialization purposes (take
   * a look at the {@link ConverterImpl}) and, as such, must remain private
   * since it exposes all the security config.</p>
   */
  private Map<String, RoleMap> getRoleMaps() {
    return grantedRoles;
  }

  /**
   * Add the given {@link Role} to the {@link RoleMap} associated to the provided class.
   * @param type The {@link AccessControlled} class referencing the {@link RoleMap}
   * @param role The {@link Role} to add
   */
  private void addRole(String type, Role role) {
    RoleMap roleMap = this.grantedRoles.get(type);
    if(roleMap != null) {
      roleMap.addRole(role);
    }
    else
    {
      // Create the RoleMap if it doesnt exist
      roleMap = new RoleMap();
      roleMap.addRole(role);
      grantedRoles.put(type, roleMap);
    }
  }

  /**
   * Assign a role to a sid
   * @param type The type of role
   * @param role The role to assign
   * @param sid The sid to assign to
   */
  private void assignRole(String type, Role role, String sid) {
    RoleMap roleMap = this.grantedRoles.get(type);
    if(roleMap != null && roleMap.hasRole(role)) {
      roleMap.assignRole(role, sid);
    }
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
        Map<String, RoleMap> maps = strategy.getRoleMaps();
        for(Map.Entry<String, RoleMap> map : maps.entrySet()) {
          RoleMap roleMap = map.getValue();
          writer.startNode("roleMap");
          writer.addAttribute("type", map.getKey());

          for(Map.Entry<Role, Set<String>> grantedRole : roleMap.getGrantedRoles().entrySet()) {
            Role role = grantedRole.getKey();
            if(role != null) {
              writer.startNode("role");
              writer.addAttribute("name", role.getName());
              writer.addAttribute("pattern", role.getPattern().pattern());

              writer.startNode("permissions");
              for(Permission permission : role.getPermissions()) {
                writer.startNode("permission");
                writer.setValue(permission.getId());
                writer.endNode();
              }
              writer.endNode();

              writer.startNode("assignedSIDs");
              for(String sid : grantedRole.getValue()) {
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
        RoleBasedAuthorizationStrategy strategy = create();

        while(reader.hasMoreChildren()) {
          reader.moveDown();
          if(reader.getNodeName().equals("roleMap")) {
            String type = reader.getAttribute("type");
            RoleMap map = new RoleMap();
            while(reader.hasMoreChildren()) {
              reader.moveDown();
              String name = reader.getAttribute("name");
              String pattern = reader.getAttribute("pattern");
              Set<Permission> permissions = new HashSet<Permission>();

              String next = reader.peekNextChild();
              if(next != null && next.equals("permissions")) {
                reader.moveDown();
                while(reader.hasMoreChildren()) {
                  reader.moveDown();
                  permissions.add(Permission.fromId(reader.getValue()));
                  reader.moveUp();
                }
                reader.moveUp();
              }

              Role role = new Role(name, pattern, permissions);
              map.addRole(role);

              next = reader.peekNextChild();
              if(next != null && next.equals("assignedSIDs")) {
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
            strategy.grantedRoles.put(type, map);
          }
          reader.moveUp();
        }
        return strategy;
      }

      protected RoleBasedAuthorizationStrategy create() {
          return new RoleBasedAuthorizationStrategy();
      }
  }  

  /**
   * Descriptor used to bind the strategy to the Web forms.
   */
  public static final class DescriptorImpl extends GlobalMatrixAuthorizationStrategy.DescriptorImpl {

    @Override
    public  String getDisplayName() {
      return Messages.RoleBasedAuthorizationStrategy_DisplayName();
    }

    /** 
     * Called on role management form's submission.
     */
    public void doRolesSubmit(StaplerRequest req, StaplerResponse rsp) throws UnsupportedEncodingException, ServletException, FormException, IOException {
      Hudson.getInstance().checkPermission(Hudson.ADMINISTER);
      
      req.setCharacterEncoding("UTF-8");
      JSONObject json = req.getSubmittedForm();
      AuthorizationStrategy strategy = this.newInstance(req, json);
      Hudson.getInstance().setAuthorizationStrategy(strategy);
      // Persist the data
      Hudson.getInstance().save();
    }

    /**
     * Called on role assignment form's submission.
     */
    public void doAssignSubmit(StaplerRequest req, StaplerResponse rsp) throws UnsupportedEncodingException, ServletException, FormException, IOException {
      Hudson.getInstance().checkPermission(Hudson.ADMINISTER);
      
      req.setCharacterEncoding("UTF-8");
      JSONObject json = req.getSubmittedForm();
      AuthorizationStrategy oldStrategy = Hudson.getInstance().getAuthorizationStrategy();
      
      if (json.has(GLOBAL) && json.has(PROJECT) && oldStrategy instanceof RoleBasedAuthorizationStrategy) {
        RoleBasedAuthorizationStrategy strategy = (RoleBasedAuthorizationStrategy) oldStrategy;
        Map<String, RoleMap> maps = strategy.getRoleMaps();

        for(Map.Entry<String, RoleMap> map : maps.entrySet()) {
          RoleMap roleMap = map.getValue();
          roleMap.clearSids();
          JSONObject roles = json.getJSONObject(map.getKey());
          for(Map.Entry<String,JSONObject> r : (Set<Map.Entry<String,JSONObject>>)roles.getJSONObject("data").entrySet()) {
            String sid = r.getKey();
            for(Map.Entry<String,Boolean> e : (Set<Map.Entry<String,Boolean>>)r.getValue().entrySet()) {
              if(e.getValue()) {
                Role role = roleMap.getRole(e.getKey());
                if(role != null && sid != null && !sid.equals("")) {
                  roleMap.assignRole(role, sid);
                }
              }
            }
          }
        }
        // Persist the data
        Hudson.getInstance().save();
      }
    }

    /**
     * Method called on Hudson Manage panel submission, and plugin specific forms
     * to create the {@link AuthorizationStrategy} object.
     */
    @Override
    public AuthorizationStrategy newInstance(StaplerRequest req, JSONObject formData) throws FormException {
      AuthorizationStrategy oldStrategy = Hudson.getInstance().getAuthorizationStrategy();
      RoleBasedAuthorizationStrategy strategy;

      // If the form contains data, it means the method has been called by plugin
      // specifics forms, and we need to handle it.
      if (formData.has(GLOBAL) && formData.has(PROJECT) && oldStrategy instanceof RoleBasedAuthorizationStrategy) {
        strategy = new RoleBasedAuthorizationStrategy();

        JSONObject globalRoles = formData.getJSONObject(GLOBAL);
        for(Map.Entry<String,JSONObject> r : (Set<Map.Entry<String,JSONObject>>)globalRoles.getJSONObject("data").entrySet()) {
          String roleName = r.getKey();
          Set<Permission> permissions = new HashSet<Permission>();
          for(Map.Entry<String,Boolean> e : (Set<Map.Entry<String,Boolean>>)r.getValue().entrySet()) {
              if(e.getValue()) {
                  Permission p = Permission.fromId(e.getKey());
                  permissions.add(p);
              }
          }

          Role role = new Role(roleName, permissions);
          strategy.addRole(GLOBAL, role);
          RoleMap roleMap = ((RoleBasedAuthorizationStrategy) oldStrategy).getRoleMap(GLOBAL);
          if(roleMap != null) {
            Set<String> sids = roleMap.getSidsForRole(roleName);
            if(sids != null) {
              for(String sid : sids) {
                strategy.assignRole(GLOBAL, role, sid);
              }
            }
          }
        }

        JSONObject projectRoles = formData.getJSONObject(PROJECT);
        for(Map.Entry<String,JSONObject> r : (Set<Map.Entry<String,JSONObject>>)projectRoles.getJSONObject("data").entrySet()) {
          String roleName = r.getKey();
          Set<Permission> permissions = new HashSet<Permission>();
          String pattern = r.getValue().getString("pattern");
          if(pattern != null) {
            r.getValue().remove("pattern");
          }
          else {
            pattern = ".*";
          }
          for(Map.Entry<String,Boolean> e : (Set<Map.Entry<String,Boolean>>)r.getValue().entrySet()) {
              if(e.getValue()) {
                  Permission p = Permission.fromId(e.getKey());
                  permissions.add(p);
              }
          }

          Role role = new Role(roleName, pattern, permissions);
          strategy.addRole(PROJECT, role);

          RoleMap roleMap = ((RoleBasedAuthorizationStrategy) oldStrategy).getRoleMap(PROJECT);
          if(roleMap != null) {
            Set<String> sids = roleMap.getSidsForRole(roleName);
            if(sids != null) {
              for(String sid : sids) {
                strategy.assignRole(PROJECT,role, sid);
              }
            }
          }
        }
      }
      // When called from Hudson Manage panel, but was already on a role-based strategy
      else if(oldStrategy instanceof RoleBasedAuthorizationStrategy) {
        // Do nothing, keep the same strategy
        strategy = (RoleBasedAuthorizationStrategy) oldStrategy;
      }
      // When called from Hudson Manage panel, but when the previous strategy wasn't
      // role-based, it means we need to create an admin role, and assign it to the
      // current user to not throw him out of the webapp
      else {
        strategy = new RoleBasedAuthorizationStrategy();
        Role adminRole = createAdminRole();
        strategy.addRole(GLOBAL, adminRole);
        strategy.assignRole(GLOBAL, adminRole, getCurrentUser());
      }
      return strategy;
    }

    /**
     * Create an admin role.
     */
    private Role createAdminRole() {
      Set<Permission> permissions = new HashSet<Permission>();
      for(PermissionGroup group : getGroups(GLOBAL)) {
        for(Permission permission : group) {
          permissions.add(permission);
        }
      }
      Role role = new Role("admin", permissions);
      return role;
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
     */
    public List<PermissionGroup> getGroups(String type) {
        List<PermissionGroup> groups;
        if(type.equals(GLOBAL)) {
          groups = new ArrayList<PermissionGroup>(PermissionGroup.getAll());
          groups.remove(PermissionGroup.get(Permission.class));
        }
        else if(type.equals(PROJECT)) {
          groups = Arrays.asList(PermissionGroup.get(Item.class),PermissionGroup.get(Run.class));
        }
        else {
          groups = null;
        }
        return groups;
    }

    /**
     * Check if the permission should be shown.
     */
    public boolean showPermission(String type, Permission p) {
      if(type.equals(GLOBAL)) {
        return showPermission(p);
      }
      else if(type.equals(PROJECT)) {
        return p!=Item.CREATE && p.getEnabled();
      }
      else {
        return false;
      }
    }

  }

}