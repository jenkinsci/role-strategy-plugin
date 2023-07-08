package jmh.benchmarks;

import com.michelin.cio.hudson.plugins.rolestrategy.AuthorizationType;
import com.michelin.cio.hudson.plugins.rolestrategy.PermissionEntry;
import com.michelin.cio.hudson.plugins.rolestrategy.Role;
import com.michelin.cio.hudson.plugins.rolestrategy.RoleMap;
import com.synopsys.arc.jenkins.plugins.rolestrategy.RoleType;
import hudson.security.AccessControlled;
import hudson.security.Permission;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import jenkins.benchmark.jmh.JmhBenchmark;
import jenkins.benchmark.jmh.JmhBenchmarkState;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.infra.Blackhole;

@JmhBenchmark
public class RoleMapBenchmark {

  public static class State50 extends RoleMapBenchmarkState {
    @Override
    int getRoleCount() {
      return 50;
    }
  }

  public static class State100 extends RoleMapBenchmarkState {
    @Override
    int getRoleCount() {
      return 100;
    }
  }

  public static class State200 extends RoleMapBenchmarkState {
    @Override
    int getRoleCount() {
      return 200;
    }
  }

  public static class State500 extends RoleMapBenchmarkState {
    @Override
    int getRoleCount() {
      return 500;
    }
  }

  // user3 does not have CREATE permission, so have to traverse through all of the Roles

  @Benchmark
  public void benchmark50(State50 state, Blackhole blackhole) throws Exception {
    blackhole.consume(state.hasPermission.invoke(state.roleMap, state.functionArgs));
  }

  @Benchmark
  public void benchmark100(State100 state, Blackhole blackhole) throws Exception {
    blackhole.consume(state.hasPermission.invoke(state.roleMap, state.functionArgs));
  }

  @Benchmark
  public void benchmark200(State200 state, Blackhole blackhole) throws Exception {
    blackhole.consume(state.hasPermission.invoke(state.roleMap, state.functionArgs));
  }

  @Benchmark
  public void benchmark500(State500 state, Blackhole blackhole) throws Exception {
    blackhole.consume(state.hasPermission.invoke(state.roleMap, state.functionArgs));
  }
}

@SuppressWarnings("checkstyle:OneTopLevelClass")
abstract class RoleMapBenchmarkState extends JmhBenchmarkState {
  RoleMap roleMap = null;
  Method hasPermission = null;
  Object[] functionArgs = null;

  @Override
  public void setup() throws Exception {
    SortedMap<Role, Set<PermissionEntry>> map = new TreeMap<>();
    final int roleCount = getRoleCount();
    for (int i = 0; i < roleCount; i++) {
      Role role = new Role("role" + i, ".*", new HashSet<>(Arrays.asList("hudson.model.Item.Discover", "hudson.model.Item.Configure")), "");
      map.put(role, Collections.singleton(new PermissionEntry(AuthorizationType.USER, "user" + i)));
    }
    roleMap = new RoleMap(map);

    // RoleMap#hasPermission is private in RoleMap
    hasPermission = Class.forName("com.michelin.cio.hudson.plugins.rolestrategy.RoleMap").getDeclaredMethod("hasPermission",
        PermissionEntry.class, Permission.class, RoleType.class, AccessControlled.class);
    hasPermission.setAccessible(true);


    functionArgs = new Object[] { new PermissionEntry(AuthorizationType.USER, "user3"), Permission.CREATE, null, null };
  }

  abstract int getRoleCount();
}
