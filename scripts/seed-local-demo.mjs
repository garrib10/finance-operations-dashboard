#!/usr/bin/env node
/*
 * Creates a fictional demo account with sample data in a LOCAL FinTrack (issue #125), for
 * manual testing, screenshots, and Selenium development. It refuses any API that is not on
 * this machine. Credentials come from environment variables only and are never printed.
 *
 *   FINTRACK_SEED_EMAIL=demo-seed@example.com FINTRACK_SEED_PASSWORD='<15+ characters>' \
 *     node scripts/seed-local-demo.mjs
 *
 * Optional: FINTRACK_API_URL (default http://localhost:8080) and FINTRACK_FRONTEND_ORIGIN
 * (default http://localhost:5173, which must match the backend's FRONTEND_URLS).
 * See docs/local-demo-data.md.
 */

import { pathToFileURL } from "node:url";

const LOCAL_HOSTS = new Set(["localhost", "127.0.0.1", "[::1]", "::1"]);

export function localApiUrl(value) {
  let url;
  try {
    url = new URL(value);
  } catch {
    throw new Error(`FINTRACK_API_URL is not a valid URL: ${value}`);
  }
  if (!["http:", "https:"].includes(url.protocol) || !LOCAL_HOSTS.has(url.hostname)) {
    throw new Error(`Refusing to seed ${url.origin}: this script only runs against a local API (localhost).`);
  }
  return url.origin;
}

/** YYYY-MM-DD for a day of the month `offset` months from today, never after today. */
export function dayIn(today, offset, day) {
  const first = new Date(today.getFullYear(), today.getMonth() + offset, 1);
  const last = new Date(first.getFullYear(), first.getMonth() + 1, 0).getDate();
  let date = new Date(first.getFullYear(), first.getMonth(), Math.min(day, last));
  if (date > today) date = today;
  const pad = (n) => String(n).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

export function samplePlan(today) {
  const prev = (day) => dayIn(today, -1, day);
  const cur = (day) => dayIn(today, 0, day);
  return {
    categories: [
      { name: "Pet Care", iconKey: "paw-print" },
      { name: "Coffee", iconKey: "coffee" },
    ],
    transactions: [
      ["Income", "INCOME", "4250.00", "Monthly Salary", prev(1)],
      ["Housing", "EXPENSE", "1450.00", "Rent", prev(2)],
      ["Groceries", "EXPENSE", "164.30", "Weekly Groceries", prev(5)],
      ["Dining", "EXPENSE", "58.75", "Dinner with Friends", prev(12)],
      ["Utilities", "EXPENSE", "112.40", "Electric Bill", prev(15)],
      ["Pet Care", "EXPENSE", "42.00", "Dog Food", prev(18)],
      ["Income", "INCOME", "4250.00", "Monthly Salary", cur(1)],
      ["Housing", "EXPENSE", "1450.00", "Rent", cur(1)],
      ["Groceries", "EXPENSE", "126.48", "Weekly Groceries", cur(3)],
      ["Utilities", "EXPENSE", "118.35", "Electric Bill", cur(4)],
      ["Coffee", "EXPENSE", "6.75", "Morning Latte", cur(5)],
      ["Dining", "EXPENSE", "74.50", "Dinner with Friends", cur(5)],
      ["Entertainment", "EXPENSE", "80.00", "Concert Tickets", cur(6)],
      ["Transportation", "EXPENSE", "85.00", "Monthly Train Pass", cur(6)],
      ["Income", "INCOME", "650.00", "Freelance Website Project", cur(7)],
      ["Groceries", "EXPENSE", "83.72", "Grocery Restock", cur(8)],
      ["Pet Care", "EXPENSE", "68.00", "Vet Checkup", cur(8)],
      ["Transportation", "EXPENSE", "62.40", "Gas", cur(9)],
      ["Coffee", "EXPENSE", "14.25", "Coffee Beans", cur(9)],
      ["Entertainment", "EXPENSE", "25.00", "Streaming Services", cur(10)],
      ["Utilities", "EXPENSE", "69.99", "Internet Bill", cur(10)],
      ["Transportation", "EXPENSE", "17.60", "Parking", cur(11)],
    ],
    budgets: [
      ["Groceries", "350.00"],
      ["Dining", "195.00"],
      ["Transportation", "150.00"],
      ["Entertainment", "120.00"],
      ["Pet Care", "150.00"],
      ["Coffee", "40.00"],
    ],
    month: today.getMonth() + 1,
    year: today.getFullYear(),
  };
}

async function main() {
  const email = process.env.FINTRACK_SEED_EMAIL;
  const password = process.env.FINTRACK_SEED_PASSWORD;
  if (!email || !password) {
    throw new Error("Set FINTRACK_SEED_EMAIL and FINTRACK_SEED_PASSWORD (see docs/local-demo-data.md).");
  }
  const api = `${localApiUrl(process.env.FINTRACK_API_URL ?? "http://localhost:8080")}/api`;
  const origin = process.env.FINTRACK_FRONTEND_ORIGIN ?? "http://localhost:5173";
  const authHeaders = { "Content-Type": "application/json", Origin: origin, "X-FinTrack-CSRF": "1" };

  async function call(path, method, body, token) {
    const headers = token ? { "Content-Type": "application/json", Authorization: `Bearer ${token}` } : authHeaders;
    const response = await fetch(api + path, { method, headers, body: body && JSON.stringify(body) });
    const text = await response.text();
    const json = text ? JSON.parse(text) : null;
    if (!response.ok) {
      const error = new Error(`${method} ${path} failed: ${response.status} ${json?.message ?? ""}`.trim());
      error.status = response.status;
      throw error;
    }
    return json;
  }

  try {
    await call("/auth/register", "POST", { firstName: "Alex", lastName: "Morgan", email, password });
    console.log(`Registered ${email}.`);
  } catch (error) {
    if (error.status !== 409) throw error;
    console.log(`${email} already exists; signing in.`);
  }
  const { accessToken: token } = await call("/auth/login", "POST", { email, password });

  const existing = await call("/transactions?page=0&size=1", "GET", null, token);
  if ((existing?.totalElements ?? 0) > 0) {
    throw new Error(`${email} already has transactions; nothing was added. Use a new email for a fresh demo account.`);
  }

  const plan = samplePlan(new Date());
  const ids = new Map();
  for (const category of await call("/categories", "GET", null, token)) ids.set(category.name, category.id);
  for (const { name, iconKey } of plan.categories) {
    if (!ids.has(name)) ids.set(name, (await call("/categories", "POST", { name, budgetEnabled: true, iconKey }, token)).id);
  }
  for (const [category, type, amount, description, transactionDate] of plan.transactions) {
    await call("/transactions", "POST", { categoryId: ids.get(category), type, amount, description, transactionDate }, token);
  }
  for (const [category, monthlyLimit] of plan.budgets) {
    await call("/budgets", "POST", { categoryId: ids.get(category), monthlyLimit, month: plan.month, year: plan.year }, token);
  }
  console.log(`Added ${plan.categories.length} custom categories, ${plan.transactions.length} transactions, `
    + `and ${plan.budgets.length} budgets for ${plan.year}-${String(plan.month).padStart(2, "0")}.`);
  console.log(`Sign in at ${origin} as ${email}.`);
}

// Run only when executed directly (the helpers above can be imported without side effects).
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main().catch((error) => {
    console.error(`seed-local-demo: ${error.message}`);
    process.exit(1);
  });
}
