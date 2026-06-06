import type { ReactNode } from "react";

import { Tooltip } from "./Tooltip.tsx";

interface IconButtonProps {
  tooltip: string;
  icon: ReactNode;
  onClick: () => void;
  destructive?: boolean;
  disabled?: boolean;
}

/**
 * Icon-only Jenkins tertiary button with a tooltip.
 */
export function IconButton({
  tooltip,
  icon,
  onClick,
  destructive,
  disabled,
}: IconButtonProps) {
  return (
    <Tooltip content={tooltip} placement="top">
      <button
        type="button"
        className={`jenkins-button jenkins-button--tertiary rsp-card__action${
          destructive ? " jenkins-!-destructive-color" : ""
        }`}
        aria-label={tooltip}
        disabled={disabled}
        onClick={onClick}
      >
        {icon}
      </button>
    </Tooltip>
  );
}
