# Restaurant Manager — UI Redesign Roadmap

**Goal:** Transform the current functional-but-bare interface into a beautiful, touch-first, production-grade restaurant management system. Every surface must feel as good on a kitchen tablet or POS terminal as it does on a desktop.

---

## Current State Audit

### What works well
- Clean data model and API contract
- Correct routing structure and session flow
- KDS has real-time WebSocket integration
- POS has a functional order + payment flow

### Critical problems to fix

| Problem | Where | Impact |
|---|---|---|
| Hardcoded hex colors (`text-[#35523d]`, `bg-[#132018]`) | Every file | No theming, no dark mode, no consistency |
| Touch targets ~28 px (`px-3 py-1 text-xs`) | All action buttons | Unusable on any touchscreen |
| `window.prompt()` / `window.alert()` for user input | Tables, KDS, Orders | Blocks the thread, hideous on iOS/Android |
| No persistent navigation | All pages | Every page is isolated, staff can't orient |
| No loading skeletons | All pages | "Loading…" flash breaks perceived performance |
| Status changed via `<select>` dropdowns | Orders, Tables | Small target, misfire-prone on touch |
| POS menu is an unsorted flat list | `/pos` | Unusable at volume; no search, no categories |
| KDS 4-column collapses on mobile | `/kds` | Kitchen tablets see a vertical pile |
| Dashboard nav is unstyled `.panel` links | `/dashboard` | No visual affordance of purpose |
| `window.prompt()` for cancel reason | Orders | Blocks page, no validation |
| No empty states / illustrations | All list pages | Blank space feels broken |
| No confirmation modals for destructive actions | Tables, Orders | Accidental deletes/cancellations |

---

## Design System (Done ✅)

`web/src/app/globals.css` now contains the full token system:

- **Color palette** — `--clr-green-*` through `--clr-neutral-*` raw scales; semantic tokens `--color-brand`, `--color-danger`, `--color-text-primary`, `--color-bg-surface`, etc.
- **Touch targets** — `--touch-xs` 36 px → `--touch-card` 80 px
- **Size/spacing** — `--size-1` (4 px) through `--size-64` (256 px), `--space-*`, `--inset-*`
- **Typography** — `--text-xs` through `--text-5xl`, `--leading-*`, `--weight-*`
- **Elevation** — `--shadow-none` through `--shadow-xl`, `--shadow-focus`
- **Motion** — `--ease-spring`, `--ease-out`, `--duration-fast` through `--duration-slow`
- **Component classes** — `.btn`, `.btn-primary`, `.btn-danger`, `.input`, `.select`, `.badge`, `.badge-*`, `.alert`, `.card`, `.card-interactive`

**Rule for all redesign work:** zero hardcoded hex values. Every color, spacing, and sizing value must reference a token.

---

## Phase Plan

### Phase 0 — Shell & Navigation (do first, unblocks everything)
*Estimated scope: 1 shared layout file + 1 sidebar component*

The app currently has no persistent navigation. Staff have no way to know where they are or move between modules without going back to the dashboard. Fix this before touching any page content.

**Deliverables:**
1. **`web/src/components/AppShell.tsx`** — Wraps all staff pages. Contains a collapsible left sidebar + a top bar.
2. **`web/src/app/(staff)/layout.tsx`** — Route group that wraps all staff-facing pages with `<AppShell>`.
3. **`web/src/components/NavSidebar.tsx`** — Role-aware navigation links grouped by section.

**Sidebar sections:**

```
Operations
  • Dashboard      /dashboard
  • POS            /pos
  • Orders         /orders
  • KDS            /kds
  • Tables         /tables
  • Reservations   /reservations
  • Group Ordering /group-ordering
  • Pickup         /orders/pickup

Management
  • Menu           /menu
  • Payments       /payments
  • Financial      /financial
  • Receipts       /receipts
  • Shifts         /shifts

Admin (ADMIN/MANAGER only)
  • Users          /users
  • Branches       /branches
  • Audit Log      /audit
  • Admin Panel    /admin
```

