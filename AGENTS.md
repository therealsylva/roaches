# Roaches repository instructions

These rules apply to every file in this repository.

## Product invariant

Roaches is a native Android media product. It must never look or read like a
SaaS dashboard, admin console, marketing landing page, hacker terminal or a
rebrand of another streaming application. Content artwork is the visual lead;
application chrome recedes.

## Mandatory design workflow

1. Read `DESIGN.md` before changing any UI.
2. Reuse `RoachesTheme`, `RoachesSpacing`, `RoachesShapes` and the shared UI
   components. Do not introduce one-off colours, spacing or corner radii in a
   feature screen.
3. Preserve one focal point and one primary action per screen region.
4. Implement loading, empty, offline, failure and content states together.
5. Run `scripts/anti_slop.py`, unit tests, lint and APK assembly before handoff.
6. Review rendered screenshots at compact and expanded widths whenever UI
   structure changes.

## Hard bans

- no analytics dashboards, KPI tiles, charts or admin sidebars;
- no glassmorphism, neon glow, gradient text or decorative gradients;
- no purple/blue startup palette, bento grid or equal three-card feature row;
- no section-sized rounded containers and no `ElevatedCard` as a default;
- no giant launch headline, decorative numbering or unnecessary onboarding;
- no emojis, fake testimonials, fake ratings or aspirational filler copy;
- no phrases such as “elevate”, “cinematic universe”, “unlock premium”,
  “seamless entertainment” or “reimagined”;
- no copied third-party graphics, marks or exact interface compositions;
- no advertising, analytics, attribution, account, subscription or telemetry
  dependency.

Gradients are permitted only as scrims over artwork for text legibility.

## Engineering invariant

Keep provider, persistence, playback and Compose UI separated. Network
failures must be recoverable. Never log signed headers, bearer tokens, stream
URLs or viewing history. All user data remains local unless a feature
explicitly requires a provider request.
