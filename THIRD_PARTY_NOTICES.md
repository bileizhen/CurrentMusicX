# Third-party notices

## Native NetEase task center references

The native Kotlin task center uses the existing NetEase transport. Feature organization was informed by [ncmm](https://github.com/3899/ncmm) (MIT); task endpoint and response field documentation was checked against [NeteaseCloudMusicApiEnhanced](https://github.com/NeteaseCloudMusicApiEnhanced/api-enhanced) and [chaunsin/netease-cloud-music](https://github.com/chaunsin/netease-cloud-music) (MIT). No Go executable, WebUI, multi-account playback automation, or simulated playback implementation is embedded.

This template uses the following libraries at runtime:

- MIUIX KMP by Yukonga and contributors (`top.yukonga.miuix.kmp`), Apache-2.0.
- AndroidX / Jetpack Compose, under the Apache License 2.0.
- AndroidX WorkManager provides persistent, cancellable song-download tasks (Apache-2.0).
- Jaudiotagger 3.0.1 by JThink / Paul Taylor, Raphael Slinckx and contributors is used under LGPL-2.1-or-later (the LGPL-3.0 option). Unmodified source: https://repo.maven.apache.org/maven2/net/jthink/jaudiotagger/3.0.1/jaudiotagger-3.0.1-sources.jar. The complete LGPL text is bundled in `legal/LGPL-3.0.txt`. The public application's Gradle source and build instructions allow rebuilding with a modified/replacement library; no library modifications are distributed. The Android artwork adapter is in `core/download/SongDownloadMetadata.kt`.
- Kotlin and kotlinx.coroutines by JetBrains and contributors, Apache-2.0.
- Material Icons by Google, Apache-2.0.
- Player controls use Google's Material Symbols Rounded vectors, Apache-2.0, from
  https://github.com/google/material-design-icons/tree/737e3324305806514d7909874fa1818ae1808232/symbols/android.
  Only the selected 24px XML assets are bundled; their source URLs remain in each resource header.
  The theme-attribute tint is removed so Compose can apply the player's animated tint.
- Coil (including animated image decoders) by Coil contributors, Apache-2.0: https://github.com/coil-kt/coil.
- ZXing QR encoder by ZXing authors, Apache-2.0: https://github.com/zxing/zxing. Copyright 2008 ZXing authors. Used for native login QR generation; the Apache-2.0 license is available offline with the notices.

## Floating Bottom Bar and Liquid Glass

`FloatingBottomBar.kt`, `liquid/{CombinedBackdrop,InnerShadow,Lens,Vibrancy}.kt`, `miuix/animation/{DampedDragAnimation,InteractiveHighlight}.kt` and `miuix/modifier/DragGestureInspector.kt` were ported from the user's local 123PanX / LeiFetch implementation. Package names were adapted for this template; the original attribution headers remain in each file.

The source chain runs through LeiFetch / XBlocker to SukiSU-Ultra v4.1.3 (`0ca744a`), GPL-3.0. The underlying liquid glass examples are derived from compose-miuix-ui/miuix and Kyant0/AndroidLiquidGlass, Apache-2.0. Original copyrights and licenses continue to apply; the combined template remains GPL-3.0-only.

- LeiFetch: https://github.com/bileizhen/LeiFetch
- SukiSU-Ultra: https://github.com/SukiSU-Ultra/SukiSU-Ultra/tree/v4.1.3
- MIUIX examples: https://github.com/compose-miuix-ui/miuix
- Liquid glass helpers: https://github.com/Kyant0/AndroidLiquidGlass

The quantized sensor-light adapter was adapted from 123PanX's `ui/util/TiltLightDirection.kt`, GPL-3.0. It avoids recomposition for insignificant sensor changes.

The Gradle Wrapper is distributed under Apache-2.0. Its generated launch scripts retain their upstream copyright notices.

Full GPL-3.0 and Apache-2.0 texts are packaged in `app/src/main/assets/legal/` and accessible offline from the About page. APK recipients can obtain the corresponding application source and build instructions from the configured GitHub project link.

## Shared settings, About, diagnostic and update UI

The saveable page stack, MIUIX NavDisplay navigation and non-predictive NavigationBackHandler fallback in `ui/CurrentMusicApp.kt` are adapted from local XBlocker `ui/MainActivity.kt`, whose navigation pattern derives from SukiSU-Ultra v4.1.3 (`0ca744a`), GPL-3.0-only. MIUIX owns gesture seeking, cancellation, settling, corner clipping and dimming. Dialogs are hosted once outside navigation entries and retain back-event priority.

The phone theme preview, icon paths, appearance layout, LeiFetch About logo/fade layout, grouped member layout, QQ avatar rows, member dialogs, AnimatedList entry motion and blurred bars, animated About background shaders, bottom dialogs and update Markdown renderer are adapted from the local 123PanX / LeiFetch / XBlocker / SukiSU-Ultra source chain. GPL-3.0-only attribution headers are retained. CurrentMusic branding and product configuration replace application-specific data.

The cancellable HTTPS download and verification helper is adapted from 123PanX `UpdateDownloader.kt`, originally XBlocker `data/AppUpdates.kt`. OkHttp and Okio are used under Apache-2.0. The upstream helper's MIT permission follows:

MIT License

Copyright (c) 2026 XBlocker contributors

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

## CurrentMusic legacy behavior and API contracts

MIT License

Copyright (c) 2026 Rcst20

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.


## LXGW WenKai lyric font

Unmodified LXGW WenKai Regular and Medium v1.522 by LXGW and The Klee Project Authors.
Source: https://github.com/lxgw/LxgwWenKai/releases/tag/v1.522
Bundled offline for lyric original text, translation and romanization under SIL OFL 1.1.
Regular SHA-256: 39ad71264b588165b469e35e6afb162a378dacd1f95348160240ba9038ac3009
Medium SHA-256: d4bdeb38a39151d74d084cba5090f8cb7d20bf83eedb78c35939ae70b9f4e3f6

Copyright 2021-2026 LXGW (https://github.com/lxgw/LxgwWenKai)
Copyright 2020 The Klee Project Authors (https://github.com/fontworks-fonts/Klee)

This Font Software is licensed under the SIL Open Font License, Version 1.1.
This license is copied below, and is also available with a FAQ at:
https://openfontlicense.org


-----------------------------------------------------------
SIL OPEN FONT LICENSE Version 1.1 - 26 February 2007
-----------------------------------------------------------

PREAMBLE
The goals of the Open Font License (OFL) are to stimulate worldwide
development of collaborative font projects, to support the font creation
efforts of academic and linguistic communities, and to provide a free and
open framework in which fonts may be shared and improved in partnership
with others.

The OFL allows the licensed fonts to be used, studied, modified and
redistributed freely as long as they are not sold by themselves. The
fonts, including any derivative works, can be bundled, embedded,
redistributed and/or sold with any software provided that any reserved
names are not used by derivative works. The fonts and derivatives,
however, cannot be released under any other type of license. The
requirement for fonts to remain under this license does not apply
to any document created using the fonts or their derivatives.

DEFINITIONS
"Font Software" refers to the set of files released by the Copyright
Holder(s) under this license and clearly marked as such. This may
include source files, build scripts and documentation.

"Reserved Font Name" refers to any names specified as such after the
copyright statement(s).

"Original Version" refers to the collection of Font Software components as
distributed by the Copyright Holder(s).

"Modified Version" refers to any derivative made by adding to, deleting,
or substituting -- in part or in whole -- any of the components of the
Original Version, by changing formats or by porting the Font Software to a
new environment.

"Author" refers to any designer, engineer, programmer, technical
writer or other person who contributed to the Font Software.

PERMISSION & CONDITIONS
Permission is hereby granted, free of charge, to any person obtaining
a copy of the Font Software, to use, study, copy, merge, embed, modify,
redistribute, and sell modified and unmodified copies of the Font
Software, subject to the following conditions:

1) Neither the Font Software nor any of its individual components,
in Original or Modified Versions, may be sold by itself.

2) Original or Modified Versions of the Font Software may be bundled,
redistributed and/or sold with any software, provided that each copy
contains the above copyright notice and this license. These can be
included either as stand-alone text files, human-readable headers or
in the appropriate machine-readable metadata fields within text or
binary files as long as those fields can be easily viewed by the user.

3) No Modified Version of the Font Software may use the Reserved Font
Name(s) unless explicit written permission is granted by the corresponding
Copyright Holder. This restriction only applies to the primary font name as
presented to the users.

