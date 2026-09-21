# WarrantyVault Android UI Alignment — Match the Web Experience

Analyze the existing WarrantyVault Android app and the existing WarrantyVault web application and redesign the Android UI so it feels like the **native mobile version of the web product**, not a separate minimalist application.

Repositories:

* Android: `https://github.com/Alex07lol/Warranty-App`
* Web: `https://github.com/Alex07lol/warranty-checker`

## Objective

Preserve the Android app's existing functionality, architecture, database, OCR, notifications, warranty logic, and navigation, but substantially improve the visual design so the Android application shares the same design language, hierarchy, branding, and interaction patterns as the web version.

Do NOT simply copy desktop layouts onto mobile.

Instead:

> Extract the visual identity and UX language from the web application and reinterpret it properly for a modern Android mobile interface.

---

## 1. Design Direction

The final Android UI should feel:

* premium
* modern
* polished
* clean
* slightly futuristic
* professional
* information-dense without feeling cluttered
* consistent with WarrantyVault branding

Use the web application's existing visual identity as the primary reference.

The current Android UI is overly minimal compared with the web version. Increase visual hierarchy, depth, affordances, and clarity while still keeping the mobile interface comfortable to use.

---

# 2. Visual System

Use the web application's visual language as the source of truth.

### Typography

The web application uses Inter.

Use an Inter-like typography hierarchy in Android wherever possible.

Create a coherent type scale:

* large page heading
* secondary heading
* section heading
* body
* metadata
* labels
* status text

Avoid excessive bold text.

Use semibold/bold primarily for:

* page titles
* important statistics
* product names
* primary actions
* status labels

Use muted typography for metadata and secondary information.

---

# 3. Color System

The Android application already has a good foundation with Indigo/Slate colors.

Keep the existing color family, but align it more closely with the web UI.

Primary:

* Indigo / violet

Secondary:

* Blue

Success:

* Green

Warning:

* Amber

Error:

* Red

Use neutral Slate tones for secondary text, borders, and surfaces.

Maintain proper contrast in both light and dark themes.

The UI should feel visually related to the web application in screenshots and side-by-side comparison.

---

# 4. Background Treatment

The web version has a richer background treatment using:

* aurora effects
* subtle particles
* ambient gradients
* layered backgrounds

Bring a **mobile-appropriate version** of this concept into Android.

Do NOT reproduce the heavy WebGL particle system.

Instead create a lightweight Compose-native equivalent:

* subtle radial/linear gradients
* extremely subtle animated ambient glow
* optional slow-moving gradient
* very low visual noise
* no distracting particle field behind every element

The background must remain readable and should never reduce accessibility.

Provide a reduced-motion-safe version.

---

# 5. Dashboard Redesign

The current Android dashboard is extremely text-oriented.

Redesign it to better resemble the visual hierarchy of the web dashboard.

Create a stronger dashboard structure:

### Header

Show:

`WarrantyVault`

with a smaller contextual subtitle/greeting underneath.

Include appropriate account/theme controls where applicable.

### Statistics

Replace the extremely plain statistics row with visually distinct compact cards.

Display:

* Active warranties
* Expiring soon
* Expired warranties
* Documents

Each statistic should have:

* icon
* numeric value
* descriptive label
* optional supporting text
* status/accent indicator

Keep the cards compact enough for mobile.

### Quick Actions

Introduce a prominent action area containing:

* Scan Document
* Add Warranty
* Import Data

The primary action should visually stand out.

### Expiring Soon

Create a dedicated section.

Each warranty should clearly communicate:

* product name
* brand/model
* warranty status
* expiry date
* visual status indicator

Make expiring items visually recognizable without relying solely on color.

### Recent Products

Use richer product cards/rows with:

* product icon/image placeholder
* product name
* brand/model
* warranty status
* expiry date

Allow the entire row/card to be clickable.

---

# 6. Products Screen

The current Android product screen should be brought closer to the web application's product-library experience.

Add a strong page hierarchy:

`Library`

`My Products`

Then provide:

* search
* filter controls
* add product action
* import/export actions where appropriate

Mobile interaction should use compact controls rather than copying the desktop toolbar literally.

### Search

Create a polished search field with:

* search icon
* placeholder
* clear button
* smooth focus state

### Filters

Use a modal/bottom-sheet style filter interface.

Support the web application's concepts:

* category
* brand
* lifecycle
* warranty status
* tags
* store
* price range
* purchase date

Do not expose every filter simultaneously on the main screen.

Show active filter count when filters are applied.

---

# 7. Product Cards

Improve current product rows so they feel closer to the web application's cards.

Each item should have:

* visual leading element
* product name
* brand/model metadata
* warranty status
* expiry date
* optional document count

Use rounded surfaces, subtle borders, and restrained elevation.

Avoid excessive shadows.

Use status colors consistently:

* green = active
* amber = expiring soon
* red = expired
* neutral = unknown/no expiry

---

# 8. Product Detail

Redesign the product detail screen as a premium information page.

Hierarchy:

1. Product identity
2. Warranty status
3. Warranty period
4. Purchase information
5. Product identifiers
6. Documents
7. Service history
8. Repair center information
9. Actions

Warranty status should be immediately visible near the top.

Use cards/sections rather than a giant undifferentiated form.

Important information should be easy to scan.

---

# 9. Add/Edit Product

