import "./App.css";

import { lazy } from "react";
import { Navigate, Route, Routes } from "react-router-dom";

import { AppLayout } from "./components/AppLayout";
import { PublicLayout } from "./components/PublicLayout";
import { CategoryProvider } from "./context/CategoryProvider";
import ProtectedRoute from "./components/ProtectedRoute";
import LoginPage from "./pages/LoginPage";
import RegisterPage from "./pages/RegisterPage";

/*
 * Signed-in pages load on demand (issue #124), each in its own file; the charts library goes
 * with the Budgets page, its only user. Login and Register stay in the main bundle because
 * they are the first screen for signed-out visitors. AppLayout shows the loading state.
 */
const DashboardPage = lazy(() => import("./pages/DashboardPage"));
const TransactionPage = lazy(() => import("./pages/TransactionPage"));
const BudgetPage = lazy(() => import("./pages/BudgetPage"));
const CategoriesPage = lazy(() => import("./pages/CategoriesPage"));
const ProfilePage = lazy(() => import("./pages/ProfilePage"));
const AccountSettingsPage = lazy(() => import("./pages/AccountSettingsPage"));

function App() {
  return (
    <div className="app-shell">
      <a className="skip-link" href="#main-content">
        Skip to main content
      </a>

      <CategoryProvider>
        <Routes>
          <Route element={<PublicLayout />}>
            <Route path="/login" element={<LoginPage />} />
            <Route path="/register" element={<RegisterPage />} />
          </Route>

          <Route element={<ProtectedRoute />}>
            <Route element={<AppLayout />}>
              <Route path="/profile" element={<ProfilePage />} />
              <Route path="/settings" element={<AccountSettingsPage />} />
              <Route path="/" element={<DashboardPage />} />
              <Route path="/transactions" element={<TransactionPage />} />
              <Route path="/budgets" element={<BudgetPage />} />
              <Route path="/categories" element={<CategoriesPage />} />
            </Route>
          </Route>

          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </CategoryProvider>
    </div>
  );
}

export default App;
