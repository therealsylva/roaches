# MovieBoxxed

MovieBoxxed is a reproducible Android binary-cleanup project for the
community-maintained MovieBox v4.0 artifact.

The project removes advertising, unnecessary telemetry, invasive permissions,
and unused components while preserving the functionality already accessible in
the supplied community build.

## Current upstream

| Field | Value |
| --- | --- |
| Package | `com.community.oneroom` |
| Version | `4.0.01.0813.03` (`50020121`) |
| SHA-256 | `6a5b57d8455414ce48d912eb7177562b5fe7e66c6316575b0bded8f7f619ad6b` |
| Protection | Ijiami/IJM runtime loader |

The upstream APK and signing keys are intentionally excluded from Git.

## Development

```bash
cp /path/to/moviebox-v-4.0-community-edition.apk upstream.apk
make bootstrap
make decode
make audit
make test
```

Generated output is written beneath `.work/`. See
[the baseline](docs/BASELINE.md) and [recovery notes](docs/RECOVERY.md) for the
current technical state.

Development happens through reviewed pull requests. A release is not considered
clean until static policy checks and runtime feature/network verification pass.
