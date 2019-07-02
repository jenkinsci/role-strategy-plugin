package io.jenkins.plugins.rolestrategy;

import com.michelin.cio.hudson.plugins.rolestrategy.Role;
import com.michelin.cio.hudson.plugins.rolestrategy.RoleBasedAuthorizationStrategy;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.AbstractItem;
import hudson.security.SidACL;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.Set;
import java.util.SortedMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * An engine that can be used for Item or Agent authorization inside a {@link RoleBasedAuthorizationStrategy}.
 * <p>
 * All subclasses need to satisfy the following requirements:
 * <ul>
 * <li>Have a public no-arg constructor
 * <Li>A {@code manage-project-roles.jelly} for managing the project roles
 * <li>A {@code assign-project-roles.jelly} for assigning these roles to users and groups
 * </ul>
 * <p>
 * For these Jelly views, the {@code it} object will be {@link RoleBasedAuthorizationStrategy.DescriptorImpl}
 *
 * @since TODO
 */
public interface RoleBasedProjectAuthorizationEngine extends ExtensionPoint {

    static RoleBasedProjectAuthorizationEngine newFromFormData(JSONObject formData,
                                                               RoleBasedProjectAuthorizationEngine oldEngine) {
        try {
            RoleBasedProjectAuthorizationEngine engine = oldEngine.getClass().newInstance();
            engine.configure(formData, oldEngine);
            return engine;
        } catch (Exception e) {
            Logger.getAnonymousLogger().log(Level.SEVERE, "Unable to instantiate " + oldEngine.getClass().getName() +
                    " from the old form data. Not changing the configuration.");
            return oldEngine;
        }
    }

    @Nonnull
    SidACL getACL(@Nonnull AbstractItem project);

    /**
     * Return the sids on which this engine works.
     *
     * @param includeAnonymous if true, the anonymous sid will be included
     * @return a collection of the sids on which this engine works
     */
    @Nonnull
    Collection<? extends String> getSids(boolean includeAnonymous);

    /**
     * Configure a {@link RoleBasedProjectAuthorizationEngine} using the XML from the reader.
     *
     * @param reader the XML reader at the node containing the configuration.
     * @return RoleBasedProjectAuthorizationEngine that has been configured correctly.
     */
    @Nonnull
    RoleBasedProjectAuthorizationEngine configure(HierarchicalStreamReader reader);

    /**
     * Configure a {@link RoleBasedProjectAuthorizationEngine} using JSON from submitted form data
     *
     * @param formData data submitted through the web form
     * @param old      the old {@link RoleBasedProjectAuthorizationEngine} which has to be replaced
     * @return RoleBasedProjectAuthorizationEngine that has been configured correctly.
     */
    @Nonnull
    RoleBasedProjectAuthorizationEngine configure(JSONObject formData, RoleBasedProjectAuthorizationEngine old);

    /**
     * Marshal the object to XML
     *
     * @param writer the XML writer
     */
    void marshal(HierarchicalStreamWriter writer);

    /**
     * Get all registered {@link RoleBasedProjectAuthorizationEngine}s.
     *
     * @return all registered {@link RoleBasedProjectAuthorizationEngine}
     */
    static ExtensionList<RoleBasedProjectAuthorizationEngine> all() {
        return Jenkins.getInstance().getExtensionList(RoleBasedProjectAuthorizationEngine.class);
    }

    /**
     * Assign roles to users from the {@link JSONObject}.
     *
     * @param json the JSON form data submitted when the roles are assigned.
     */
    void assignRolesFromJson(@Nonnull JSONObject json);

    /**
     * Remove the roles with the given name from this engine.
     *
     * @param roleNames the role names to be removed
     */
    void removeRoles(String[] roleNames);

    /**
     * Adds a role to this engine
     *
     * @param shouldOverride if true, the Role will be overwritten if it existed in the engine.
     * @param params         information about the role to be added, may or may not be supported by the engine.
     * @throws UnsupportedOperationException if unable to add roles
     */
    default void addRole(boolean shouldOverride, Object... params) {
        throw new UnsupportedOperationException("Cannot add roles.");
    }

    /**
     * Assign a role to a given user sid
     *
     * @param roleName the name of the role
     * @param sid      the user's sid
     */
    void assignRole(String roleName, String sid);

    /**
     * Get a map associating a {@link Role} with the sids it is assigned to.
     *
     * @return map associating a {@link Role} with the sids it is assigned to
     * @throws UnsupportedOperationException when the engine does not support this method.
     */
    @Nonnull
    default SortedMap<Role, Set<String>> getGrantedRoles() {
        throw new UnsupportedOperationException("This operation is not supported yet.");
    }

    /**
     * Marshal the role (identified by its name) to JSON
     *
     * @param roleName the name of the role
     * @param json     a mutable {@link JSONObject}
     */
    void marshalRoleToJson(String roleName, JSONObject json);

    /**
     * Delete the sids from all roles
     *
     * @param sids the sids to be deleted
     */
    void deleteSids(String... sids);

    /**
     * Unassigns the sid from the Role identified by its roleName.
     *
     * @param sid      the sid of the user
     * @param roleName the name of the role
     */
    void unassignSidFromRole(String sid, String roleName);

    /**
     * For each role, marshal the sids assigned to it
     *
     * @param json mutable JSON object
     */
    void marshalAssignedSidsToJson(JSONObject json);
}
