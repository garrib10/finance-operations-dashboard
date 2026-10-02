import { useState } from "react";
import { Link, Outlet } from "react-router-dom";
import { PanelLeftClose, PanelLeftOpen } from "lucide-react";
import { readSidebarCollapsed, saveSidebarCollapsed } from "../utils/sidebarPreference";
import { AccountMenu } from "./AccountMenu";
import { PrimaryNav } from "./PrimaryNav";

const SIDEBAR_ID = "app-sidebar";

/**
 * The signed-in application shell: sidebar navigation, a compact top bar with the
 * account menu, and the main landmark. It renders only inside ProtectedRoute, so it
 * never appears before the session is restored. Each page's root section must stay a
 * direct child of <main>; the page CSS relies on that.
 *
 * Collapsing only changes a data attribute, so the routed page is never remounted.
 * The collapsed styles apply above 1100px only; narrower screens ignore the preference.
 */
export function AppLayout() {
  // Read synchronously so the first render already has the saved width (no flash).
  const [isSidebarCollapsed, setIsSidebarCollapsed] = useState(readSidebarCollapsed);
  const toggleLabel = isSidebarCollapsed ? "Expand sidebar" : "Collapse sidebar";
  const ToggleIcon = isSidebarCollapsed ? PanelLeftOpen : PanelLeftClose;

  function toggleSidebar(): void {
    const collapsed = !isSidebarCollapsed;
    setIsSidebarCollapsed(collapsed);
    saveSidebarCollapsed(collapsed);
  }

  return (
    <div className="app-layout" data-sidebar-collapsed={isSidebarCollapsed}>
      <div id={SIDEBAR_ID} className="app-sidebar">
        <div className="app-sidebar__header">
          <Link className="app-brand app-sidebar__brand" to="/" aria-label="FinTrack">
            <span className="app-sidebar__brand-name">FinTrack</span>
            <span className="app-sidebar__brand-mark" aria-hidden="true">F</span>
          </Link>

          {/* Icon-only beside the brand; the hidden label is its name, the tooltip its visual label. */}
          <button
            type="button"
            className="app-sidebar__toggle"
            aria-controls={SIDEBAR_ID}
            aria-expanded={!isSidebarCollapsed}
            onClick={toggleSidebar}
          >
            <ToggleIcon className="app-sidebar__toggle-icon" aria-hidden="true" size={20} />
            <span className="visually-hidden">{toggleLabel}</span>
            <span className="sidebar-tooltip" aria-hidden="true">{toggleLabel}</span>
          </button>
        </div>

        <PrimaryNav className="app-sidebar__nav" />
      </div>

      <div className="app-layout__body">
        <header className="app-topbar">
          <div className="app-topbar__content">
            <AccountMenu />
          </div>
        </header>

        <main id="main-content" className="app-main" tabIndex={-1}>
          <Outlet />
        </main>
      </div>
    </div>
  );
}
