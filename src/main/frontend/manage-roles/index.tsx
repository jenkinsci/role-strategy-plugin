import { StrictMode } from "react";
import { createRoot } from "react-dom/client";

import { createStrategyClient } from "../common/api/strategy.ts";
import type {
  BootstrapPermissionGroups,
  ManageRolesBootstrap,
} from "../common/types/bootstrap.ts";
import { readBootstrap } from "../common/utils/bootstrap.ts";
import { ManageRolesPage } from "./ManageRolesPage.tsx";

const mountNode = document.getElementById("manage-roles-root");
if (mountNode) {
  const permissionGroups = readBootstrap<BootstrapPermissionGroups>(
    mountNode,
    "data-permission-groups",
  );
  const bootstrap = readBootstrap<ManageRolesBootstrap>(
    mountNode,
    "data-bootstrap",
  );
  const strategyUrl = mountNode.dataset.strategyUrl ?? "";
  const client = createStrategyClient(strategyUrl);
  const listMacrosUrl = mountNode.dataset.listMacrosUrl ?? "";
  createRoot(mountNode).render(
    <StrictMode>
      <ManageRolesPage
        permissionGroups={permissionGroups}
        bootstrap={bootstrap}
        client={client}
        listMacrosUrl={listMacrosUrl}
      />
    </StrictMode>,
  );
}
