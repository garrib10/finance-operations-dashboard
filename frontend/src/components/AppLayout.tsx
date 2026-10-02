import { Link, Outlet } from "react-router-dom";
import { AccountMenu } from "./AccountMenu";
import { PrimaryNav } from "./PrimaryNav";

/**
 * The signed-in application shell: sidebar navigation, a compact top bar with the
 * account menu, and the main landmark. It renders only inside ProtectedRoute, so it
 * never appears before the session is restored. Each page's root section must stay a
 * direct child of <main>; the page CSS relies on that.
 */
export function AppLayout() {
  return (
    <div className="app-layout">
      <div className="app-sidebar">
        <Link className="app-brand app-sidebar__brand" to="/">
          FinTrack
        </Link>

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
