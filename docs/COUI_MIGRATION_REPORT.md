# CurrentMusicX COUI 17 Migration Report

## 1. Before and after

| Area | Before | After |
| --- | --- | --- |
| UI toolkit | MIUIX KMP 0.9.3 (`top.yukonga.miuix.kmp`) | COUI 2.1.0 (`io.github.suqi8.coui.kmp`) |
| Design language | HyperOS / Miuix | ColorOS 17 / COUI 17 |
| Theme entry | `LeiTheme` -> `MiuixTheme(colors, textStyles)` | `LeiTheme` -> `COUITheme(colors, textStyles)` |
| Color roles | Miuix role names (`onSurface`, `onSurfaceVariantSummary`, `surfaceContainer`, ...) | COUI 17 roles (`labelPrimary`, `labelSecondaryVariant`, `cardBackground`, ...) |
| Text roles | `main`, `body1/2`, `footnote1/2`, `title1..4` | `bodyXL/L/M/S/XS`, `headlineS/XS`, `displayS/XS`, ... |
| Material | MIUIX blur + CurrentMusic liquid glass | Official COUI 2.1 blur + CurrentMusic liquid glass |
| Navigation | Navigation 3 runtime + MIUIX navigation3 adapter | COUI 2.1 navigation runtime (`coui-nav`) with COUI page transitions |
| minSdk | 26 | 33 |

## 2. Main changes

- `app/build.gradle.kts`: replaced MIUIX artifacts with `coui-ui-android`, `coui-blur-android`, `coui-icons-android`, `coui-preference-android`, `coui-squircle-android`, and `coui-nav-android` at 2.1.0. `minSdk` is now 33.
- About 190 Kotlin files had MIUIX imports; 211 Kotlin files were rewritten in total (imports, `Miuix*` symbols, color slots, text styles).
- `ui/theme/Theme.kt`: theme now uses `COUITheme`; removed the global transparent `LocalIndication` override so COUI's press/hover/focus feedback is active.
- `ui/theme/AppearanceColors.kt`: rebuilt on `io.github.suqi8.coui.kmp.theme.Colors`. Monet/system seed, custom accent, pure black, card/background levels and label opacity are preserved through COUI roles.
- `app/src/main/AndroidManifest.xml`: removed `tools:overrideLibrary="top.yukonga.miuix.kmp.blur"`. No SDK override is used.
- `SettingsScreen`: rebuilt as a dedicated COUI `Scaffold` with a 116dp/40sp collapsible SettingsHome header, in-place `TopAppBarSearchBar`, `topAppBarSearchContent`/`topAppBarSearchResults`, `PreferenceCategory`, `Card`, `CardListGroup`, `ArrowPreference`, and `COUIIcons`. The previous floating-header host is no longer used for the settings home.
- Settings account row now shows the signed-in CurrentMusic account avatar and nickname, with the app-specific summary `管理资料、收藏与听歌记录`; tapping it opens the existing profile tab.
- Settings neutrals now use the ColorOS palette instead of the wallpaper-derived hue: light `#F2F5F7` pages with white cards, dark near-black/pure-black pages with `#1B1C1D` cards, neutral dividers and labels. The user's accent color is retained for controls.
- Settings entry icons use COUI semantic colors (blue, orange, green, mint, violet, yellow, red) while remaining the official `COUIIcons` set.
- Settings home cards use flat ColorOS card surfaces and omit the former `个性化` / `音乐` / `应用` group captions, matching the system Settings card stack without an artificial outline or shadow line.
- Settings surfaces now use the official `coui-squircle` `squircleSurface` with ColorOS weights (2.5 for cards, 1.0 for capsules) instead of circular `RoundedCornerShape` corners, covering the settings home, shared settings groups, appearance preview, color palette/sheet, adaptive EQ and sound-effect tiles.
- Settings entry rows show only their title, semantic icon and chevron; the previous description lines were removed to match the system Settings list density.
- Font settings were rebuilt as a fixed, non-scrolling ColorOS-style page: three built-in choices (`系统默认`, `OPPO Sans`, `霞鹜文楷`), retained font import, no search field, a fixed 300dp bottom panel and a 22sp/14sp preview hierarchy. OPPO Sans 4.0 is bundled from the user-provided official archive, with its license recorded in `THIRD_PARTY_NOTICES.md` and `app/src/main/assets/legal/`.
- The software update page keeps its product-specific `UpdateGlassMenu` liquid-glass overflow menu and download-source badges. The COUI top bar and `IconButton`s remain; the menu intentionally does not use `OverlayCascadingListPopup`.
- Settings subpages now share a COUI-native `Scaffold` + `SmallTopAppBar` shell. Playback, appearance, network sources, storage, equalizer, audio output, feedback, desktop lyrics, adaptive audio, NetEase effects and sound effects no longer use the old floating glass header.
- Shared settings sections now use COUI `PreferenceCategory` headers, clickable `SettingsPreference` rows get the native trailing arrow, and option rows use `OverlaySpinnerPreference` instead of the custom glass picker.
- `THIRD_PARTY_NOTICES.md` and `app/src/main/assets/legal/NOTICES.md`: MIUIX attribution replaced with COUI 2.0 attribution.