4) The name(s) of the Copyright Holder(s) or the Author(s) of the Font
Software shall not be used to promote, endorse or advertise any
Modified Version, except to acknowledge the contribution(s) of the
Copyright Holder(s) and the Author(s) or with their explicit written
permission.

5) The Font Software, modified or unmodified, in part or in whole,
must be distributed entirely under this license, and must not be
distributed under any other license. The requirement for fonts to
remain under this license does not apply to any document created
using the Font Software.

TERMINATION
This license becomes null and void if any of the above conditions are
not met.

DISCLAIMER
THE FONT SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO ANY WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT
OF COPYRIGHT, PATENT, TRADEMARK, OR OTHER RIGHT. IN NO EVENT SHALL THE
COPYRIGHT HOLDER BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
INCLUDING ANY GENERAL, SPECIAL, INDIRECT, INCIDENTAL, OR CONSEQUENTIAL
DAMAGES, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
FROM, OUT OF THE USE OR INABILITY TO USE THE FONT SOFTWARE OR FROM
OTHER DEALINGS IN THE FONT SOFTWARE.


## AMLL TTML DB lyric data source

The native lyrics engine fetches individual TTML files from AMLL TTML DB,
https://github.com/amll-dev/amll-ttml-db (repository data license: CC0-1.0).
The database is not bundled in the app. Song platform ids, lyric author metadata,
translations, romanization and vocal agents are preserved when parsing.
No AMLL Web Renderer code is included.