**Design specs:**
- Desktop: fixed 260 px sidebar (`--layout-sidebar`), collapsible to 64 px icon rail
- Tablet (≤1024 px): sidebar hidden behind hamburger, slides in as overlay
- Mobile (≤640 px): bottom navigation bar showing 5 most-used icons
- Active link: `--color-brand` left border, `--color-bg-brand-subtle` background
- Each link: minimum `--touch-md` (48 px) height, 16 px horizontal padding

---

### Phase 1 — Global Token Migration
*Scope: every page file — mechanical find-and-replace*

Replace all hardcoded Tailwind arbitrary values with token-based utility classes or CSS variables. This is mechanical but must be done page-by-page to avoid regressions.

**Replacement table:**

| Old | New |
|---|---|
| `text-[#35523d]` | `text-[var(--color-text-secondary)]` or `.text-muted` utility |
| `text-[#132018]` | `text-[var(--color-text-primary)]` |
| `bg-[#132018]` | `bg-[var(--color-brand)]` |
| `border-[#cfe0c8]` | `border-[var(--color-border-default)]` |
| `bg-[#fee2e2]` | `bg-[var(--color-danger-subtle)]` |
| `text-[#991b1b]` | `text-[var(--color-danger)]` |
| `rounded-full … px-3 py-1` | `.btn .btn-secondary` or `.btn-sm` |
| `rounded-full bg-[#132018] px-4 py-2 text-white` | `.btn .btn-primary` |
| `rounded-xl border border-[#cfe0c8] p-2` | `.input` |
| `rounded-xl border border-[#cfe0c8] p-2 text-sm` | `.select` |

**Page order (easiest → most complex):**
1. `/audit` — read-only table
2. `/receipts` — read-only list
3. `/payments` — read-only list
4. `/financial` — mostly read-only
5. `/users` — list + form
6. `/branches` — list + form
7. `/shifts` — list + form
8. `/reservations` — list + form
9. `/menu` — list + form
10. `/orders/pickup` — search form
11. `/orders/[id]` — detail view
12. `/orders` — list + status management
13. `/tables` — map + management
14. `/dashboard` — metrics + nav grid
15. `/pos` — complex checkout flow
16. `/kds` — real-time kanban board
17. `/scan/[token]` — customer-facing
18. `/bill/[token]` — customer-facing

---

### Phase 2 — Touch Target Compliance
*All interactive elements must meet minimum 44 px / Apple HIG*

Every button, link, select, and input must pass this test: **minimum height 44 px on any viewport**.

**Changes per component type:**

**Buttons:**
```css
/* Current (bad) */
.rounded-full.border.px-3.py-1.text-xs  /* ~28px height */

/* Target */
.btn.btn-sm  /* min-height: var(--touch-sm) = 44px */
.btn.btn-md  /* min-height: var(--touch-md) = 48px - default */
.btn.btn-lg  /* min-height: var(--touch-lg) = 56px - primary CTA */
```

**Status action buttons (Orders, KDS, Tables):**
Replace `<select>` dropdowns with segmented button groups or tap-target rows:
```
[PENDING] → [Accept ▶] button (full width, 56px, green)
[CONFIRMED] → [Start Prep ▶] button (full width, 56px, amber)
[PREPARING] → [Mark Ready ▶] button (full width, 56px, blue)
[READY] → [Complete ✓] button (full width, 56px, brand)
```

**Form inputs:**
```css
.input { min-height: var(--touch-md); padding: var(--inset-sm); }
.select { min-height: var(--touch-md); }
```

**Table action cards (Tables page):**
Action buttons inside table cards (`Edit`, `QR Meta`, `Table Bill`, etc.) must be at least 44 px tall. Group into a 2-column grid on mobile instead of a wrapping flex row.

---

### Phase 3 — Replace `prompt()` and `alert()` with Modals
*Eliminates the 4 worst UX offenders in the codebase*

**Occurrences to eliminate:**