### Migration vs visual status

| Area | API migration | Visual redesign |
| --- | --- | --- |
| Settings home / search / account card | Complete | Complete ColorOS 17 structure |
| Settings subpages / shared cards | Complete | Complete native top bar, category headers, cards, arrows and spinner popups; product-specific quality sheet retained |
| Font settings | Complete | Complete fixed-layout redesign |
| Home / discover / library / profile | Complete | Section headings and cards migrated; player-specific visuals intentionally retained |
| Software update page | Complete | COUI top bar and buttons; product-specific liquid-glass menu restored |
| Player / lyrics / liquid glass | Complete | Product-specific visuals preserved, not replaced by system templates |
| Dialogs / sheets / popups | Complete at API level | Remaining custom glass surfaces still need a page-by-page visual pass |

## 3. Blur and compatibility

`coui-blur-android:2.1.0` is now used directly and the application minimum SDK is 33. The former `coui-blur-compat` module and its manifest/dependency wiring were removed. The old `tools:overrideLibrary` entry is also absent.

The settings page records its scrolling content with `rememberLayerBackdrop` and passes it to COUI's `TopAppBarGlow`. The scroll-driven condensed-light overlay provides the ColorOS 17 progressive blur, 30dp alpha threshold and 24dp fade strip below the bar. A dedicated collapsing header uses the 116dp/40sp SettingsHome metrics, but removes the generic `TopAppBar` 52dp collapsed toolbar slot: the title height reaches zero and the pinned search bar sits directly below the status bar, matching the system Settings page without an empty band. Without runtime shader support, `TopAppBarGlow` falls back to the opaque page surface.

## 4. Preserved custom components

These are CurrentMusic product features rather than generic look-alikes, so they were kept and now consume COUI colors/text styles:

- Liquid glass: `GlassMaterial`, `GlassMenu`, `GlassButtons`, `GlassSwitch`, `GlassSlider`, `PageGlassScene`, `CombinedBackdrop`.
- `SettingsTopBar` / `SettingsScrollLayout` remain for non-settings utility pages such as legal documents and account forms; settings subpages use the COUI-native shell. `SettingsPreference` and `SettingsGroup` remain as thin adapters over COUI colors, cards, arrows and press feedback.
- Independent search sheet, player/lyrics visuals, launch animations, playlist ribbon/shelf, the update page's `UpdateGlassMenu` and its cover-derived color extraction/animation.

The settings home is the exception: it now uses the official COUI page/search/preference structure. Other settings subpages keep their shared custom chrome while their rows already use `Card`, `SwitchPreference`, `PreferenceCategory` and COUI dividers. Settings data, DataStore keys and ViewModel state were not changed.

## 5. Build and test results

