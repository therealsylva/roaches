# Roaches design constitution

## Thesis

**Midnight repertory cinema.** Roaches should feel like opening a private,
well-run screening room: quiet matte chrome, confident typography, excellent
artwork crops and controls that disappear when they are no longer useful.

Premium does not mean decorative. It means the user immediately understands
what to watch, the artwork looks intentional, playback starts cleanly and
every edge state has been considered.

## Signature

Roaches uses a restrained *crawl line*: a thin amber line for real progress,
selection or focus only. It is never a decorative border or page-wide glow.
All other colour is supplied by title artwork.

## Tokens

| Role | Value |
| --- | --- |
| Canvas | `#090A0B` |
| Raised surface | `#111315` |
| Quiet surface | `#181A1D` |
| Primary ink | `#F2F0E9` |
| Secondary ink | `#AAA9A4` |
| Crawl line | `#C47A45` |
| Error | `#E36A6A` |
| Spacing | `4, 8, 12, 16, 24, 32, 48dp` |
| Corners | `0, 6, 12dp` |
| Motion | `160–260ms`, state-driven only |

Use a single variable sans family and no arbitrary serif insertions. Use
sentence case. Metadata may use compact uppercase only when it encodes a real
category such as a video resolution.

## Composition laws

- Home begins with artwork and a concrete watch action, then direct content
  rails. Rails are not wrapped in panels.
- Search begins with the field and useful content, never a landing-page hero.
- Details place backdrop, identity, metadata and Watch before secondary tools.
- Player chrome is black, sparse, tap-revealed and timed to disappear.
- Downloads are functional rows with honest progress, not storage analytics.
- Library uses posters and recency, not empty decorative containers.
- Posters stay 2:3. Backdrops stay 16:9. Never stretch artwork.
- Only one primary button may dominate a region. Secondary actions use quiet
  icon or text treatment.
- Use cards only for elevation, selection or a unified touch target.
- A persistent bottom bar has four destinations at most.

## Accessibility and adaptation

- Minimum interactive target is 48dp.
- Small text and controls meet 4.5:1 contrast; large text and graphics meet
  3:1.
- Every meaningful image and icon has semantics; decorative artwork is hidden
  from accessibility services when adjacent text already names it.
- Layouts reflow rather than stretch at compact, medium and expanded widths.
- Text remains usable at 1.3x font scale without clipped controls.
- Reduced-motion preference removes nonessential transitions.

## Copy

Words exist to navigate or explain state. Use direct verbs: “Watch”,
“Download”, “Try again”, “Remove from library”. Errors state what failed and
what the user can do next. Do not sell the interface to the person already
using it.

## Release gate

A build is not design-complete until the anti-slop audit, compact and expanded
UI tests, accessibility checks, lint, unit tests and APK assembly pass. A
golden screenshot may be updated only when the visual change itself is
intentional and reviewed; it must never be regenerated merely to silence CI.
