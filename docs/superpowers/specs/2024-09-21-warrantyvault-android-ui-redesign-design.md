# WarrantyVault Android UI Redesign — Design Spec

**Based on:** `instruction.md` (UI alignment brief)  
**Target:** Align Android app visual language with WarrantyVault web application  
**Date:** 2024-09-21

---

## 1. Visual Design System (Tokens & Theming)

### Color Palette (from web CSS tokens)

| Token | Light Value | Dark Value | Usage |
|-------|-------------|------------|-------|
| **Primary Brand** | `#2457F5` | `#4B7CFF` | Nav bar, active tab, primary buttons |
| **Accent Brand** | `#12A7C9` | `#2CC6E6` | Highlights, secondary actions |
| **OK / Success** | `#0B7C71` | `#34C9B4` | Active warranty status |
| **OK Soft** | `#E1F7F3` | `#102E2C` | Badge backgrounds |
| **WARN / Expiring** | `#A05C05` | `#F0A03C` | Expiring soon status |
| **WARN Soft** | `#FDF0D9` | `#33240F` | Badge backgrounds |
| **DANGER / Expired** | `#C13648` | `#FF7B8A` | Expired warranty status |
| **DANGER Soft** | `#FFE9EC` | `#351A20` | Badge backgrounds |
| **NEUTRAL / Unknown** | `#5F6E84` | `#93A4BD` | No expiry / unknown |
| **NEUTRAL Soft** | `#EEF2F8` | `#1B2740` | Badge backgrounds |

### Surface Palette

| Token | Light | Dark |
|-------|-------|------|
| Canvas (page bg) | `#EDF2F9` | `#080F1E` |
| Surface (card bg) | `#FFFFFF` | `#121C30` |
| Surface-2 | `#F7F9FD` | `#16223A` |
| Surface-3 | `#EEF3FA` | `#1B2942` |
| Ink (primary text) | `#0F1F3A` | `#EEF4FF` |
| Ink-2 (secondary) | `#4D5C76` | `#A7B6CF` |
| Ink-3 (muted) | `#626D7F` | `#8492AB` |
| Line (borders) | `#DEE6F1` | `#22314C` |
| Line-2 | `#C6D4E8` | `#2F4160` |

### Nav Glass (dark in both themes)
- `navBg`: `rgba(9, 22, 48, 0.9)` / `rgba(6, 13, 28, 0.92)`
- `navInk`: `#A9BAD4` / `#9DB0CD`
- `navLine`: `rgba(255,255,255,0.1)` / `rgba(255,255,255,0.08)`

### Radii
- `rXS = 8dp`, `rSM = 10dp`, `rMD = 14dp`, `rLG = 18dp`, `rXL = 24dp`, `rPill = 999dp`

### Elevation (Shadows)
- `shadowXS`: `0 1dp 2dp rgba(15,31,58,0.06)` / `rgba(0,0,0,0.4)`
- `shadowSM`: `0 1dp 2dp + 0 6dp 16dp` (elevated cards)
- `shadowMD`: `0 10dp 26dp` (hover lift)
- `shadowLG`: `0 22dp 48dp` (modals, overlays)

### Typography
- **Font Family:** Inter (fallback to system SansSerif)
- **Scales:**
  - Display Large: 57sp, ExtraBold, -0.25sp letter-spacing
  - Headline Large: 32sp, Bold
  - Title Large: 22sp, Medium
  - Body Large: 16sp, Regular
  - Label Large: 14sp, Medium
  - Label Small: 11sp, SemiBold, 0.5sp letter-spacing, uppercase

### Background Ambience (Mobile-Appropriate)
- Two radial gradients drawn via `Canvas`:
  - `radial(900x420 at 6% -12%, brand@0.1, transparent 58%)`
  - `radial(760x420 at 104% 108%, accent@0.1, transparent 58%)`
- Optional low-fps particle layer (disabled in reduced-motion)
- Never reduce accessibility / readability

---

## 2. Reusable Compose Components

All components in `ui/components/`:

