import Tippy, { type TippyProps } from "@tippyjs/react";

/**
 * Shared Tippy wrapper. Mirrors the convention used by pipeline-graph-view-plugin:
 * - `theme="tooltip"` and `animation="tooltip"` pick up Jenkins core's
 *   tippy theming so tooltips look like the rest of the UI.
 * - `duration={250}` matches core's transition timing.
 * - `touch={false}` disables long-press tooltips on touch devices.
 *
 * If no `content` is provided, the children are rendered without any Tippy
 * wrapping (so callers can pass an optional tooltip without conditionals).
 */
export function Tooltip(props: TippyProps) {
  if (
    props.content === undefined ||
    props.content === null ||
    props.content === ""
  ) {
    return props.children;
  }
  return (
    <Tippy
      theme="tooltip"
      animation="tooltip"
      duration={250}
      touch={false}
      delay={[200, 0]}
      // Render into the nearest <dialog> so tooltips work in the modal top layer.
      appendTo={(reference) => reference.closest("dialog") ?? document.body}
      {...props}
    >
      {props.children}
    </Tippy>
  );
}