| File | Current | Replacement |
|---|---|---|
| `tables/page.tsx:98–101` | `prompt()` for edit table number/capacity/zone | `<EditTableModal>` with form |
| `tables/page.tsx:124` | `alert()` for QR metadata | `<QrMetaModal>` with copyable token + link |
| `orders/page.tsx:54` | `prompt()` for cancel reason | `<CancelOrderModal>` with textarea + confirm |
| Any confirm-before-delete | None currently | `<ConfirmModal>` reusable component |

**`<Modal>` base component spec:**
- Renders into a portal at `document.body`
- Backdrop: `rgba(0,0,0,0.4)` with `backdrop-filter: blur(2px)`
- Panel: centered, `--radius-xl`, `--shadow-xl`, max-width 480 px, full-screen on mobile
- Close on backdrop click or `Escape` key
- Focus trap while open
- Animates in with `scale(0.97) → scale(1)` + `opacity 0 → 1` using `--duration-normal`

**Components to build:**
```
web/src/components/Modal.tsx          — base portal + backdrop
web/src/components/ConfirmModal.tsx   — title, message, cancel/confirm buttons
web/src/components/EditTableModal.tsx — number, capacity, zone fields
web/src/components/QrMetaModal.tsx   — token display, copy button, scan URL
web/src/components/CancelOrderModal.tsx — reason textarea
```

---

### Phase 4 — Loading States & Skeletons
*Replace "Loading…" text with skeleton UI that matches the page shape*

**Skeleton pattern:**
```tsx
// Pulse animation using the token system
<div className="animate-pulse rounded-[var(--radius-md)] bg-[var(--color-bg-muted)] h-[var(--size-12)] w-full" />
```

**Per-page skeletons:**

| Page | Skeleton shape |
|---|---|
| Dashboard | 3 metric tiles (40px number + 2 lines) + 14 nav tile ghosts |
| Orders | 5 order card ghosts (2 lines header + status pill) |
| Tables | Grid of 6–9 table card ghosts |
| KDS | 4 columns × 3 card ghosts per column |
| POS | Left: 8 menu item rows; Right: checkout panel outline |
| Menu | Category chips row + 8 item rows |

**Global `<Spinner>` component** for inline button loading states:
- 18 px SVG spinner, current color
- Replace all `"Creating..."` / `"Updating..."` / `"Working..."` strings

---

### Phase 5 — Dashboard Redesign
*The entry point sets the tone for the entire app*

**Current:** Metric tiles + flat list of link-styled `.panel` blocks.

**Target:**

```
┌─────────────────────────────────────────────────┐
│  Admin Dashboard        [Refresh]  [Sign Out]   │
│  Thursday, 26 March 2026                        │
├──────────┬──────────┬──────────────────────────┤
│ Active   │ Tables   │ Gross Sales Today        │
│ Orders   │ Occupied │                          │
│  [n]     │  [n/N]   │  GHS [xxx.xx]            │
│          │          │                          │
├──────────┴──────────┴──────────────────────────┤
│  OPERATIONS           MANAGEMENT     ADMIN     │
│  ┌──────────┐ ┌──────┐ ┌──────┐ ┌──────────┐  │
│  │ 🧾 POS   │ │Orders│ │ KDS  │ │  Tables  │  │
│  │          │ │      │ │      │ │          │  │
│  └──────────┘ └──────┘ └──────┘ └──────────┘  │
│  ...                                           │
└─────────────────────────────────────────────────┘
```

**Nav tile specs:**
- Size: 160 × 120 px on desktop, full-width on mobile
- Contains: icon (32 px), label, optional badge (active count)
- Uses `.card-interactive` with hover lift + shadow
- Touch target: entire tile is tappable (min 80px height: `--touch-card`)
- Live badges: "Orders" tile shows count of active orders; "Tables" shows occupied/total

---

### Phase 6 — POS Redesign
*The most heavily-used staff page; must work on a 10" tablet*

**Current problems:**
- Menu is an unsorted flat list — 50+ items is unworkable
- No item images or descriptions
- Cart items use tiny `+/-` buttons (~28px)
- Payment flow is hidden below the fold after order creation

