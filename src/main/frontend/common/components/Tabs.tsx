import { type KeyboardEvent, useRef } from "react";

export interface TabDef {
  key: string;
  label: string;
  /** Hash fragment (e.g. "#item") the tab's anchor should point to. */
  hash: string;
}

interface TabsProps {
  tabs: TabDef[];
  activeKey: string;
  onSelect: (key: string) => void;
  /** id of the tabpanel element every tab controls. */
  panelId: string;
}

/**
 * ARIA tablist with roving focus and arrow-key navigation.
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
    <div className="app-build-bar__tabs">
      <div
        ref={listRef}
        className="app-build-tabs"
        role="tablist"
        onKeyDown={onKeyDown}
      >
        {tabs.map((tab) => {
          const active = tab.key === activeKey;
          return (
            <a
              key={tab.key}
              href={tab.hash}
              role="tab"
              id={`rsp-tab-${tab.key}`}
              data-tab-key={tab.key}
              aria-selected={active}
              aria-controls={panelId}
              tabIndex={active ? 0 : -1}
              className={`jenkins-button ${active ? "" : " jenkins-button--tertiary"}`}
              onClick={(e) => {
                e.preventDefault();
                onSelect(tab.key);
              }}
            >
              {tab.label}
            </a>
          );
        })}
      </div>
    </div>
  );
}
