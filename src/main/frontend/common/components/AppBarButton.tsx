import { useEffect } from "react";

/**
 * Attach a click handler to a Jelly-rendered app-bar button identified by `id`.
 * The button lives outside the React mount node but in the same document.
 * `options.visible` toggles the button, e.g. when only some tabs of a page are
 * editable; Jelly still gates whether the button is rendered at all.
 */
export function useAppBarButton(
  id: string,
  handler: () => void,
  options?: { visible?: boolean },
) {
  const visible = options?.visible ?? true;

  useEffect(() => {
    const node = document.getElementById(id);
    if (!node) return;
    const onClick = (e: Event) => {
      e.preventDefault();
      handler();
    };
    node.addEventListener("click", onClick);
    return () => node.removeEventListener("click", onClick);
  }, [id, handler]);

  useEffect(() => {
    const node = document.getElementById(id);
    if (!node) return;
    node.hidden = !visible;
    return () => {
      node.hidden = false;
    };
  }, [id, visible]);
}
