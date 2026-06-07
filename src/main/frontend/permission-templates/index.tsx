import { StrictMode } from "react";
import { createRoot } from "react-dom/client";

import { createStrategyClient } from "../common/api/strategy.ts";
import type { TemplatesBootstrap } from "../common/types/bootstrap.ts";
import { readBootstrap } from "../common/utils/bootstrap.ts";
import { PermissionTemplatesPage } from "./PermissionTemplatesPage.tsx";

const mountNode = document.getElementById("permission-templates-root");
if (mountNode) {
  const bootstrap = readBootstrap<TemplatesBootstrap>(
    mountNode,
    "data-bootstrap",
  );
  const strategyUrl = mountNode.dataset.strategyUrl ?? "";
  const client = createStrategyClient(strategyUrl);
  createRoot(mountNode).render(
    <StrictMode>
      <PermissionTemplatesPage bootstrap={bootstrap} client={client} />
    </StrictMode>,
  );
}