| Component | File | Purpose |
|-----------|------|---------|
| `WarrantyStatCard` | `StatCard.kt` | Dashboard stats (Active, Expiring, Expired, Documents) |
| `WarrantyProductCard` | `ProductCard.kt` | Product list items (Dashboard + Products screen) |
| `WarrantyStatusBadge` | `StatusBadge.kt` | Status pill (active/expiring/expired/unknown) |
| `WarrantySectionHeader` | `SectionHeader.kt` | Section titles with accent bar |
| `WarrantyPrimaryButton` | `Buttons.kt` | Brand-gradient CTA |
| `WarrantyGhostButton` | `Buttons.kt` | Outlined secondary actions |
| `WarrantyIconButton` | `Buttons.kt` | Nav icons, theme toggle |
| `WarrantySearchBar` | `SearchBar.kt` | Search input with focus ring |
| `WarrantyFilterSheet` | `FilterSheet.kt` | Bottom-sheet filter UI |
| `WarrantyFormField` | `FormField.kt` | Labeled OutlinedTextField |
| `WarrantySkeleton` | `Skeleton.kt` | Shimmer placeholders |
| `WarrantyToast` | `Toast.kt` | Transient messages |
| `WarrantyBottomNav` | `BottomNav.kt` | Glass-pill bottom navigation |
| `WarrantyBackground` | `Background.kt` | Canvas gradients + optional particles |

---

## 3. Screen Redesign Mapping

| Screen | Key Changes |
|--------|-------------|
| **Dashboard** | StatCard grid (4), DashboardActionsBanner (Primary + Ghost), ProductCard lists for Expiring/Recent, SectionHeaders |
| **Products** | SearchBar, FilterSheet trigger, ProductCard list, Import/Export toolbar buttons |
| **ProductDetail** | Slide-in overlay, SectionHeaders for subsections, StatusBadge, read-only FormField rows |
| **AddEditProduct** | Grouped FormField sections, PrimaryButton save, validation Toasts |
| **ScanOCR** | Large PrimaryButton camera, result preview as ProductCard-style |
| **Notifications** | NotificationItem cards with left accent border, unread styling |
| **Settings** | Card groups with SectionHeaders, Primary/Ghost buttons for export/import |
| **Navigation** | Custom BottomNav with glass effect, active = brand gradient |

---

## 4. Motion & Interaction

| Interaction | Spec | Reduced-Motion |
|-------------|------|----------------|
| Button press | Scale 0.975, tween 180ms | Disabled |
| Card hover/press | TranslateY -3dp / -1dp, shadowSM→MD | Visual only |
| Screen enter | fadeInUp 340ms cubic-bezier(0.22,1,0.36,1) | Instant |
| Detail overlay | slideInFromRight 300ms | Instant |
| Skeleton shimmer | Linear gradient translateX 1.4s infinite | Static surface-3 |
| Background particles | 15-30fps canvas loop | Disabled |

---

## 5. Light / Dark Mode

- All tokens defined once; dark overrides in `darkColorScheme`
- Semantic colors meet WCAG AA in both themes
- Background ambience uses token colors (auto-switches)
- Reduced-motion controlled via system setting

---

## 6. Accessibility & Touch Targets

- Minimum 48dp touch targets on all interactive elements
- Focus visible outline: 2dp solid brand color
- Content descriptions on all icons
- Color contrast ≥ 4.5:1 (verified by token design)
- Reduced-motion disables particles + heavy transitions

---

## 7. Implementation Roadmap

1. **Theme & Token Layer** — `Colors.kt`, extend `Theme.kt`
2. **Core Components** — `ui/components/*` (StatCard, ProductCard, StatusBadge, SectionHeader, Buttons, SearchBar, FormField, Skeleton, BottomNav, Background)
3. **Screen Refactors** — Replace each screen's UI with new components
4. **Background Ambience** — Canvas gradients + particle toggle
5. **Motion Integration** — Animateable APIs for transitions
6. **Testing** — Light/dark, reduced-motion, UI tests
7. **Polish** — Spacing, responsiveness, edge cases

---

## 8. Spec Location

`docs/superpowers/specs/2024-09-21-warrantyvault-android-ui-redesign-design.md`