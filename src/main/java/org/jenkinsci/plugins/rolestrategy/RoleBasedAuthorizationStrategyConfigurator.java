package org.jenkinsci.plugins.rolestrategy;


import com.michelin.cio.hudson.plugins.rolestrategy.Role;
import com.michelin.cio.hudson.plugins.rolestrategy.RoleBasedAuthorizationStrategy;
import com.michelin.cio.hudson.plugins.rolestrategy.RoleMap;
import hudson.Extension;
import org.jenkinsci.plugins.casc.Configurator;
import org.jenkinsci.plugins.casc.ConfiguratorException;
import org.jenkinsci.plugins.casc.MultivaluedAttribute;
import org.jenkinsci.plugins.casc.model.CNode;
import org.jenkinsci.plugins.casc.model.Mapping;
import org.jenkinsci.plugins.casc.model.Sequence;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Provides the configuration logic for Role Strategy plugin.
 * @author Oleg Nenashev
 * @since TODO
 */
@Extension(optional = true)
@Restricted({NoExternalUse.class})
public class RoleBasedAuthorizationStrategyConfigurator extends Configurator<RoleBasedAuthorizationStrategy> {

    @Override
    public String getName() {
        return "roleStrategy";
    }

    @Override
    public Class<RoleBasedAuthorizationStrategy> getTarget() {
        return RoleBasedAuthorizationStrategy.class;
    }

    @Override
    public RoleBasedAuthorizationStrategy configure(CNode config) throws ConfiguratorException {
        //TODO: API should return a qualified type
        final Configurator<RoleDefinition> roleDefinitionConfigurator =
                (Configurator<RoleDefinition>) Configurator.lookupOrFail(RoleDefinition.class);

        Mapping map = config.asMapping();
        Map<String, RoleMap> grantedRoles = new HashMap<>();

        CNode rolesConfig = map.get("roles");
        if (rolesConfig != null) {
            grantedRoles.put(RoleBasedAuthorizationStrategy.GLOBAL,
                    retrieveRoleMap(rolesConfig, "global", roleDefinitionConfigurator));
            grantedRoles.put(RoleBasedAuthorizationStrategy.PROJECT,
                    retrieveRoleMap(rolesConfig, "items", roleDefinitionConfigurator));
            grantedRoles.put(RoleBasedAuthorizationStrategy.SLAVE,
                    retrieveRoleMap(rolesConfig, "agents", roleDefinitionConfigurator));
        }
        return new RoleBasedAuthorizationStrategy(grantedRoles);
    }

    @Nonnull
    private static RoleMap retrieveRoleMap(@Nonnull CNode config, @Nonnull String name, Configurator<RoleDefinition> configurator) throws ConfiguratorException {
        Mapping map = config.asMapping();
        final Sequence c = map.get(name).asSequence();

        TreeMap<Role, Set<String>> resMap = new TreeMap<>();
        if (c == null) {
            // we cannot return emptyMap here due to the Role Strategy code
            return new RoleMap(resMap);
        }

        for (CNode entry : c) {
            RoleDefinition definition = configurator.configure(entry);
            resMap.put(definition.getRole(), definition.getAssignments());
        }

        return new RoleMap(resMap);
    }

    @Override
    public Set describe() {
        return new HashSet<>(Arrays.asList(
                new MultivaluedAttribute<RoleBasedAuthorizationStrategy, RoleDefinition>("global", RoleDefinition.class),
                new MultivaluedAttribute<RoleBasedAuthorizationStrategy, RoleDefinition>("items", RoleDefinition.class),
                new MultivaluedAttribute<RoleBasedAuthorizationStrategy, RoleDefinition>("agents", RoleDefinition.class)
        ));
    }

    @CheckForNull
    @Override
    public CNode describe(RoleBasedAuthorizationStrategy instance) throws Exception {
        Mapping mapping = new Mapping();
        final Configurator c = Configurator.lookupOrFail(RoleDefinition.class);

        mapping.put("global", exportRoleMap(instance, c, RoleBasedAuthorizationStrategy.GLOBAL));
        mapping.put("items", exportRoleMap(instance, c, RoleBasedAuthorizationStrategy.PROJECT));
        mapping.put("agents", exportRoleMap(instance, c, RoleBasedAuthorizationStrategy.SLAVE));

        return mapping;
    }

    private CNode exportRoleMap(RoleBasedAuthorizationStrategy instance, Configurator c, String scope) throws Exception {
        final SortedMap<Role, Set<String>> roles = instance.getGrantedRoles(scope);
        if (roles == null) return null;
        Sequence sequence = new Sequence();
        for (Map.Entry<Role, Set<String>> entry : roles.entrySet()) {
            sequence.add(c.describe(new RoleDefinition(entry.getKey().getName(), null, null, Collections.<String>emptySet(), entry.getValue())));
        }
        return sequence;
    }


}