**Target layout (tablet landscape):**
```
┌─────────────────────┬──────────────────┐
│  MENU               │  ORDER           │
│  [Search items...]  │                  │
│  ┌─────────────────┐│  Type: [DINE_IN] │
│  │ Starters  Mains ││  Table: [T-03  ] │
│  │ Drinks    Sides ││                  │
│  └─────────────────┘│  ┌────────────┐  │
│                     │  │ Jollof Rice│  │
│  ┌──────┐ ┌──────┐  │  │ 1  × GHS 35│  │
│  │ Item │ │ Item │  │  │ [−] [qty] [+]│  │
│  │      │ │      │  │  └────────────┘  │
│  │ GHS  │ │ GHS  │  │                  │
│  └──────┘ └──────┘  │  Total: GHS xxx │
│  ┌──────┐ ┌──────┐  │                  │
│  │ Item │ │ Item │  │  [Create Order]  │
│  └──────┘ └──────┘  │  ─────────────── │
│                     │  Payment         │
│                     │  [MoMo][Card][Cash]│
└─────────────────────┴──────────────────┘
```

**Key changes:**
- Category chip row (sticky, scrollable horizontally)
- Menu grid: 2 columns on tablet, 3 on desktop; cards 120px tall
- Cart `+/-` buttons: `--touch-md` (48px) minimum, large font
- Payment method: 3 large radio-style buttons (not a `<select>`)
- After order created: payment section scrolls into view with animation
- Total bar: sticky at bottom of checkout panel

---

### Phase 7 — KDS Redesign
*Kitchen-specific: bright, readable at distance, fat-finger proof*

**Current problems:**
- 4 columns collapse to 1 on mobile/tablet
- Cards are small (font-size small, action button 28px)
- No visual urgency (no age indicator, no color by time)
- No swipe-to-advance on touch

**Target layout:**
```
PENDING (3)       CONFIRMED (2)    PREPARING (5)    READY (1)
┌──────────────┐  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐
│ #A3B2 T-05   │  │ #C9D1 T-02   │ │ #FF01 T-08   │ │ #8821 T-01   │
│ 14:23        │  │ 14:18  ⏱ 7m  │ │ 14:10 ⚠ 15m  │ │ 14:05 ✅ READY│
│              │  │              │ │              │ │              │
│ 2× Jollof    │  │ 1× Tilapia   │ │ 3× Rice+Stew │ │ 1× Chicken   │
│ 1× Chicken   │  │ 1× Plantain  │ │ 1× Water     │ │ 2× Coke      │
│              │  │              │ │              │ │              │
│ [Accept ▶]   │  │ [Start Prep] │ │ [Mark Ready] │ │ [Complete ✓] │
└──────────────┘  └──────────────┘ └──────────────┘ └──────────────┘
```

