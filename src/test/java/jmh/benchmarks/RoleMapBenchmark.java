package jmh.benchmarks;

import com.michelin.cio.hudson.plugins.rolestrategy.Role;
import com.michelin.cio.hudson.plugins.rolestrategy.RoleMap;
import com.synopsys.arc.jenkins.plugins.rolestrategy.RoleType;
import hudson.security.AccessControlled;
import hudson.security.Permission;
import jmh.JmhBenchmark;
import jmh.JmhBenchmarkState;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.infra.Blackhole;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

@JmhBenchmark
public class RoleMapBenchmark {

    public static class State extends JmhBenchmarkState {
        RoleMap roleMap = null;
        Method hasPermission = null;

        @Setup
        @Override
        public void setup() throws Exception {
            SortedMap<Role, Set<String>> map = new TreeMap<>();
            for (int i = 0; i < 50; i++) {
                Role role = new Role("role" + i, ".*",
                        new HashSet<>(Arrays.asList(
                                "hudson.model.Item.Discover",
                                "hudson.model.Item.Configure"
                        )), "");
                map.put(role, Collections.singleton("user" + i));
            }
            roleMap = new RoleMap(map);

            // RoleMap#hasPermission is private in RoleMap
            hasPermission = Class.forName("com.michelin.cio.hudson.plugins.rolestrategy.RoleMap")
                    .getDeclaredMethod("hasPermission", String.class, Permission.class,
                            RoleType.class, AccessControlled.class);
            hasPermission.setAccessible(true);
        }
    }

    @Benchmark
    public void benchmark(State state, Blackhole blackhole) throws Exception {
        // user3 does not have CREATE permission, so have to traverse through all of the Roles
        blackhole.consume(state.hasPermission.invoke(state.roleMap, "user3", Permission.CREATE, null, null));
    }
}
