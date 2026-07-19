import { Fragment, useEffect, useRef, useState } from "react";

import type { PermissionGroup } from "../types/permission.ts";
import { FilterIcon } from "./icons/FilterIcon.tsx";
import { SearchInput } from "./SearchInput.tsx";

interface SearchWithFilterProps {
  searchPlaceholder: string;
  search: string;
  onSearchChange: (next: string) => void;
  filterGroups?: PermissionGroup[];
  filterLabel?: string;
  selectedFilterIds?: ReadonlySet<string>;
  onFilterToggle?: (permissionId: string) => void;
  onFilterReset?: () => void;
}

export function SearchWithFilter({
  searchPlaceholder,
  search,
  onSearchChange,
  filterGroups,
  filterLabel = "Filter by permission",
  selectedFilterIds = new Set(),
  onFilterToggle,
  onFilterReset,
}: SearchWithFilterProps) {
  const [filterOpen, setFilterOpen] = useState(false);
  const [filterQuery, setFilterQuery] = useState("");
  const containerRef = useRef<HTMLDivElement | null>(null);
  const filterButtonRef = useRef<HTMLButtonElement | null>(null);

  useEffect(() => {
    if (!filterOpen) return;
    const onMouseDown = (e: MouseEvent) => {
      if (
        containerRef.current &&
        !containerRef.current.contains(e.target as Node)
      ) {
        setFilterOpen(false);
      }
    };
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        setFilterOpen(false);
        filterButtonRef.current?.focus();
      }
    };
    document.addEventListener("mousedown", onMouseDown);
    document.addEventListener("keydown", onKeyDown);
    return () => {
      document.removeEventListener("mousedown", onMouseDown);
      document.removeEventListener("keydown", onKeyDown);
    };
  }, [filterOpen]);

  const hasActiveFilter = selectedFilterIds.size > 0;
  const filterQ = filterQuery.trim().toLowerCase();

  return (
    <div ref={containerRef} className="rsp-search-wrapper">
      <SearchInput
        placeholder={searchPlaceholder}
        value={search}
        onChange={(e) => onSearchChange(e.target.value)}
      />
      {filterGroups && filterGroups.length > 0 && (
        <div className="rsp-filter">
          <button
            type="button"
            ref={filterButtonRef}
            className={`jenkins-button jenkins-button--tertiary rsp-filter__button${
              hasActiveFilter ? " rsp-filter__button--active" : ""
            }`}
            aria-expanded={filterOpen}
            aria-haspopup="true"
            onClick={() => setFilterOpen((v) => !v)}
            title={filterLabel}
          >
            <FilterIcon />
            {hasActiveFilter && (
              <span className="rsp-filter__count">
                {selectedFilterIds.size}
              </span>
            )}
          </button>
          {filterOpen && (
            <div className="rsp-filter__dropdown">
              <div className="rsp-filter__header">
                <span className="rsp-filter__header-label">{filterLabel}</span>
                {hasActiveFilter && (
                  <button
                    type="button"
                    className="rsp-filter__reset-button"
                    onClick={() => onFilterReset?.()}
                  >
                    Reset
                  </button>
                )}
              </div>
              <div className="rsp-filter__search">
                <SearchInput
                  className="rsp-filter__search-bar"
                  placeholder="Search permissions"
                  value={filterQuery}
                  onChange={(e) => setFilterQuery(e.target.value)}
                />
              </div>
              <div className="rsp-filter__list">
                {filterGroups.map((group) => {
                  const visiblePerms = group.permissions.filter(
                    (p) =>
                      !filterQ ||
                      p.name.toLowerCase().includes(filterQ) ||
                      group.title.toLowerCase().includes(filterQ),
                  );
                  if (visiblePerms.length === 0) return null;
                  return (
                    <Fragment key={group.title}>
                      <div className="rsp-filter__group-title">
                        {group.title}
                      </div>
                      {visiblePerms.map((p) => {
                        const active = selectedFilterIds.has(p.id);
                        return (
                          <button
                            type="button"
                            key={p.id}
                            className={`jenkins-dropdown__item${
                              active ? " rsp-filter__item--active" : ""
                            }`}
                            onClick={() => onFilterToggle?.(p.id)}
                          >
                            <span className="rsp-filter__item-indicator" />
                            <span className="rsp-filter__item-name">
                              {p.name}
                            </span>
                          </button>
                        );
                      })}
                    </Fragment>
                  );
                })}
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
