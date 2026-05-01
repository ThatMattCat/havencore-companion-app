# Design system

The companion app is voice-first, ambient, and glanceable — a household
tool the user reaches for at arm's length, often hands-free or with a
quick tap. Calm beats flashy. The assist overlay (`voice/AssistOverlay.kt`
+ `voice/AssistVisualizers.kt`) is the visual reference; every other
surface should feel like its sibling.

This doc is the source of truth for color, typography, shape, spacing,
motion, elevation, and component patterns. Read it before adding a UI
surface or changing the look of an existing one.

## Principles

- **Material 3 is the framework.** Stock M3 + dynamic color is the
  whole design system; we layer a thin token file on top for axes M3
  doesn't cover (spacing, motion, semantic elevation, hero sizes).
  No third-party design libraries.
- **Dynamic color is the variety mechanism.** On Android 12+ the
  user's wallpaper drives primary / secondary / tertiary / containers.
  The custom palette is the fallback for older devices and for a
  future "brand-locked" Settings toggle.
- **One source for each axis.** Spacing comes from `HavenTokens.Spacing`.
  Color comes from `MaterialTheme.colorScheme`. Shape comes from
  `MaterialTheme.shapes`. Type comes from `MaterialTheme.typography`.
  Hardcoded `dp` literals only inside `ui/theme/` or as one-off
  pixel-precise visual constants with a justifying comment.
- **The voice overlay sets the bar.** Match its motion quality, its
  use of color containers for state, its `120.dp` hero sizing, its
  `220 ms` content-size animation. Inconsistency with the overlay is
  the bug.

## The four parameter objects

To change the vibe, edit these and only these:

| Object | File | What it controls |
|---|---|---|
| `ColorScheme` | `ui/theme/Color.kt`, wired in `Theme.kt` | All color roles |
| `HavenTypography` | `ui/theme/Type.kt` | Font, weights, sizes |
| `HavenShapes` | `ui/theme/Shapes.kt` | Corner radius scale |
| `HavenTokens` | `ui/theme/Tokens.kt` | Spacing, motion, elevation, hero sizes |

If a screen file reads from anything other than these four, it's a
bug — fix the screen, not the token.

## Color

`HavenCoreTheme` in `ui/theme/Theme.kt` selects a `ColorScheme` in
this order:

1. Dynamic color, when `dynamicColor=true` AND `SDK_INT >= 31`. The
   palette tracks the user's wallpaper.
2. `HavenDarkColors` when the system is in dark mode.
3. `HavenLightColors` otherwise.

