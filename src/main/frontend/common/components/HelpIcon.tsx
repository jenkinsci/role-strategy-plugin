import type { ReactNode } from "react";

import { HelpCircleIcon } from "./icons/HelpCircleIcon.tsx";
import { Tooltip } from "./Tooltip.tsx";

interface HelpIconProps {
  description: ReactNode;
}

/**
 * Help-circle icon with a tooltip showing the description.
 */
export function HelpIcon({ description }: HelpIconProps) {
  return (
    <Tooltip content={description} placement="top" maxWidth={360}>
      <span
        className="rsp-help-icon"
        tabIndex={0}
        aria-label={typeof description === "string" ? description : undefined}
        role="img"
      >
        <HelpCircleIcon />
      </span>
    </Tooltip>
  );
}
