import {
  type KeyboardEvent,
  type MouseEvent,
  type ReactNode,
  useEffect,
  useRef,
  useState,
} from "react";

import { ChevronDownIcon } from "./icons/ChevronDownIcon.tsx";

interface CardProps {
  name: string;
  pattern?: string;
  badges?: ReactNode;
  summary?: ReactNode;
  actions?: ReactNode;
  readOnly?: boolean;
  body: ReactNode;
}

export function Card({
  name,
  pattern,
  badges,
  summary,
  actions,
  readOnly,
  body,
}: CardProps) {
  const [expanded, setExpanded] = useState(false);
  // Lazy body mount: keep the body out of the DOM until first expand, then
  // keep it mounted so expand/collapse transitions stay cheap. This keeps the
  // page light when there are many cards (each body holds many checkboxes).
  const [hasMountedBody, setHasMountedBody] = useState(false);
  const bodyRef = useRef<HTMLDivElement | null>(null);
  const [bodyHeight, setBodyHeight] = useState<number>(0);

  useEffect(() => {
    if (expanded && !hasMountedBody) {
      setHasMountedBody(true);
      return;
    }
    if (!bodyRef.current) return;
    if (expanded) {
      // Let the browser handle subsequent reflows (we set "auto" via the
      // `overflow: visible` branch). Re-measuring on every render would
      // restart the max-height transition and cause a strobing flicker.
      setBodyHeight(bodyRef.current.scrollHeight);
    } else {
      setBodyHeight(0);
    }
    // Intentionally exclude `body` from deps: a fresh JSX element on every
    // parent render would otherwise loop here and re-trigger the transition.
  }, [expanded, hasMountedBody]);

  // A card with no summary has nothing to reveal, so it is not expandable.
  const expandable = Boolean(summary);

  const toggle = () => setExpanded((v) => !v);
  const onKeyDown = (e: KeyboardEvent<HTMLDivElement>) => {
    if (e.key === "Enter" || e.key === " ") {
      e.preventDefault();
      toggle();
    }
  };

  const stop = (e: MouseEvent) => e.stopPropagation();

  return (
    <div
      className={`rsp-card${readOnly ? " rsp-card--read-only" : ""}`}
      aria-expanded={expandable ? expanded : undefined}
    >
      <div
        className="rsp-card__header"
        role={expandable ? "button" : undefined}
        tabIndex={expandable ? 0 : undefined}
        aria-expanded={expandable ? expanded : undefined}
        onClick={expandable ? toggle : undefined}
        onKeyDown={expandable ? onKeyDown : undefined}
      >
        <span className="rsp-card__name">{name}</span>
        {pattern !== undefined && (
          <span className="rsp-card__pattern">&quot;{pattern}&quot;</span>
        )}
        {badges}
        <span
          className={`rsp-card__summary${!summary ? " rsp-card__summary--empty" : ""}`}
        >
          {summary}
        </span>
        {actions && (
          <div className="rsp-card__actions" onClick={stop}>
            {actions}
          </div>
        )}
        {/* Always reserve the toggle's slot so action buttons stay aligned
            across expandable and non-expandable cards; only show the chevron
            when there is something to expand. */}
        <div className="rsp-card__toggle">
          {expandable && <ChevronDownIcon />}
        </div>
      </div>
      <div
        ref={bodyRef}
        className={`rsp-card__body${expanded ? "" : " rsp-card__body--collapsed"}`}
        style={{
          maxHeight: bodyHeight,
          overflow: expanded ? "visible" : "hidden",
        }}
        aria-hidden={!expanded}
      >
        {hasMountedBody && body}
      </div>
    </div>
  );
}