## Native NetEase request protocol

The Kotlin web/desktop request encoding and endpoint mappings are adapted from
NeteaseCloudMusicApiEnhanced/api-enhanced, https://github.com/NeteaseCloudMusicApiEnhanced/api-enhanced.
This is a native implementation; the Node.js service is not bundled or required.
Original copyright and MIT license:

The MIT License (MIT)

Copyright (c) 2013-2022 Binaryify

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.


## Rhino 1.8.1

The local audio-parameter codecs run in Mozilla Rhino 1.8.1,
https://github.com/mozilla/rhino (Mozilla Public License 2.0).
Rhino is used without browser or Java object access. The MPL 2.0 text is included
in the offline notices as MPL-2.0.txt. Rhino source is available at the link above.

## NetEase sound-effect interoperability resources

The native sound-effect page uses NetEase Music 9.5.30 reference artwork and
local audio-parameter algorithms. NetEase retains the rights to its resources.
Artwork source URLs and hashes are recorded in the bundled
netease-effects-ui/resource-manifest.json; the two local parameter codecs are
hash-verified before execution. These resources do not imply endorsement.
The release bundles the version-bound arm64 NetEase 9.5.30 audio engine and
MNN dependencies; their SHA-256 hashes are listed in the bundled
original-effects/engine-manifest.json. No user account data is bundled.
Member functions require the current account's actual entitlement.


## Folia Monet lyric motion and transport fade (Android port)

Copyright chthollyphile and Folia contributors. Source: https://github.com/chthollyphile/folia-major/tree/26c57f9b1b26b402a09e8943171a332215418ce6/src/components/visualizer/monet

`MonetLyricMotion.kt`, `MonetLyricsRail.kt`, `MonetSweepInk.kt`, and `MonetHaloInk.kt` adapt the Monet rail's
fixed transform tracks, spring constants, line tone, measured grapheme sweep, feathered
mask and absolute-media-time glow from `monetLyricMotion.ts`, `monetRailLayout.ts`,
`MonetLyricsRail.tsx`, `MonetRailLine.tsx`, `MonetRailTokens.tsx`, `monetLyricsModel.ts`,
and `utils/lyrics/graphemeTiming.ts`. React/Framer Motion and CSS masks are replaced by
Compose layers and cached Android raster shaders; native lyric sources, duet/background
vocals, translation preferences, seeking, and Android accessibility remain supported.

`core/media/PlaybackPauseFade.kt` also adapts the 200 ms transport envelope and stale-pause
cancellation behavior from `src/services/playbackFade.ts` and
`src/components/app/playback/bridges/usePlaybackTransportController.ts` at the same commit.
Android coroutines and independently multiplied ExoPlayer deck gains replace Web Audio gain
nodes. Focus/noisy-route safety pauses remain immediate; room commands are acknowledged
without waiting for the local fade.

The port and its adaptations are AGPL-3.0-only. The full AGPL text is bundled offline at
`legal/AGPL-3.0.txt`. Existing GPL-3.0 code retains its license; this combined work is
conveyed under the combination provisions of GPLv3/AGPLv3 section 13. The earlier
statement about the template's GPL-only license describes the original template, not
an exemption for these AGPL-covered components. Corresponding application source and
build instructions are available through the application's configured GitHub source link.