**Key changes:**
- Action button spans full card width, 56px height (`.btn-lg`)
- Age badge: green 0–5 min, amber 5–12 min, red 12+ min
- Swipe right = advance status (touch gesture)
- Column header shows count badge
- Font sizes: order ID 16 px, item name 18 px (readable at arm's length)
- KDS enters full-screen mode on tablet (hides sidebar)
- Compact mode toggle: show only item names + button (for tiny screens)

---

### Phase 8 — Tables Map Redesign
*Eliminate `prompt()`, add a visual floor plan feel*

**Current problems:**
- Color-coded cards but no spatial layout
- Edit uses `prompt()` (blocks thread)
- Action buttons are 28px tall, 7 of them per card
- Zone filter is a `<select>` dropdown

**Target:**

```
┌─────────────────────────────────────────────┐
│ Tables  [+ Add Table]  Search... [zone▼]    │
│                                             │
│ MAIN FLOOR                                  │
│  ┌──────┐  ┌──────┐  ┌──────┐  ┌──────┐   │
│  │  T-1 │  │  T-2 │  │  T-3 │  │  T-4 │   │
│  │ 🟢 4 │  │ 🔴 4 │  │ 🟡 2 │  │ ⚫ 6 │   │
│  │AVAIL │  │OCCUP │  │RESRV │  │CLSD  │   │
│  └──────┘  └──────┘  └──────┘  └──────┘   │
│                                             │
│ TERRACE                                     │
│  ┌──────┐  ┌──────┐                         │
│  │  T-5 │  │  T-6 │                         │
└─────────────────────────────────────────────┘
```

**Key changes:**
- Tables grouped by zone with zone header
- Status shown as large colored tile (not just text badge)
- Tap a table → side drawer opens with:
  - Table info (number, capacity, zone)
  - Active orders
  - Status change buttons (4 large color-coded buttons)
  - Edit form (inline, not `prompt()`)
  - QR token with copy + display
  - Quick links: Table Bill, Web Menu
- Drawer slides in from right; on mobile becomes full-screen bottom sheet
- "Add Table" opens a form drawer (not inline form at top)

---

### Phase 9 — Orders Page Redesign
*Replace `<select>` status management with visual controls*

**Current problems:**
- Status changed via `<select>` dropdown (small, misfire-prone)
- Cancel uses `prompt()` for reason
- No visual distinction between order types
- List is dense with no visual hierarchy

**Target:**

```
┌─────────────────────────────────────────────┐
│ Active Orders  [filter chips: ALL DINE PICKUP]│
│ [Search...                              ]    │
├─────────────────────────────────────────────┤
│ #A3B2C1 · DINE_IN · Table 5       14:23     │
│ 3 items · GHS 85.00                         │
│ [PENDING]  →  [Accept ▶]  [Cancel ✕]        │
├─────────────────────────────────────────────┤
│ #D4E5F6 · PICKUP · Code ABC123     14:18    │
│ 2 items · GHS 45.00                         │
│ [CONFIRMED]  →  [Start Prep]  [Cancel ✕]    │
└─────────────────────────────────────────────┘
```

**Key changes:**
- Status badge `.badge-*` shows current state in color
- Single prominent action button (next state) instead of a `<select>`
- Cancel opens `<CancelOrderModal>` instead of `prompt()`
- Filter chips replace 3 `<select>` filter dropdowns
- Order type icons (🍽️ dine-in, 📦 pickup, 🛵 delivery)
- Pull-to-refresh on mobile

---

### Phase 10 — Customer-Facing Pages
*`/scan/[token]` and `/bill/[token]` — the public-facing pages*

These pages are seen by restaurant customers, not staff. They need maximum polish.

**`/scan/[token]` (web menu):**
- Full mobile-first design (assume 390px wide phone)
- Menu items in cards with category tabs
- Add-to-cart with quantity picker
- Sticky cart summary bar at bottom
- Checkout flow with customer details

**`/bill/[token]` (table bill):**
- Clean receipt-style layout
- Itemized list with prices
- Total section with tax/service charge if applicable
- Payment status indicator
- "Request service" button

---

## Component Library

Build these shared components **before** starting Phase 3+:

```
web/src/components/
├── Modal.tsx              # Base modal portal
├── ConfirmModal.tsx       # Confirm/cancel dialog
├── Drawer.tsx             # Side drawer + bottom sheet
├── Skeleton.tsx           # Animated skeleton placeholder
├── Spinner.tsx            # Inline loading indicator
├── EmptyState.tsx         # Empty list state with icon + message
├── StatusBadge.tsx        # Unified status badge using .badge-* classes
├── ActionButton.tsx       # Big touch-optimized single action button
├── FilterChips.tsx        # Horizontal scrollable filter tabs
├── SearchInput.tsx        # Search field with clear button
└── PageHeader.tsx         # Consistent page title + action slot
```

---

## Breakpoints

| Name | Width | Layout |
|---|---|---|
| `sm` | < 640 px | Single column, bottom nav, bottom sheets |
| `md` | 640–1023 px | Two columns, hamburger sidebar, side drawers |
| `lg` | 1024–1279 px | Full sidebar (collapsed rail), wider content |
| `xl` | ≥ 1280 px | Full sidebar expanded, multi-column content |

**Special viewport targets:**
- `10" tablet landscape (1280×800)` — POS and KDS primary device
- `7" tablet portrait (800×1280)` — KDS secondary, Tables map
- `Phone (390×844)` — Orders, customer scan pages

---

## Interaction Design

### Motion principles
- **Meaningful, not decorative** — animations communicate state change (status transition, modal open)
- **Fast** — default `--duration-fast` (120ms) for micro-interactions; `--duration-normal` (200ms) for panels
- **Reducible** — respect `prefers-reduced-motion`

### Touch gesture map

| Gesture | Page | Action |
|---|---|---|
| Swipe right on KDS card | KDS | Advance order status |
| Swipe left on KDS card | KDS | Show reject option |
| Long press on table card | Tables | Quick status menu |
| Pull down | Orders, Tables, KDS | Refresh data |
| Tap anywhere on order row | Orders | Open order detail drawer |

### Feedback patterns
- Button tap: `scale(0.97)` for 100ms then release (CSS `active:scale-[0.97]`)
- Success action: brief green flash on the affected card
- Error: shake animation + red border on the relevant input
- Loading: spinner replaces button label (width preserved with `min-width`)

---

## Typography Hierarchy

| Level | Token | Size | Weight | Use |
|---|---|---|---|---|
| Page title | `--text-2xl` | 24px | `--weight-semibold` | H1 on each page |
| Section header | `--text-xl` | 20px | `--weight-semibold` | Panel/drawer titles |
| Card title | `--text-base` | 16px | `--weight-medium` | Order ID, table number |
| Body | `--text-sm` | 14px | `--weight-regular` | Descriptions, labels |
| Caption/kicker | `--text-xs` | 12px | `--weight-medium` | Section kickers, meta |
| Data / numbers | `--text-3xl`–`--text-4xl` | 30–36px | `--weight-semibold` | Dashboard metrics |

---

## Accessibility Checklist

- [ ] All interactive elements have `aria-label` or visible text
- [ ] Color is never the only indicator (status badges have text + color)
- [ ] Focus ring visible on keyboard navigation (`--shadow-focus` token)
- [ ] Modal traps focus and returns focus on close
- [ ] Spinners have `aria-busy="true"` and `aria-label="Loading"`
- [ ] Error messages linked to inputs with `aria-describedby`
- [ ] KDS columns have `aria-live="polite"` for screen reader updates
- [ ] Touch targets meet 44×44 px minimum

---

## Implementation Priority

| Priority | Phase | Why |
|---|---|---|
| 🔴 P0 | Phase 0 — Shell & Nav | Staff can't work without navigation |
| 🔴 P0 | Phase 3 — Remove `prompt()` | Broken on iOS Safari, embarrassing |
| 🟡 P1 | Phase 2 — Touch Targets | Required for any tablet deployment |
| 🟡 P1 | Phase 1 — Token Migration | Enables dark mode and theming |
| 🟢 P2 | Phase 6 — POS Redesign | Highest-volume staff page |
| 🟢 P2 | Phase 7 — KDS Redesign | Kitchen staff primary interface |
| 🔵 P3 | Phase 5 — Dashboard | Manager visibility |
| 🔵 P3 | Phase 8 — Tables Map | Front-of-house staff |
| 🔵 P3 | Phase 9 — Orders Page | General staff |
| ⚪ P4 | Phase 4 — Skeletons | Polish |
| ⚪ P4 | Phase 10 — Customer pages | Customer-facing polish |

---

## File Change Summary

When complete, every `.tsx` page file will:
1. Use only token-based CSS (no `text-[#hex]` patterns)
2. Have all interactive elements ≥ 44px height
3. Use modal components instead of `prompt()`/`alert()`
4. Import from the shared component library
5. Show a skeleton loader during data fetch
6. Have a proper empty state for zero-result lists

The design token system in `globals.css` is already in place. The rest is component-by-component execution of this plan.
