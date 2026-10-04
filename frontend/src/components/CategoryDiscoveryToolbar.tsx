import { Search } from "lucide-react";
import {
  FILTER_OPTIONS,
  SORT_OPTIONS,
  isDefaultDiscovery,
  type CategoryDiscovery,
  type CategoryFilter,
  type CategorySort,
} from "../utils/categoryDiscovery";
import { IconField } from "./IconField";

interface CategoryDiscoveryToolbarProps {
  discovery: CategoryDiscovery;
  onChange: (discovery: CategoryDiscovery) => void;
  onClear: () => void;
  shown: number;
  total: number;
  /** Explains a card kept visible although it does not match (one being edited, say). */
  keptVisibleNote?: string;
}

/**
 * Search, filter, and sort for the category cards only; the summary and the spending
 * table always cover every category. The result count is a polite status region, so
 * screen readers hear the new count without every keystroke being announced twice.
 */
export function CategoryDiscoveryToolbar({
  discovery,
  onChange,
  onClear,
  shown,
  total,
  keptVisibleNote,
}: CategoryDiscoveryToolbarProps) {
  return (
    <div className="category-toolbar">
      <div className="category-toolbar__controls" role="search" aria-label="Find categories">
        <div className="form-field category-toolbar__search">
          <label htmlFor="category-search">Search categories</label>
          <IconField icon={Search} showIcon={discovery.query !== ""}>
            <input
              id="category-search"
              type="search"
              value={discovery.query}
              autoComplete="off"
              onChange={(event) => onChange({ ...discovery, query: event.target.value })}
            />
          </IconField>
        </div>

        <div className="form-field">
          <label htmlFor="category-filter">Filter categories</label>
          <select
            id="category-filter"
            value={discovery.filter}
            onChange={(event) => onChange({ ...discovery, filter: event.target.value as CategoryFilter })}
          >
            {FILTER_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>{option.label}</option>
            ))}
          </select>
        </div>

        <div className="form-field">
          <label htmlFor="category-sort">Sort categories</label>
          <select
            id="category-sort"
            value={discovery.sort}
            onChange={(event) => onChange({ ...discovery, sort: event.target.value as CategorySort })}
          >
            {SORT_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>{option.label}</option>
            ))}
          </select>
        </div>

        {!isDefaultDiscovery(discovery) && (
          <button type="button" className="button button--secondary category-toolbar__clear" onClick={onClear}>
            Clear category filters
          </button>
        )}
      </div>

      <p className="category-toolbar__count" role="status">
        Showing {shown} of {total} {total === 1 ? "category" : "categories"}
      </p>
      {keptVisibleNote && <p className="field-hint category-toolbar__note">{keptVisibleNote}</p>}
    </div>
  );
}
