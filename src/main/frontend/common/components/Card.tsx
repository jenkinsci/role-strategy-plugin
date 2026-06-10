import {
  type KeyboardEvent,
  type MouseEvent,
  type ReactNode,
  type TransitionEvent,
  useEffect,
  useId,
  useRef,
  useState,
} from "react";

import { ChevronDownIcon } from "./icons/ChevronDownIcon.tsx";

interface CardProps {
  name: string;
  badges?: ReactNode;
  summary?: ReactNode;
  /** Shown in the summary slot when `summary` is empty. */
  summaryPlaceholder?: string;
  actions?: ReactNode;
  readOnly?: boolean;
  body: ReactNode;
}

export function Card({
  name,
  badges,
  summary,
  summaryPlaceholder,
  actions,
  readOnly,
  body,
}: CardProps) {
  const bodyId = useId();
  const [expanded, setExpanded] = useState(false);
  // Lazy body mount: keep the body out of the DOM until first expand, then
  // keep it mounted so expand/collapse transitions stay cheap. This keeps the
  // page light when there are many cards (each body holds many checkboxes).
  const [hasMountedBody, setHasMountedBody] = useState(false);
  const bodyRef = useRef<HTMLDivElement | null>(null);
  const [bodyHeight, setBodyHeight] = useState<number>(0);
  // Once the expand transition finishes, the measured max-height is released
  // (settled -> "none") so later reflows, e.g. a window resize that makes the
  // body taller, are not clipped by a stale measurement.
  const [settled, setSettled] = useState(false);

  useEffect(() => {
    if (expanded && !hasMountedBody) {
      setHasMountedBody(true);
      return;
    }
    const node = bodyRef.current;
    if (!node) return;
    if (expanded) {
      setBodyHeight(node.scrollHeight);
    } else {
      // A transition from max-height "none" cannot animate, so pin the
      // current pixel height and drop to 0 on the next frame.
      setSettled(false);
      setBodyHeight(node.scrollHeight);
      const raf = requestAnimationFrame(() => setBodyHeight(0));
      return () => cancelAnimationFrame(raf);
    }
    // Intentionally exclude `body` from deps: a fresh JSX element on every
    // parent render would otherwise loop here and re-trigger the transition.
  }, [expanded, hasMountedBody]);

  const onBodyTransitionEnd = (e: TransitionEvent<HTMLDivElement>) => {
    if (
      e.target === bodyRef.current &&
      e.propertyName === "max-height" &&
      expanded
    ) {
      setSettled(true);
    }
  };

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
      data-expanded={expandable ? expanded : undefined}
    >
      <div
        className="rsp-card__header"
        role={expandable ? "button" : undefined}
        tabIndex={expandable ? 0 : undefined}
        aria-expanded={expandable ? expanded : undefined}
        aria-controls={expandable ? bodyId : undefined}
        onClick={expandable ? toggle : undefined}
        onKeyDown={expandable ? onKeyDown : undefined}
      >
        <span className="rsp-card__name">{name}</span>
        {badges}
        <span
          className={`rsp-card__summary${!summary ? " rsp-card__summary--empty" : ""}`}
        >
          {summary ?? summaryPlaceholder}
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
        id={bodyId}
        className={`rsp-card__body${expanded ? "" : " rsp-card__body--collapsed"}`}
        style={{
          maxHeight: settled ? "none" : bodyHeight,
          overflow: settled ? "visible" : "hidden",
        }}
        inert={!expanded}
        onTransitionEnd={onBodyTransitionEnd}
      >
        {hasMountedBody && body}
      </div>
    </div>
  );
}