Environment: Windows, JDK 21.0.10, AGP 8.13.2, Kotlin 2.4.0, compileSdk 37.

| Task | Result |
| --- | --- |
| `:app:compileDebugKotlin` | PASS |
| `:app:compileReleaseKotlin` | PASS |
| `:app:testDebugUnitTest` | PASS (no local test sources in this checkout) |
| `:app:lintRelease` | PASS (HTML report generated) |
| `:app:minifyReleaseWithR8` | PASS |
| `:app:assembleDebug` | PASS when the local CMake task is excluded, as in the existing verification workflow |
| `:app:assembleRelease` | PASS with the local CMake tasks excluded; signed APK assembled from the checked-in native libraries |

The debug and signed release APKs report `minSdkVersion 33`, `targetSdkVersion 36`, and contain official COUI blur classes. Navigation also uses COUI's own 2.1.0 runtime; no MIUIX navigation artifact remains on the runtime classpath.

After the settings, font and update-menu visual changes, `:app:compileDebugKotlin`, `:app:assembleDebug`, `:app:assembleRelease`, `:app:lintRelease`, `:app:minifyReleaseWithR8` and `:app:testDebugUnitTest` were rerun and passed. `:app:testDebugUnitTest` remains `NO-SOURCE` in this checkout.

### Native build blocker

Every `configureCMakeDebug[abi]` task aborts with Windows exit code `-1073740791` immediately after "The CXX compiler identification is Clang 21.0.0". This is not caused by the migration:

- It reproduces on a fresh ASCII-path project with a two-line `CMakeLists.txt`.
- It reproduces with the bundled CMake 4.1.2, a portable CMake 3.31.6 and CMake 3.22.1.
- NDK 30 clang and lld compile/link the same probe file successfully when invoked directly.

The signed APK can still be assembled locally when the CMake tasks are excluded because the required native libraries are committed under `jniLibs`. A clean native rebuild/AAB still needs a working CMake host or CI.

## 5.1 Predictive back follows SukiSU-Nova

Ordinary pages now use COUI 2.1.0's `coui-nav-android` navigation runtime: one `NavDisplay` owns the route stack and the library's predictive-back handler owns gesture progress, velocity, commit and cancel. This replaces the previous AndroidX Navigation3 host plus page-specific return animator, which had competing back handlers and never reliably delivered the system gesture to the page animation.

The `预测性返回手势` preference also follows Nova's Android 14+ application opt-in: `ApplicationInfo.setEnableOnBackInvokedCallback` is invoked through `HiddenApiBypass`, and changing the preference recreates the activity so Android installs the matching callback policy. COUI's `COUIDefault` transition drives the page and underlying page together; no custom container shrink is used for normal page navigation.

The COUI screen components, settings UI and player/update overlays remain in CurrentMusic. Custom fullscreen update and search overlays retain their existing ownership and back handling outside the navigation stack.

On the isolated verification package on RMX5060 (ColorOS 17), the final COUI 2.1 build was recorded with full-page horizontal movement before release and only one pop on commit. Left- and right-edge commit and gesture cancellation were checked on Settings/Appearance. The preceding verification build also covered returning from Font through Appearance to Settings and switching prediction off and back on, including ordinary back while prediction was disabled. Release assembly and lint passed; unit tests had no sources. The production package was not replaced for this change.

## 6. Device verification

The signed release (`com.bileizhen.currentmusic`, certificate SHA-256 `FC:3E:63:07:9B:8E:69:AE:A7:AB:73:48:28:46:DA:09:39:D6:CD:12:DA:F0:AF:FF:AA:9A:2E:93:FB:FC:CF:35`) was installed over the user's existing package with `adb install -r`. Login state, avatar, username, mini-player state and account data were preserved. The isolated verification package remains installed for regression checks. Verified visually:

