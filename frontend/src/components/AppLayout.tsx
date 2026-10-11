import { useEffect, useRef, useState } from "react";
import { Link, Outlet, useLocation } from "react-router-dom";
import { Menu, PanelLeftClose, PanelLeftOpen } from "lucide-react";
import { DESKTOP_NAV_QUERY } from "../navigation";
import { readSidebarCollapsed, saveSidebarCollapsed } from "../utils/sidebarPreference";
import { AccountMenu } from "./AccountMenu";
import { MOBILE_NAV_ID, MobileNavDrawer } from "./MobileNavDrawer";
import { PrimaryNav } from "./PrimaryNav";

const SIDEBAR_ID = "app-sidebar";

/**
 * The signed-in application shell: sidebar navigation, a compact top bar with the
 * account menu, and the main landmark. It renders only inside ProtectedRoute, so it
 * never appears before the session is restored. Each page's root section must stay a
 * direct child of <main>; the page CSS relies on that.
 *
 * Collapsing only changes a data attribute, so the routed page is never remounted.
 * The collapsed styles apply above 1100px only; narrower screens ignore the preference
 * and use the menu button and drawer instead.
 */
export function AppLayout() {
  const location = useLocation();
  // Read synchronously so the first render already has the saved width (no flash).
  const [isSidebarCollapsed, setIsSidebarCollapsed] = useState(readSidebarCollapsed);
  // The drawer is open only for the location it was opened on, so any navigation
  // (a link, browser back or forward) closes it without an extra effect.
  const [drawerOpenedAt, setDrawerOpenedAt] = useState<string | null>(null);
  const isDrawerOpen = drawerOpenedAt === location.key;
  const menuButtonRef = useRef<HTMLButtonElement>(null);
  const toggleLabel = isSidebarCollapsed ? "Expand sidebar" : "Collapse sidebar";
  const ToggleIcon = isSidebarCollapsed ? PanelLeftOpen : PanelLeftClose;

  function toggleSidebar(): void {
    const collapsed = !isSidebarCollapsed;
    setIsSidebarCollapsed(collapsed);
    saveSidebarCollapsed(collapsed);
  }

  function closeDrawer(): void {
    setDrawerOpenedAt(null);
  }

  // Widening to the desktop layout closes the drawer, so two navigations never coexist.
  useEffect(() => {
    if (!isDrawerOpen || typeof window.matchMedia !== "function") return;

    const desktop = window.matchMedia(DESKTOP_NAV_QUERY);
    function closeOnDesktop(): void {
      if (desktop.matches) setDrawerOpenedAt(null);
    }

    closeOnDesktop();
    desktop.addEventListener("change", closeOnDesktop);
    return () => desktop.removeEventListener("change", closeOnDesktop);
  }, [isDrawerOpen]);

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
            <button
              ref={menuButtonRef}
              type="button"
              className="app-topbar__menu"
              aria-label="Open navigation menu"
              aria-haspopup="dialog"
              aria-expanded={isDrawerOpen}
              aria-controls={MOBILE_NAV_ID}
              onClick={() => setDrawerOpenedAt(location.key)}
            >
              <Menu aria-hidden="true" size={22} />
            </button>

            <Link className="app-brand app-topbar__brand" to="/">
              FinTrack
            </Link>

            <AccountMenu />
          </div>
        </header>

        <main id="main-content" className="app-main" tabIndex={-1}>
          <Outlet />
        </main>
      </div>

      <MobileNavDrawer open={isDrawerOpen} onClose={closeDrawer} returnFocusRef={menuButtonRef} />
    </div>
  );
}
