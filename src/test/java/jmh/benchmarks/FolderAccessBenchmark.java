package jmh.benchmarks;

import com.cloudbees.hudson.plugins.folder.Folder;
import com.michelin.cio.hudson.plugins.rolestrategy.Role;
import com.michelin.cio.hudson.plugins.rolestrategy.RoleBasedAuthorizationStrategy;
import com.michelin.cio.hudson.plugins.rolestrategy.RoleMap;
import hudson.model.FreeStyleProject;
import hudson.model.TopLevelItem;
import hudson.model.User;
import hudson.security.Permission;
import jenkins.benchmark.jmh.JmhBenchmark;
import jenkins.benchmark.jmh.JmhBenchmarkState;
import jenkins.model.Jenkins;
import org.acegisecurity.context.SecurityContext;
import org.acegisecurity.context.SecurityContextHolder;
import org.jvnet.hudson.test.JenkinsRule;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

@JmhBenchmark
public class FolderAccessBenchmark {
    @State(Scope.Benchmark)
    public static class JenkinsState extends JmhBenchmarkState {
        List<Folder> topFolders = new ArrayList<>();
        FreeStyleProject testProject = null;
        Map<String, TopLevelItem> items = null;
        RoleMap projectRoleMap = null;

        @Override
        public void setup() throws Exception {
            Jenkins jenkins = Objects.requireNonNull(Jenkins.getInstanceOrNull());
            jenkins.setSecurityRealm(new JenkinsRule().createDummySecurityRealm());

            Map<String, RoleMap> rbasMap = new HashMap<>(1);
            SortedMap<Role, Set<String>> projectRoles = new TreeMap<>();

            Set<String> userPermissions = new HashSet<>();
            Collections.addAll(userPermissions,
                      "hudson.model.Item.Discover",
                                "hudson.model.Item.Read");

            Set<String> maintainerPermissions = new HashSet<>();
            Collections.addAll(maintainerPermissions,
                      "hudson.model.Item.Discover",
                                "hudson.model.Item.Read",
                                "hudson.model.Item.Create");

            Set<String> adminPermissions = new HashSet<>();
            Collections.addAll(adminPermissions,
                      "hudson.model.Item.Discover",
                                "hudson.model.Item.Read",
                                "hudson.model.Item.Create",
                                "hudson.model.Item.Configure");

            Random random = new Random(100L);

            /*
             * This configuration was provided by @Straber.
             * Structure:
             *  10 parent folders each containing 5 child folders => total 50 child folders
             *  each child folder contains 5 projects
             *  For each child folder, there are 3 roles: admin, maintainer, user
             *  Total 100 users
             */
            for (int i = 0; i < 10; i++) {
                Folder folder = jenkins.createProject(Folder.class, "TopFolder" + i);
                for (int j = 0; j < 5; j++) {
                    Folder child = folder.createProject(Folder.class, "BottomFolder" + j);

                    // 5 projects for every child folder
                    for (int k = 0; k < 5; k++) {
                        FreeStyleProject project = child.createProject(FreeStyleProject.class, "Project" + k);
                        if (i == 5 && j == 3) {
                            testProject = project;
                        }
                    }

                    Role userRole = new Role(String.format("user%d-%d", i, j),
                            "TopFolder" + i + "(/BottomFolder" + j + "/.*)?",
                            userPermissions, "");
                    Role maintainerRole = new Role(String.format("maintainer%d-%d", i, j),
                            "TopFolder" + i + "/BottomFolder" + j + "(/.*)",
                            maintainerPermissions, "");
                    Role adminRole = new Role(String.format("admin%d-%d", i, j),
                            "TopFolder" + i + "/BottomFolder" + j + "(/.*)",
                            adminPermissions, "");

                    Set<String> users = new HashSet<>();
                    for (int k = 0; k < random.nextInt(5); k++) {
                        users.add("user" + random.nextInt(100));
                    }

                    Set<String> maintainers = new HashSet<>(2);
                    maintainers.add("user" + random.nextInt(100));
                    maintainers.add("user" + random.nextInt(100));

                    Set<String> admin = Collections.singleton("user" + random.nextInt(100));

                    projectRoles.put(userRole, users);
                    projectRoles.put(maintainerRole, maintainers);
                    projectRoles.put(adminRole, admin);
                }
                topFolders.add(folder);
            }

            projectRoleMap = new RoleMap(projectRoles);
            rbasMap.put(RoleBasedAuthorizationStrategy.PROJECT, projectRoleMap);
            RoleBasedAuthorizationStrategy rbas = new RoleBasedAuthorizationStrategy(rbasMap);
            jenkins.setAuthorizationStrategy(rbas);
            Field itemField = Jenkins.class.getDeclaredField("items");
            itemField.setAccessible(true);
            items = (Map<String, TopLevelItem>) itemField.get(jenkins);
        }
    }

    @org.openjdk.jmh.annotations.State(Scope.Thread)
    public static class ThreadState {
        @Setup(Level.Iteration)
        public void setup() {
            SecurityContext securityContext = SecurityContextHolder.getContext();
            securityContext.setAuthentication(User.getById("user33", true).impersonate());
        }
    }

    @Benchmark
    public void benchmark(JenkinsState state, ThreadState threadState, Blackhole blackhole) {
        state.testProject.hasPermission(Permission.CREATE);
    }

    /**
     * Benchmark {@link RoleMap#newMatchingRoleMap(String)} since regex matching takes place there
     */
    @Benchmark
    public void benchmarkNewMatchingRoleMap(JenkinsState state, ThreadState threadState, Blackhole blackhole) {
        blackhole.consume(state.projectRoleMap.newMatchingRoleMap("TopFolder4/BottomFolder3/Project2"));
    }

    @Benchmark
    public void renderViewSimulation(JenkinsState state, ThreadState threadState, Blackhole blackhole) {
        List<TopLevelItem> viewableItems = new ArrayList<>();
        for (TopLevelItem item : state.items.values()) {
            if (item.hasPermission(Permission.WRITE))
                viewableItems.add(item);
        }
        blackhole.consume(viewableItems);
    }
}