Both `HavenLightColors` and `HavenDarkColors` define **all** roles —
primary / onPrimary / primaryContainer / onPrimaryContainer / secondary
/ tertiary / background / surface / surfaceVariant / outline /
outlineVariant / error* / scrim. Generate via the
[Material 3 theme builder](https://material-foundation.github.io/material-theme-builder/)
seeded with `HavenPrimary` (`#6E8AAB`) and paste the output into
`Color.kt`. Do not hand-pick individual roles — drift will follow.

### Role usage

| Role | When to use |
|---|---|
| `primary` / `onPrimary` | Primary actions (FAB, send button, "active" mic) |
| `primaryContainer` / `onPrimaryContainer` | Calm-positive state (assist Listening pill, mic disc, user bubble) |
| `secondary` / `secondaryContainer` | Neutral / informational state (Connecting, Transcribing) |
| `tertiary` / `tertiaryContainer` | Accent — noteworthy / positive surprise (assist Replying pill, cache-hit metric chip). Use sparingly; if it shows up everywhere it stops meaning anything |
| `error` / `errorContainer` | Destructive / failure state (stop button, error banner, permission missing) |
| `surface` / `surfaceVariant` | Cards and inert containers |
| `outline` / `outlineVariant` | Dividers, drag handles, OutlinedCard borders |
| `scrim` | Modal scrims. Use `MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)` — never `Color.Black.copy(...)`. |

Never call `Color(0x...)` inside a screen file. The only `Color`
literals in the app live in `ui/theme/Color.kt`.

## Typography

Stock Material 3 `Typography()` for now, exposed as `HavenTypography`
in `ui/theme/Type.kt`. Customize there when a brand font lands; do
not customize per call site.

| Style | Where it appears |
|---|---|
| `titleMedium` | Top app bar titles, screen-section headers |
| `titleSmall` | Card headers, expandable row titles |
| `bodyLarge` | Primary chat content (assistant reply, user message) |
| `bodyMedium` | Card body text, settings descriptions |
| `bodySmall` | Metadata, timestamps, secondary detail |
| `labelMedium` | Status pills, chip labels |
| `labelSmall` | Session id last-8, divider reasons, supporting metadata |

To swap to a custom font later: add `androidx.compose:ui-text-google-fonts`,
build a `FontFamily` via `GoogleFont.Provider`, pass through the
existing `Typography(...)` constructor in `Type.kt`. One file, no
screen edits.

## Shapes

`HavenShapes` in `ui/theme/Shapes.kt` maps Material 3's five shape
buckets to concrete radii from `HavenTokens.Radius`:

| `MaterialTheme.shapes` | Radius | Use for |
|---|---|---|
| `extraSmall` | 4 dp | Small surfaces, summary cards |
| `small` | 8 dp | Chips, small cards |
| `medium` | 12 dp | Standard cards (settings cards, history rows) |
| `large` | 20 dp | Message bubbles, transcript bubbles, expandable cards |
| `extraLarge` | 28 dp | Bottom sheet top corners |

Two off-scale shapes have explicit names because they don't fit the
buckets:

- `HavenTokens.Radius.pill = RoundedCornerShape(50)` — status pills,
  fully rounded chips.
- `HavenTokens.Radius.sheetTop` — top-only 28 dp rounding for bottom
  sheets. Equivalent to `MaterialTheme.shapes.extraLarge` constrained
  to top corners; named for callsite clarity.

Reading `MaterialTheme.shapes.large` is the rule — never write
`RoundedCornerShape(20.dp)` in a screen file.

## Spacing

A 4 dp grid, six steps, in `HavenTokens.Spacing`:

| Token | dp | Use |
|---|---|---|
| `xs` | 4 | Chip gaps, divider padding, micro-spacing |
| `sm` | 8 | Default item gap, small card inner padding |
| `md` | 12 | Standard inter-element gap, content padding |
| `lg` | 16 | Card padding, screen edge margin |
| `xl` | 20 | Sheet horizontal padding, large vertical breaks |
| `xxl` | 32 | Empty-state gutter, large hero margins |

Anything that doesn't fit the scale is almost certainly off the
grid — round to the nearest token.

## Motion

Three durations and two easings, in `HavenTokens.Motion`:

| Token | ms | Use |
|---|---|---|
| `Instant` | 100 | Micro-feedback (button press, tiny state nudges) |
| `Fast` | 150 | Exit transitions, small fades |
| `Standard` | 200 | Primary transitions (phase swaps, content fades) |
| `Slow` | 220 | Container size changes (`animateContentSize`) |

Easings: `EmphasizedDecelerate` for entries, `Standard1` for most
other tweens. Linear for responsive feedback (mic-level halo at 120 ms
should feel immediate).

### Anointed transition recipes

**Phase swap** (state machine with named phases — assist overlay's
`AnimatedContent`, history loading→loaded, settings ping result):

```kotlin
AnimatedContent(
    targetState = phase,
    transitionSpec = {
        (fadeIn(tween(HavenTokens.Motion.Standard))
            + slideInVertically(tween(HavenTokens.Motion.Standard)) { it / 4 })
            .togetherWith(fadeOut(tween(HavenTokens.Motion.Fast)))
            .using(SizeTransform(clip = false))
    },
    label = "phase",
) { state -> ... }
```

Promoted into `ui/motion/AnimatedSwap.kt` once a second site needs it.

**Container size change** (sheet growing as content swaps):

```kotlin
Modifier.animateContentSize(tween(HavenTokens.Motion.Slow))
```

Infinite animations (`ThinkingDots`, `SpeakingBars`, `MicLevelHero`
halo) are fine, but they should always be **gated by a phase**, never
left running on a screen the user is just reading. See Accessibility
below for the reduce-motion follow-up.

## Elevation

Tonal elevation only — never `shadowElevation`. Dark theme is the
dominant target and shadows vanish against `HavenBackground`.
`HavenTokens.Elevation`:

| Token | dp | Use |
|---|---|---|
| `Level0` | 0 | App background |
| `Level1` | 2 | Banners (`ConnectionBanner`) |
| `Level2` | 4 | Input bars, bottom sheets |
| `Level3` | 8 | Modal-over-content (rare) |

## Component patterns

Canonical recipes live (or will live, as use cases land) in
`ui/components/`. Before writing a new card / banner / pill / state
surface, check this folder. Patterns the doc anchors:

- **`StatusPill(label, container, content)`** — pill-shaped Surface
  with label. The assist overlay's status indicator is the canonical
  instance. Use for any "state-as-text-with-color" affordance.
- **`AnimatedSwap`** — the phase-swap recipe above, packaged.
- **`HeroDisc(icon, container, content, size = HavenTokens.Hero.InnerDisc)`**
  — colored disc with centered icon. Promoted from
  `voice/AssistVisualizers.kt::StaticHeroIcon`. Screen-level
  empty / error / explanatory states get a HeroDisc; in-line errors
  do not.
- **`EmptyState` / `LoadingState` / `ErrorState`** — the three state
  surfaces. History screen has the canonical inline implementations;
  extract to `ui/components/States.kt` when a second screen needs
  them. `EmptyState` takes an icon (rendered via `HeroDisc`), title,
  body, optional action button.
- **`Banner(severity, label, action?)`** — generalization of
  `ConnectionBanner`. `severity` maps to a color role (info →
  secondaryContainer, warning → tertiaryContainer, error →
  errorContainer). Use for any persistent inline status row.
- **`BottomSheetSurface`** — the assist-overlay surface setup
  (`HavenTokens.Radius.sheetTop`, `Level2` elevation,
  `animateContentSize(Slow)`, drag handle). New sheets reuse this so
  they feel identical to the assist overlay.

### Card vs OutlinedCard

- Filled `Card` for content the user **consumes** — message bubbles,
  assistant reply, settings sections.
- `OutlinedCard` for content the user **structurally interacts with**
  — `ToolCallCard`, `ReasoningCard`, anything expandable.

### Accent rule (tertiary)

`tertiaryContainer` is the "accent" color role. Use it for noteworthy
or positive-surprise surfaces (assist overlay's Replying phase,
cache-hit metric chip). If everything is tertiary, nothing is — keep
it rare.

## Accessibility

- **`contentDescription`**: if the icon is the only indication of
  state, give it a description. If a sibling `Text` already names
  the thing, `contentDescription = null` is correct (the icon is
  decorative).
- **Touch targets**: 48 dp minimum for any tap target. `IconButton`
  defaults are fine; custom shapes need explicit sizing.
- **Scrim**: `MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)`,
  not `Color.Black.copy(...)`.
- **Reduce motion** (follow-up, not yet implemented): infinite
  animations (`ThinkingDots`, `SpeakingBars`, `MicLevelHero` halo)
  should fall back to static representations when reduce-motion is
  enabled. Read via `LocalAccessibilityManager.current` once a
  cross-cutting helper is added.
- **User-facing strings**: always `stringResource(R.string.*)` or a
  typed sealed-class label. Never `.replace('_', ' ')` on a server
  token.

## Conventions and enforcement

Three-layer light-touch convention. No custom Lint rules, no Detekt,
no pre-commit greps — solo project, AI-assistant workflow.

1. **CLAUDE.md** has a Design system section pointing here. Every
   Claude session reads CLAUDE.md, so the rules propagate.
2. **The pre-merge checklist below** — 30 seconds to walk before
   committing UI changes.
3. **Optional CI safety net** (deferred until token migration is
   complete): bump `lint.xml` severity for built-in `HardcodedText`
   and add a project rule for `Color(0x...)` literals outside
   `ui/theme/`.

The win condition is **"the next 50 UI changes feel like the assist
overlay,"** not "no `dp` literal ever appears outside the theme
file."

## Vibe-shift recipes

Three worked examples — each is a single-file edit.

### Warmer / peachy

In the M3 theme builder, seed with a warm primary (e.g. `#C97A5B`).
Replace `HavenLightColors` and `HavenDarkColors` in `Color.kt`. Done.
Dynamic color users on Android 12+ are unaffected (their wallpaper
still drives the palette); brand-locked users see the warmer scheme.

### Monochrome high-contrast

In `Color.kt`, replace both schemes with a near-mono palette
(grayscale primary/secondary, single accent for tertiary). Either
ship as-is or expose a Settings toggle that forces `dynamicColor =
false` and forces `darkTheme = true` so dynamic color doesn't fight
the look.

### Brand-locked, dynamic color off

Plumb a `dynamicColor: Boolean` setting through DataStore →
`HavenCoreTheme(dynamicColor = ...)`. The parameter already exists in
`Theme.kt`. Now the brand colors (`HavenPrimary` etc.) are visible
on every device, every time. Trade variety for identity.

## Known gotchas

- **Light-scheme parity gap**: the original `LightScheme` only
  overrode primary / secondary; background / surface / tertiary /
  error fell back to M3 defaults, so light mode looked generic.
  `HavenLightColors` fixes this once it lands.
- **Dynamic color hides brand colors**: on Android 12+, `HavenPrimary`
  is never visible by default. The wallpaper wins. Anchor the brand
  with non-color elements (logo glyph, the assist overlay's specific
  shape language) — or expose the dynamic-color toggle.
- **Scrim was hardcoded** in `voice/AssistOverlay.kt` as
  `Color.Black.copy(alpha = 0.4f)`. The replacement is
  `MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)` — adapts to
  light theme, matches M3's standard sheet scrim alpha.
- **`SummaryResetDivider`** builds its label by `.replace('_', ' ')`
  on the server-emitted reason. Replace with a typed sealed-class
  label or `stringResource`.
- **Edge-to-edge inconsistency**: the assist overlay's full-screen
  scrim assumes edge-to-edge, but the rest of the app doesn't call
  `enableEdgeToEdge()`. Either commit (call `enableEdgeToEdge` in
  `MainActivity` and audit insets on every screen) or document the
  asymmetry as intentional.
- **Compose BOM bumps can rename M3 roles**. Pinned at
  `2024.12.01`. If you bump it, re-verify `ColorScheme` and `Shapes`
  role names — M3 has occasionally renamed between minor versions.

## Pre-merge checklist

Walk this before committing UI changes (~30 seconds):

- [ ] All spacing / radius / motion / elevation come from
      `HavenTokens` or `MaterialTheme.shapes`. No fresh `4.dp` /
      `8.dp` / `RoundedCornerShape(20.dp)` literals in screen files.
- [ ] All colors come from `MaterialTheme.colorScheme`. No
      `Color(0x...)` literal outside `ui/theme/`.
- [ ] Both light and dark theme rendered (toggle in Compose preview
      or on the device).
- [ ] Decorative icons have `contentDescription = null`; meaningful
      icons have a string. User-facing strings come from
      `stringResource`.
- [ ] No infinite animation runs without a phase gate.
- [ ] Empty / loading / error states use the canonical
      `EmptyState` / `LoadingState` / `ErrorState` (or the inline
      History implementations until they're extracted).
- [ ] Looks like a sibling of the assist overlay.
