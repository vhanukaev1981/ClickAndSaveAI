# Android launcher icon redesign proposal

Base source boundary: `887518646fb66b36b10345fe2187e087457395ae`

This review-only proposal compares four launcher directions. The Owner selected the final **C + $** direction for Android implementation.

| Option | Direction | Preview |
|---|---|---|
| A | Premium dark / elegant | [Preview](./option_A_preview.svg) |
| B | Savings-focused / financial clarity | [Preview](./option_B_preview.svg) |
| C | AI-tech / modern intelligence | [Preview](./option_C_preview.svg) |
| D | Owner-selected C + $ monogram | [Preview](./option_D_preview.svg) |

## Implemented default

The implemented launcher uses a bold white C containing a mint-green dollar-sign S, plus a restrained mint intelligence spark on a midnight background. The silhouette remains identifiable at small launcher sizes and works on light and dark home-screen contexts.

[Implemented Android context preview](./implemented_option_D_android_home.svg)

## Android resource strategy

- Adaptive icon background and foreground remain separate.
- A dedicated monochrome layer is supplied for themed icons.
- Round icon references stay consistent with the primary launcher icon.
- API 24–25 legacy launcher assets use vector mipmap resources instead of density-specific raster copies.
- Existing density-specific WebP launcher files remain removed to avoid duplicate legacy resource definitions.

No manifest change is required because it already references `@mipmap/ic_launcher` and `@mipmap/ic_launcher_round`.