## Folia per-song lyric management, matching and synchronization (Android port)

Copyright chthollyphile and Folia contributors. Reference version:
https://github.com/chthollyphile/folia-major/tree/aecd330b720eb8fc728e6b10ebcec261b144d683

`SongLyricsStore.kt`, `LyricsFileCodec.kt`, `OnlineLyricsMatcher.kt` and
`PlayerLyricsManagementDialog.kt` adapt the per-song imported/online source priority,
independent offset memory, explicit candidate matching, original-document export and
Folia lyric-data v1 interchange described in `onlineLyricsState.ts`,
`lyrics/{lyricOffsetMemory,autoMatchBestLyric,foliaLyricDocument,enhancedLrcSerializer,lyricFileDecoding}.ts`,
`modal/lyric-match/onlineSongMatch.ts` and the AMLL/Kugou lyric providers. Kotlin coroutines,
atomic private app files and Android's document picker replace the browser persistence and file APIs.

The synchronized lyric clock retains pre-roll time for delayed cues, rejects seeks at or beyond
an audio track's end, and separates different platform/library identities. Monet's display-only
punctuation and space timing follows `utils/lyrics/graphemeTiming.ts`; source documents and
exported word timing remain unmodified. These adaptations are AGPL-3.0-only, subject to the
same source-availability and license-combination provisions above. The full AGPL text is
available offline in `legal/AGPL-3.0.txt`. Lyric content is fetched on user request; no reference
song lyrics or audio are bundled with this port.

## QQ Music and Qishui public lyric matching

`QqLyricsMatchSource.kt` uses QQ Music's public web catalogue and LRC endpoints.
It retains real line timing and optional auxiliary lyric tracks; no encrypted QRC
or estimated word timing is claimed. Its integration follows the Folia source
registry credited above, with the public web endpoint adaptation implemented in Kotlin.

`QishuiLyricsMatchSource.kt` adapts the anonymous client-token, public search and
lyric-metadata portions of [qishui-music-api 0.2.2](https://github.com/GSQZ/qishui-music-api/tree/924add376cadf8a2268f11999051eb976e3b8e0d),
copyright (c) 2026 sayqz, MIT. No Node.js runtime, account session or audio decryption
component is bundled. The original MIT notice and source archive checksum are
provided offline in `legal/QISHUI-MIT.txt`. Folia's Qishui lyric normalization remains
covered by the AGPL attribution above.

Automatic matching obtains NetEase lyrics as a content and timing reference, then
compares candidates from every configured source. Ordered text agreement, timeline
compatibility, genuine word-cue coverage and completeness determine the winner;
source order does not. CurrentMusic matching explicitly uses the configured project
server, while NetEase matching uses the native official gateway.
The selected recording and audio metadata are never replaced by a lyric match.

## CurrentMusic halo startup wordmark

The fixed CurrentMusic vector lettering in `current_music_wordmark.json` was
produced using the RoG Sans lettering and -0.4 shear of the user-provided
BlueArchive-style generator: https://symbolon.pages.dev/ . Generator source:
https://github.com/nulla2011/Bluearchive-logo (MIT, copyright 2023 nulla).
The application bundles only this fixed wordmark artwork, not the font files.
Font names and their respective rights remain with their original owners.
The ring, star, particles and animation timeline are rendered with native Canvas.
Ring motion keyframes were measured from the user-supplied animation reference;
the reference video and its audio are not bundled. Usernames are rendered locally
using Android fonts and are never sent to the generator.

## Oboe 1.10.0

Copyright The Android Open Source Project. Apache License 2.0.
https://github.com/google/oboe
Source revision: a81bb9f87d4105b84b682685d3bfbb5beca371d1.
Used for OpenSL ES / AAudio / Oboe audio output. Compatibility declarations and enum ABI checks in AAudioLoader are adjusted for NDK 30; the original copyright notices are retained. The full license is included in legal/APACHE-2.0.txt.

## Lucide Icons 0.468.0

https://github.com/lucide-icons/lucide/tree/0.468.0

The audio output settings use audio-lines, waves, activity, cable, guitar and binary,
converted from the original SVGs to Android Vector Drawables with 24dp bounds
and 2-unit rounded strokes. The original copyright and ISC license are included
in legal/LUCIDE-LICENSE.txt.
