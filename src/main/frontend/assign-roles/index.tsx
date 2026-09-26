import { StrictMode } from "react";
import { createRoot } from "react-dom/client";

import { createStrategyClient } from "../common/api/strategy.ts";
import type { AssignRolesBootstrap } from "../common/types/bootstrap.ts";
import { readBootstrap } from "../common/utils/bootstrap.ts";
import { AssignRolesPage } from "./AssignRolesPage.tsx";

const mountNode = document.getElementById("assign-roles-root");
if (mountNode) {
  const bootstrap = readBootstrap<AssignRolesBootstrap>(
    mountNode,
    "data-bootstrap",
  );
  const strategyUrl = mountNode.dataset.strategyUrl ?? "";
  const client = createStrategyClient(strategyUrl);
  createRoot(mountNode).render(
    <StrictMode>
      <AssignRolesPage
        bootstrap={bootstrap}
        client={client}
        checkSidNameUrl={mountNode.dataset.checkSidNameUrl ?? ""}
      />
    </StrictMode>,
  );
}
