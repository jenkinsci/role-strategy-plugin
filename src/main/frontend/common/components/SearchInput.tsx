import type { InputHTMLAttributes } from "react";

import { SearchIcon } from "./icons/SearchIcon.tsx";

interface SearchInputProps extends Omit<
  InputHTMLAttributes<HTMLInputElement>,
  "type"
> {
  className?: string;
}

/**
 * Matches the markup that Jenkins core's <l:search-bar> renders so the search
 * icon and padding land in the right place.
 */
export function SearchInput({ className, ...rest }: SearchInputProps) {
  return (
    <div className={`jenkins-search${className ? ` ${className}` : ""}`}>
      <div className="jenkins-search__icon">
        <SearchIcon />
      </div>
      <input
        {...rest}
        type="search"
        className="jenkins-input jenkins-search__input"
      />
    </div>
  );
}
