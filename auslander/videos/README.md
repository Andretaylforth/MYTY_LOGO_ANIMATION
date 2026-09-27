# Auslander project videos (compressed)

AV1 is the primary file (smallest); the MP4 fallback plays everywhere. The fallback is the H.264 encode when it came out smaller than the original, otherwise the original file.
In Framer, put these into the ResponsiveVideo component: the AV1 file in the AV1 slot, the fallback in the MP4 slot.
Audio is kept only where the source had it. Longest side is capped at 1920 px. SSIM compares the AV1 encode with the original (1.0 = identical).

| Video | Original | AV1 | Fallback | SSIM (AV1) |
|---|---|---|---|---|
| 360tiles | 4.4 MB | not smaller, skipped | 4.4 MB (original) | 0.993802 |
| brokenapart-blue | 3.2 MB | 1.3 MB | 2.1 MB (H.264) | 0.992715 |
| brokenapart-blue-mobile | 3.9 MB | 1.8 MB | 3.3 MB (H.264) | 0.992761 |
| claybikeside | 2.6 MB | 0.5 MB | 1.5 MB (H.264) | 0.993463 |
| helmet-switchfinal | 1.6 MB | 0.8 MB | 1.4 MB (H.264) | 0.994279 |
| hydra-header-desktop | 6.7 MB | 3.1 MB | 4.4 MB (H.264) | 0.990017 |
| hydra-header-mobile | 2.8 MB | 2.2 MB | 2.8 MB (original) | 0.994387 |
| insta-vid | 4.9 MB | 1.4 MB | 4.1 MB (H.264) | 0.980305 |
| lamborghini-temerario-carpark-2160x2700 | 1.0 MB | 0.8 MB | 1.0 MB (original) | 0.993190 |
| lamborghini-temerario-carpark-4k | 2.1 MB | 1.5 MB | 2.1 MB (original) | 0.991103 |
| mesh-small | 0.6 MB | 0.4 MB | 0.5 MB (H.264) | 0.992738 |
| rad-01-desktop-header | 11.3 MB | 2.3 MB | 6.4 MB (H.264) | 0.985741 |
| rad-01-mobile-header | 6.8 MB | 2.0 MB | 4.6 MB (H.264) | 0.988258 |
| suit-spin-handbreak | 2.0 MB | 0.9 MB | 1.7 MB (H.264) | 0.995015 |
| untitled-video-made-with-clipchamp-2024-09-14t170215-615 | 2.6 MB | not smaller, skipped | 2.6 MB (original) | 0.978428 |
| untitled-video-made-with-clipchamp-2024-09-15t015704-812 | 1.3 MB | 1.2 MB | 1.3 MB (original) | 0.992833 |
| untitled-video-made-with-clipchamp-2024-12-16t212752-711 | 3.1 MB | not smaller, skipped | 3.1 MB (original) | 0.978065 |
| website-girlface | 2.8 MB | 0.2 MB | 1.1 MB (H.264) | 0.987622 |
| wireframe-short | 6.3 MB | 2.0 MB | 4.2 MB (H.264) | 0.992262 |
| dab-tennis-motorcycle-teaser | 2.3 MB | 1.7 MB | 2.3 MB (original) | 0.990525 |
| dab-tennis-motorcycle | 1.9 MB | 1.6 MB | 1.9 MB (original) | 0.994110 |
| lamborgini-temerario-hurrican-2024 | 2.0 MB | 1.5 MB | 2.0 MB (original) | 0.989495 |
| mobile-reexport | 5.3 MB | 4.2 MB | 5.3 MB (original) | 0.988330 |
| mobile-reexport-desktop | 8.4 MB | 7.8 MB | 8.4 MB (original) | 0.986657 |

Smallest file per video: 89.9 MB before, 49.6 MB after. The homepage hero videos are in ../hero-video/.
