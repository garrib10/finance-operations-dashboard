import { NavLink } from "react-router-dom";
import { PRIMARY_NAV } from "../navigation";

interface PrimaryNavProps {
  className?: string;
}

/** Primary destinations; NavLink marks the current page with aria-current="page". */
export function PrimaryNav({ className }: PrimaryNavProps) {
  return (
    <nav className={["primary-nav", className].filter(Boolean).join(" ")} aria-label="Primary navigation">
      <ul className="primary-nav__list">
        {PRIMARY_NAV.map(({ label, path, icon: Icon, end }) => (
          <li key={path}>
            <NavLink className="primary-nav__link" to={path} end={end}>
              <Icon className="primary-nav__icon" aria-hidden="true" size={20} />
              <span className="primary-nav__label">{label}</span>
            </NavLink>
          </li>
        ))}
      </ul>
    </nav>
  );
}
