# Android launcher icon redesign proposal

Base source boundary: `887518646fb66b36b10345fe2187e087457395ae`

This review-only proposal compares four launcher directions and implements only **Option D** in Android resources.

| Option | Direction | Preview |
|---|---|---|
| A | Premium dark / elegant | [Preview](./option_A_preview.svg) |
| B | Savings-focused / financial clarity | [Preview](./option_B_preview.svg) |
| C | AI-tech / modern intelligence | [Preview](./option_C_preview.svg) |
| D | Minimal monogram / strong recognizability | [Preview](./option_D_preview.svg) |

## Implemented default

Option D uses a high-contrast C/S monogram on a midnight background with a restrained mint intelligence spark. It was selected because the silhouette remains identifiable at small launcher sizes, works on light and dark home-screen contexts, and does not depend on text or fine detail.

[Implemented Android context preview](./implemented_option_D_android_home.svg)

## Android resource strategy

- Adaptive icon background and foreground remain separate.
- A dedicated monochrome layer is supplied for themed icons.
- Round icon references stay consistent with the primary launcher icon.
- API 24–25 legacy launcher assets use vector mipmap resources instead of density-specific raster copies.
- Existing density-specific WebP launcher files are removed to avoid duplicate legacy resource definitions.

No manifest change is required because it already references `@mipmap/ic_launcher` and `@mipmap/ic_launcher_round`.
