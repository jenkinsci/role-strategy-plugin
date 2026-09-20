import { StrictMode } from "react";
import { createRoot } from "react-dom/client";

import { createStrategyClient } from "../common/api/strategy.ts";
import type { ManageRolesBootstrap } from "../common/types/bootstrap.ts";
import { readBootstrap } from "../common/utils/bootstrap.ts";
import { ManageRolesPage } from "./ManageRolesPage.tsx";

const mountNode = document.getElementById("manage-roles-root");
if (mountNode) {
  const bootstrap = readBootstrap<ManageRolesBootstrap>(
    mountNode,
    "data-bootstrap",
  );
  const strategyUrl = mountNode.dataset.strategyUrl ?? "";
  const client = createStrategyClient(strategyUrl);
  createRoot(mountNode).render(
    <StrictMode>
      <ManageRolesPage
        bootstrap={bootstrap}
        client={client}
        rootUrl={mountNode.dataset.rootUrl ?? ""}
        checkPatternUrl={mountNode.dataset.checkPatternUrl ?? ""}
      />
    </StrictMode>,
  );
}