Improve the current form so it visually matches the web application's polished form experience.

Group fields into logical sections:

### Product

* Product name
* Brand
* Model
* Category

### Purchase

* Purchase date
* Purchase price
* Store

### Warranty

* Warranty duration
* Warranty expiry
* Warranty provider

### Identification

* Serial number
* SKU
* other identifiers

### Documents

* receipt
* warranty card
* supporting files

Use consistent field styling.

Make section titles visually distinct.

Use proper validation states and helpful error messages.

---

# 10. OCR / Scan Screen

The current OCR screen should feel like a major product feature rather than a plain form.

Create a dedicated scanning experience with:

* strong visual scan area
* document/camera icon
* clear primary action
* processing state
* OCR progress indication
* extracted data preview
* confidence/error indication where possible

After OCR completes, show extracted fields in a structured review card before saving.

Make it obvious that the user can inspect and edit extracted information.

---

# 11. Notifications

Redesign the notification screen to match the richer web visual language.

Separate notifications visually by importance.

Examples:

* warranty expiring
* warranty expired
* document reminders
* service reminders

Use:

* icon
* title
* supporting text
* date/time
* related product

Unread notifications should have a subtle visual distinction.

---

# 12. Settings

The current settings page relies heavily on large generic cards.

Redesign it into a cleaner settings hierarchy.

Sections:

### Appearance

* Theme
* Light / dark / system

### Data & Privacy

* Local storage
* Privacy information
* Export data
* Import data

### Application

* version
* about
* technology information

Keep descriptions concise.

Avoid oversized cards that consume excessive vertical space.

---

# 13. Navigation

The existing bottom navigation is functional.

Keep bottom navigation for the primary destinations:

* Dashboard
* Products
* Scan
* Alerts
* Settings

However, improve its visual treatment so it feels more consistent with the web branding.

Use:

* clear selected-state styling
* icon + label hierarchy
* subtle surface separation
* correct edge-to-edge behavior

Do not introduce unnecessary navigation complexity.

---

# 14. Components

Create reusable Compose components instead of styling each screen independently.

Examples:

* `WarrantyStatCard`
* `ProductCard`
* `WarrantyStatusBadge`
* `SectionHeader`
* `PrimaryActionButton`
* `SecondaryActionButton`
* `SearchField`
* `FilterButton`
* `FilterBottomSheet`
* `DetailSection`
* `InfoRow`
* `EmptyState`
* `LoadingState`
* `ErrorState`

Centralize styling tokens.

---

# 15. Motion & Interaction

The web version contains animated interactions.

Bring the **feel** of those interactions to Android without making the app excessive.

Use subtle:

* button press animations
* card press feedback
* fade/slide screen transitions
* animated status changes
* animated loading states
* subtle section entrance animations

Animations should be fast and restrained.

Respect Android reduced-motion/accessibility settings.

---

# 16. Light and Dark Mode

Both modes must be designed intentionally.

Do not merely invert colors.

Dark mode should use layered dark surfaces such as:

* background
* surface
* elevated surface
* surface variant

Light mode should use:

* soft background
* white surfaces
* subtle borders
* restrained shadows

Both modes should clearly resemble the WarrantyVault web application.

---

# 17. Responsive Mobile Design

Design specifically for Android phones.

The UI must work well across:

* small phones
* normal phones
* large phones
* different density settings

Avoid:

* fixed widths
* desktop-style oversized toolbars
* horizontal overflow
* cramped controls
* excessive vertical whitespace

Use responsive Compose layouts.

---

# 18. Empty, Loading and Error States

Every major screen should have polished states for:

### Empty

Explain what the user should do next.

### Loading

Use skeleton/loading indicators where appropriate.

### Error

Show a clear human-readable explanation and retry action.

Do not leave empty white/blank areas.

---

# 19. Important Constraint

Do NOT change the application's underlying behavior just for visual redesign.

Preserve:

* Room database
* existing DAO layer
* warranty calculations
* OCR engine
* notifications
* WorkManager
* import/export logic
* service history
* repair center features
* user handling
* navigation behavior

Only modify functionality when necessary to support the improved UX.

---

# 20. Implementation Process

Before editing code:

1. Inspect the current Android UI implementation.
2. Inspect the web application's HTML/CSS/JS visual system.
3. Identify reusable design tokens.
4. Map equivalent web screens to Android screens.
5. Identify inconsistencies between Android and web.
6. Create reusable Compose components.
7. Refactor screens to use those components.
8. Verify light mode.
9. Verify dark mode.
10. Verify accessibility and touch targets.
11. Build the application.
12. Fix all compilation/runtime/UI issues.

---

# 21. Visual Priority

When deciding between the existing Android design and the web design, prioritize:

1. WarrantyVault brand identity
2. consistent typography
3. strong information hierarchy
4. clear warranty status
5. polished cards and surfaces
6. clear primary actions
7. accessibility
8. mobile usability
9. animation
10. decorative effects

Do not sacrifice usability for visual effects.

---

# 22. Final Result

The finished application should look like:

**WarrantyVault Web → translated into a polished native Android experience**

not:

**simple Android Material app + unrelated web application**

The two products should immediately appear to belong to the same design system when viewed side by side.

Use the existing web application as the visual reference and the existing Android application as the functional foundation.

Do not rewrite the application from scratch.

Perform the redesign incrementally while preserving working functionality.
