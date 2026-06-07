import { useEffect } from "react";

/**
 * Attach a click handler to a Jelly-rendered app-bar button identified by `id`.
 * The button lives outside the React mount node but in the same document.
 */
export function useAppBarButton(id: string, handler: () => void) {
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
}
