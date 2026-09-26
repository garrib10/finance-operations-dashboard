import "./App.css";

import { Navigate, Route, Routes } from "react-router-dom";

import ProfilePage from "./pages/ProfilePage";
import AccountSettingsPage from "./pages/AccountSettingsPage";

import AppHeader from "./components/AppHeader";
import ProtectedRoute from "./components/ProtectedRoute";
import BudgetPage from "./pages/BudgetPage";
import DashboardPage from "./pages/DashboardPage";
import LoginPage from "./pages/LoginPage";
import RegisterPage from "./pages/RegisterPage";
import TransactionPage from "./pages/TransactionPage";

function App() {
  return (
    <div className="app-shell">
      <a className="skip-link" href="#main-content">
        Skip to main content
      </a>

      <AppHeader />

      <main id="main-content" className="app-main" tabIndex={-1}>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />

          <Route element={<ProtectedRoute />}>
            <Route path="/profile" element={<ProfilePage />} />
            <Route path="/settings" element={<AccountSettingsPage />} />
            <Route path="/" element={<DashboardPage />} />
            <Route path="/transactions" element={<TransactionPage />} />
            <Route path="/budgets" element={<BudgetPage />} />
          </Route>

          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </main>
    </div>
  );
}

export default App;
