/*
 * The MIT License
 *
 * Copyright 2022 Markus Winter
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

package com.synopsys.arc.jenkins.plugins.rolestrategy.macros;

import com.cloudbees.hudson.plugins.folder.AbstractFolder;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.synopsys.arc.jenkins.plugins.rolestrategy.Macro;
import com.synopsys.arc.jenkins.plugins.rolestrategy.RoleMacroExtension;
import com.synopsys.arc.jenkins.plugins.rolestrategy.RoleType;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.model.Item;
import hudson.model.ListView;
import hudson.model.View;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.security.AccessControlled;
import hudson.security.Permission;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.rolestrategy.Settings;

/**
 * Checks if the item is contained in one of the views.
 *
 */
@Extension(optional = true)
public class ContainedInViewMacro extends RoleMacroExtension {

  /*
   * Map of macro to view.
   *
   * Getting the items in a view is expensive so we need to cache that information
   */
  private final Cache<Macro, Map<View, Set<String>>> cache = Caffeine.newBuilder().maximumSize(Settings.VIEW_CACHE_MAX_SIZE)
      .expireAfterWrite(Settings.VIEW_CACHE_EXPIRATION_TIME_SEC, TimeUnit.SECONDS).weakKeys().build();

  /*
   * Map of view to items contained in the view.
   *
   * Getting the items in a view is expensive so we need to cache that information
   */
  private final Cache<View, Set<String>> viewCache = Caffeine.newBuilder().maximumSize(Settings.VIEW_CACHE_MAX_SIZE)
      .expireAfterWrite(Settings.VIEW_CACHE_EXPIRATION_TIME_SEC, TimeUnit.SECONDS).weakKeys().build();

  @Override
  public String getName() {
    return "ContainedInView";
  }

  // TODO: fix naming conventions
  @SuppressFBWarnings(value = "NM_METHOD_NAMING_CONVENTION", justification = "Old code, should be fixed later")
  @Override
  public boolean IsApplicable(RoleType roleType) {
    return roleType == RoleType.Project;
  }

  @Override
  @SuppressFBWarnings(value = "NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE", justification = "we know that the cache has no null entries")
  public boolean hasPermission(String sid, Permission p, RoleType type, AccessControlled accessControlledItem, Macro macro) {
    if (accessControlledItem instanceof Item) {
      Item item = (Item) accessControlledItem;
      Map<View, Set<String>> items = cache.get(macro, this::getItemsForMacro);
      for (Entry<View, Set<String>> entry : items.entrySet()) {
        if (entry.getValue().contains(item.getFullName())) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public String getDescription() {
    return "Access items that are added to a ListView. Specify the views as parameter to the macro, e.g. "
        + "<code>@ContainedInView(view1, view2)</code>. "
        + "Prepend the folder name if the view is in a folder, e.g. <code>@ContainedInView(folder/view1)</code> "
        + "(To access views inside a folder, access to the folder itself is required).<br/>"
        + "When enabling the <dfn>Recurse in subfolders</dfn> option, make sure to also check the folders themselves for which you "
        + "add items.<br/>"
        + "NestedView plugin is not supported currently as this allows to create ambiguous names for views.";
  }

  /**
   * Returns a list of all items of all views this macro covers.
   *
   * @param macro The macro for which to get the items
   * @return Set of all items;
   */
  private Map<View, Set<String>> getItemsForMacro(Macro macro) {
    Map<View, Set<String>> viewList = new HashMap<>();
    for (String viewName : macro.getParameters()) {
      View view = getViewFromFullName(viewName);
      if (view != null) {
        viewList.put(view, viewCache.get(view, this::getItemsForView));
      }
    }

    return viewList;
  }

  /**
   * Returns the set of fullNames of all items contained in the view.
   *
   * @param view The View to get the items from
   * @return Set of item fullNames
   */
  private Set<String> getItemsForView(View view) {
    Set<String> items = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

    try (ACLContext c = ACL.as2(ACL.SYSTEM2)) {
      items.addAll(view.getItems().stream().map(Item::getFullName).collect(Collectors.toSet()));
    }
    return items;
  }

  /**
   * Gets the view for the given view name.
   * Currently only ListViews are supported that are directly under Jenkins or under a folder.
   *
   * @param viewName Full name of the view
   * @return the View matching the name or null of not found.
   */
  @CheckForNull
  private View getViewFromFullName(String viewName) {
    Jenkins jenkins = Jenkins.get();
    try (ACLContext c = ACL.as2(ACL.SYSTEM2)) {
      int index = viewName.lastIndexOf("/");
      if (index > 0) {
        String folderFullName = viewName.substring(0, index);
        String viewBaseName = viewName.substring(index + 1);
        AbstractFolder<?> folder = jenkins.getItemByFullName(folderFullName, AbstractFolder.class);
        if (folder != null) {
          for (View view : folder.getViews()) {
            if (view instanceof ListView && view.getViewName().equals(viewBaseName) && view.getOwner() == folder) {
              return view;
            }
          }
        }
      } else {
        View view = jenkins.getView(viewName);
        if (view instanceof ListView && view.getOwner() == Jenkins.get()) {
          return view;
        }
      }
    }
    return null;
  }
}
