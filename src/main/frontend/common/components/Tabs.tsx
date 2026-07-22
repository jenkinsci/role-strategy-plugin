import { type KeyboardEvent, useRef } from "react";

export interface TabDef {
  key: string;
  label: string;
}

interface TabsProps {
  tabs: TabDef[];
  activeKey: string;
  onSelect: (key: string) => void;
  /** id of the tabpanel element every tab controls. */
  panelId: string;
}

/**
 * ARIA tablist with roving focus and arrow-key navigation. Jenkins core has no
 * reusable tab markup outside jelly, so this brings its own `rsp-tabs` styling.
 */
export function Tabs({ tabs, activeKey, onSelect, panelId }: TabsProps) {
  const listRef = useRef<HTMLDivElement | null>(null);

  const onKeyDown = (e: KeyboardEvent<HTMLDivElement>) => {
    if (e.key !== "ArrowLeft" && e.key !== "ArrowRight") return;
    e.preventDefault();
    const index = tabs.findIndex((t) => t.key === activeKey);
    const delta = e.key === "ArrowRight" ? 1 : -1;
    const next = tabs[(index + delta + tabs.length) % tabs.length];
    onSelect(next.key);
    listRef.current
      ?.querySelector<HTMLElement>(`[data-tab-key="${next.key}"]`)
      ?.focus();
  };

  return (
    <div
      ref={listRef}
      className="rsp-tabs"
      role="tablist"
      onKeyDown={onKeyDown}
    >
      {tabs.map((tab) => {
        const active = tab.key === activeKey;
        return (
          <button
            key={tab.key}
            type="button"
            role="tab"
            id={`rsp-tab-${tab.key}`}
            data-tab-key={tab.key}
            aria-selected={active}
            aria-controls={panelId}
            tabIndex={active ? 0 : -1}
            className={`rsp-tabs__tab${active ? " rsp-tabs__tab--active" : ""}`}
            onClick={() => onSelect(tab.key)}
          >
            {tab.label}
          </button>
        );
      })}
    </div>
  );
}