- Light home page with search capsule, recommendation cards and floating dock.
- Settings home rebuilt with a COUI `Scaffold`, collapsible large title, in-place search and native preference cards.
- Scrolled settings use `TopAppBarGlow` progressive blur with the large title fully collapsed and the search bar directly below the status bar, matching the ColorOS 17 Settings material band without a 52dp empty toolbar.
- Account card shows the real `bileizhen` avatar and nickname; tapping it opens `我的`.
- Search opens from the top bar, fades the settings content, filters rows in a separate results list, and cancels back to the unchanged page.
- Settings page light and dark palettes, pinned search bar and scrolled/collapsed state.
- Software update page now uses COUI `SmallTopAppBar`, COUI primary/secondary capsule `Button`s and a COUI glass material status card while retaining the current song's extracted background palette.
- Desktop-lyrics subpage inheriting the shared COUI `Card` group container.
- Home and library section headings using COUI `PreferenceCategory`.
- Settings toggles using `SwitchPreference` on the interface-effects card.
- Shared settings section headers using `PreferenceCategory` and row dividers using `HorizontalDivider`.
- Appearance page, light and dark themes.
- Custom color sheet (`OverlayBottomSheet`) with preview, HEX field and color grid.
- Pure dark palette switches correctly and keeps contrast.
- Font page in light and dark themes: fixed preview, three built-in fonts, import action, fixed 300dp bottom panel and no page scrolling.
- Update page: COUI top bar with the restored `UpdateGlassMenu` overflow menu, nested update settings/download source entries, source-health badges and retained cover-derived background.
- Playback settings: native COUI top bar, compact section headers, cards, trailing arrows and compact text hierarchy.
- Appearance settings: native top bar, compact `主题模式` / `纯黑模式` headers, preview card and native option rows.
- Audio output settings: native top bar and a COUI `OverlaySpinnerPreference` popup for the output sample rate; selecting an option updates the row and closes the popup.
- Adaptive audio, NetEase effects package and sound effects pages: native top bars and cards, with the existing audio controls preserved.

Both the updated production package and the verification package are intentionally left installed until the user confirms the visual result.

Not verified: player/lyrics animation, audio output, Bluetooth/notification controls, API 33+ devices other than the connected ColorOS 17 device, landscape/wide layouts, and account mutations beyond opening the profile tab.

## 7. Known risks / follow-up

- The settings home, search, account card, shared settings cards and home/library section headings are now structurally COUI. Player controls and remaining dialogs still need their visual pass.
- A previous attempt that placed a COUI `TopAppBar` inside the existing floating-header backdrop host produced a native RenderThread stack overflow. The settings home now owns a separate COUI `Scaffold`; repeated open/search/cancel/account/back navigation on the production build produced no fatal exception or RenderThread recursion.
- Official COUI blur is now exercised on the connected Android 17 device. Additional API 33+ devices remain unverified.
- No performance comparison was measured; the visual pass only checks that page navigation and search remain responsive on the connected device.
- COUI upstream limitations found during device acceptance are tracked separately; none of them are patched locally:
  - [#14](https://github.com/suqi8/coui/issues/14) vertical popup drag does not trigger deformation
  - [#15](https://github.com/suqi8/coui/issues/15) open cascading menus do not accept live content updates or reordering
  - [#16](https://github.com/suqi8/coui/issues/16) `DropdownItem` has no measured leading/trailing custom-content slot
  - [#17](https://github.com/suqi8/coui/issues/17) cascading submenu width/height cannot be configured independently
  - [#18](https://github.com/suqi8/coui/issues/18) per-item dismiss-on-click is not configurable
  - [#19](https://github.com/suqi8/coui/issues/19) cascading depth is limited to two levels
- Current update-menu behavior: the product-specific glass menu is kept for its nested settings/source flow; source health probes run only while the download-source path is active and sorting is deferred while the menu is being dragged.
- `docs/` is ignored by the repository; this report must be force-added.

## 8. Suggested PR

Title: `refactor(ui): migrate CurrentMusic to COUI 17`

The PR should be opened by the user after reviewing the report and completing device acceptance.
