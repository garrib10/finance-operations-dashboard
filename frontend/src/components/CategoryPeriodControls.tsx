import {
  availableMonths,
  availableYears,
  clampPeriod,
  isSamePeriod,
  monthName,
  type ReportingPeriod,
} from "../utils/reportingPeriod";

export const PERIOD_MONTH_ID = "category-period-month";
const PERIOD_YEAR_ID = "category-period-year";
const PERIOD_HINT_ID = "category-period-hint";

interface CategoryPeriodControlsProps {
  /** The month being shown (or loading). */
  selected: ReportingPeriod;
  /** The server's current month: the latest that can be chosen. */
  current: ReportingPeriod;
  onChange: (period: ReportingPeriod) => void;
  onReset: () => void;
  /** When set, the controls are disabled and this explains why. */
  disabledReason?: string;
}

/**
 * Month and year selects for the Categories page (not a form, so nothing is submitted).
 * Only January 2000 to the server's current month can be chosen; picking the current
 * year with a later month moves the month back to the current one.
 */
export function CategoryPeriodControls({
  selected,
  current,
  onChange,
  onReset,
  disabledReason,
}: CategoryPeriodControlsProps) {
  const disabled = Boolean(disabledReason);
  const describedBy = disabledReason ? PERIOD_HINT_ID : undefined;

  return (
    <div className="category-period" role="group" aria-label="Reporting month">
      <div className="form-field">
        <label htmlFor={PERIOD_MONTH_ID}>Month</label>
        <select
          id={PERIOD_MONTH_ID}
          value={selected.month}
          disabled={disabled}
          aria-describedby={describedBy}
          onChange={(event) => onChange({ month: Number(event.target.value), year: selected.year })}
        >
          {availableMonths(selected.year, current).map((month) => (
            <option key={month} value={month}>{monthName(month)}</option>
          ))}
        </select>
      </div>

      <div className="form-field">
        <label htmlFor={PERIOD_YEAR_ID}>Year</label>
        <select
          id={PERIOD_YEAR_ID}
          value={selected.year}
          disabled={disabled}
          aria-describedby={describedBy}
          onChange={(event) => onChange(clampPeriod({ month: selected.month, year: Number(event.target.value) }, current))}
        >
          {availableYears(current).map((year) => (
            <option key={year} value={year}>{year}</option>
          ))}
        </select>
      </div>

      {!isSamePeriod(selected, current) && (
        <button
          type="button"
          className="button button--secondary category-period__reset"
          disabled={disabled}
          aria-describedby={describedBy}
          onClick={onReset}
        >
          Back to current month
        </button>
      )}

      {disabledReason && (
        <p id={PERIOD_HINT_ID} className="field-hint category-period__hint">{disabledReason}</p>
      )}
    </div>
  );
}
