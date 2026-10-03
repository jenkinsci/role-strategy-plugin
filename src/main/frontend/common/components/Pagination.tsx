interface PaginationProps {
  /** Zero-based current page. */
  page: number;
  pageCount: number;
  totalItems: number;
  pageSize: number;
  onPageChange: (page: number) => void;
}

/**
 * Pager for long card lists. Renders nothing when everything fits on one page.
 */
export function Pagination({
  page,
  pageCount,
  totalItems,
  pageSize,
  onPageChange,
}: PaginationProps) {
  if (pageCount <= 1) return null;
  const start = page * pageSize + 1;
  const end = Math.min((page + 1) * pageSize, totalItems);
  const pages = Array.from({ length: pageCount }, (_, i) => i);

  return (
    <div className="rsp-pagination">
      <span className="rsp-pagination__status">
        Showing {start}-{end} of {totalItems}
      </span>
      <div className="rsp-pagination__controls">
        <button
          type="button"
          className="jenkins-button"
          disabled={page === 0}
          onClick={() => onPageChange(page - 1)}
        >
          Previous
        </button>
        <div className="jenkins-select rsp-pagination__select">
          <select
            className="jenkins-select__input"
            aria-label="Page"
            value={page}
            onChange={(e) => onPageChange(Number(e.target.value))}
          >
            {pages.map((p) => (
              <option key={p} value={p}>
                Page {p + 1} of {pageCount}
              </option>
            ))}
          </select>
        </div>
        <button
          type="button"
          className="jenkins-button"
          disabled={page >= pageCount - 1}
          onClick={() => onPageChange(page + 1)}
        >
          Next
        </button>
      </div>
    </div>
  );
}
