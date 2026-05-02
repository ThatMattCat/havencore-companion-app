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

## Visual identity — Quiet Tech

The companion app's identity is **Quiet Tech**: a self-hosted tool you
own, not a cloud product you rent. Slate canvas, cool blue primary,
warm amber as the rare accent. Dark-first. Confident, technical-but-
warm, never showy.

### Palette

Seeded from `#2D5BA1` (cool blue). The dark scheme is the canonical
look — anchored to a deep slate background (`#0F1622`) so cards lift
without needing a stroke. Light scheme is a clean inverse for users
who prefer it.

| Role | Dark | Light | Use |
|---|---|---|---|
| `background` | `#0F1622` | `#FBFCFE` | App canvas |
| `surface` | `#1A2332` | `#FFFFFF` | Cards |
| `primary` | `#ADCBF8` | `#2D5BA1` | Send button, FAB, mic-active |
| `primaryContainer` | `#194878` | `#D5E3FF` | User bubble, Listening pill |
| `secondary` | `#BCC7DC` | `#545F71` | Neutral text/chrome |
| `secondaryContainer` | `#3C4858` | `#D8E3F8` | Neutral pills, accent discs |
| `tertiary` | `#FFB870` | `#8A6420` | Rare accent — see rule below |
| `tertiaryContainer` | `#693E00` | `#FFDDB1` | Replying pill, cache-hit chip |
| `error*` | M3 default | M3 default | Wrong / destructive |

**The tertiary rule.** Tertiary is *the* alive accent — warm amber
against the cool palette. It only fires for moments worth noticing:
the assist overlay's Replying phase, a cache-hit metric chip. If
tertiary shows up everywhere it stops meaning anything; default
neutral state should be `secondaryContainer`.

### Typography

Stock Material 3 today (system sans). The intended brand typeface is
**Inter** via `androidx.compose:ui-text-google-fonts`, but adopting it
cleanly needs a `res/values/font_certs.xml` resource with the Google
Fonts provider certs (a long, opaque base64 file neither AndroidX nor
Compose ship). Deferred until that resource lands.

When ready, the swap is one file: build a `FontFamily` via
`GoogleFont.Provider` in `ui/theme/Type.kt`, pass it through
`Typography(...)`, no screen edits required.

### Dynamic color

**Off by default.** The brand-locked palette is the canonical
experience; users who want their wallpaper-driven palette toggle it on
in Settings → Appearance. This is the only way to commit to an
identity — on Android 12+, dynamic color overrides our scheme on every
device.

The `HavenCoreTheme(dynamicColor = ...)` parameter is plumbed through
DataStore (`SettingsRepository.dynamicColorFlow`) so the toggle takes
effect live without an app restart.

### Brand glyphs

Four shapes make the app recognizable at a glance:

1. **The hero disc + halo** — the `MicLevelHero` in the assist
   overlay. The 120 dp halo scaling with mic amplitude is the brand's
   signature motion.
2. **The status pill** — fully-rounded, color-coded label (`StatusPill`).
   Used for assist phases, push status, ping outcome.
3. **The asymmetric user bubble** — `HavenBrandShapes.UserBubble`
   (large radii except a small bottom-end nip). Distinguishes user
   turns from assistant cards instantly.
4. **The accent disc** — small leading icon-on-circle (`AccentDisc`)
   used in card headers, history rows, default-assistant card.

Reach for these shapes when a surface needs identity, not novelty.

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

Brand-identity shapes live in `HavenBrandShapes` (also in `Shapes.kt`):

- `HavenBrandShapes.UserBubble` — large radii except a small
  bottom-end nip (`xs` instead of `lg`). One of the four brand glyphs
  (see "Visual identity"); the asymmetry is what distinguishes a user
  turn from an assistant card at a glance.

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
| `Level1` | 2 | Inline `Banner`, user bubble |
| `Level2` | 4 | Input bars, bottom sheets |
| `Level3` | 8 | Modal-over-content (rare) |

## Component patterns

Canonical recipes live in `ui/components/`. Before writing a new card
/ banner / pill / state surface, check this folder.

- **`StatusPill(label, container, content)`** — pill-shaped Surface
  with label. The assist overlay's phase indicator is the canonical
  caller. Use for any "state-as-text-with-color" affordance.
- **`Banner(severity, text, icon?, actionLabel?, onAction?)`** —
  inline status row. `severity` maps to a color role (`Info` /
  `Progress` → secondaryContainer, `Warning` → tertiaryContainer,
  `Error` → errorContainer). Used for ping result, push status,
  connection state.
- **`HeroDisc(icon, discColor, iconColor)`** — 120 dp halo with a
  76 dp inner disc and 36 dp icon. Screen-level empty / error /
  explanatory surfaces; the assist overlay's terminal phases.
- **`AccentDisc(icon, discColor, iconColor, size = 32.dp, iconSize = 18.dp)`**
  — small inline leading-icon variant of `HeroDisc`. Use in card
  headers, list rows, banner-style affordances. Settings cards,
  History rows, and the default-assistant card all use it.
- **`EmptyState(icon, title, body?)` / `LoadingState()` /
  `ErrorState(title, message, onRetry?)`** — three canonical
  screen-level state surfaces. All center vertically, all use a
  `HeroDisc` for the icon. In-line errors (banners, field validation)
  belong in `Banner`, not here.
- **`AnimatedSwap(targetState, contentKey?) { state -> ... }`** —
  packaged phase-swap recipe (the spec from the Motion section). Used
  by the assist overlay's `PhaseBody`, History's loading→loaded→error
  swap, the Settings ping result, the push status.
- **`BottomSheetSurface(maxHeight = ∞) { ... }`** — assist-overlay
  surface anatomy: top-only `extraLarge` radius, `Level2` tonal
  elevation, `animateContentSize(Slow)`, drag handle. New sheets reuse
  this so they feel identical to the assist overlay.

### Card vs OutlinedCard vs flat row

- Filled `Card` (or `Surface`) for content the user **consumes** —
  message bubbles, assistant reply, settings sections.
- `OutlinedCard` for content the user **structurally interacts with**
  on its own — settings groups in other apps, single isolated
  expandable cards.
- **Flat row** for content that lives **inside** another card —
  `ToolCallRow`, `ReasoningRow` are inline expandable rows inside
  `AssistantTurnCard`, not nested cards. The parent card owns the
  visual frame; nesting cards adds noise without adding meaning.

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

We've picked the vibe (Quiet Tech, see "Visual identity" above), but
the system is still small enough to re-skin in one or two file edits
if the identity ever needs to change. Three worked examples — each is
a single-file edit.

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

- **Dynamic color hides brand colors**: on Android 12+, dynamic color
  (when enabled) overrides every brand role. We default to OFF and
  expose a Settings toggle, but if a user opts in, the Quiet Tech
  palette is invisible — the `BrandSeed` is the fallback only. Anchor
  identity with the four brand glyphs above when this matters.
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
