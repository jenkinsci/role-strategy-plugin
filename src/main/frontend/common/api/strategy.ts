import { postForm } from "./client.ts";

/**
 * Client for the role-strategy REST endpoints exposed on
 * RoleBasedAuthorizationStrategy (`<rootURL>/role-strategy/strategy`).
 * Only the endpoints used by the React pages are wrapped here; future
 * pages add methods as they need them.
 */
export interface StrategyClient {
  /**
   * Create or update a template. Pass {@code overwrite: false} when creating so
   * the backend rejects a name that already exists; pass {@code true} when
   * editing an existing template.
   */
  addTemplate(
    name: string,
    permissionIds: string[],
    overwrite: boolean,
  ): Promise<void>;
  removeTemplates(templateNames: string[]): Promise<void>;
}

export function createStrategyClient(baseUrl: string): StrategyClient {
  const url = (endpoint: string) => `${baseUrl}/${endpoint}`;

  return {
    addTemplate: (name, permissionIds, overwrite) =>
      postForm(url("addTemplate"), {
        name,
        permissionIds: permissionIds.join(","),
        overwrite: String(overwrite),
      }),
    removeTemplates: (templateNames) =>
      postForm(url("removeTemplates"), {
        names: templateNames.join(","),
      }),
  };
}
