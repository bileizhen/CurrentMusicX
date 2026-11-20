# CurrentMusicX COUI 17 Migration Report

## 1. Before and after

| Area | Before | After |
| --- | --- | --- |
| UI toolkit | MIUIX KMP 0.9.3 (`top.yukonga.miuix.kmp`) | COUI 2.0.0 (`io.github.suqi8.coui.kmp`) |
| Design language | HyperOS / Miuix | ColorOS 17 / COUI 17 |
| Theme entry | `LeiTheme` -> `MiuixTheme(colors, textStyles)` | `LeiTheme` -> `COUITheme(colors, textStyles)` |
| Color roles | Miuix role names (`onSurface`, `onSurfaceVariantSummary`, `surfaceContainer`, ...) | COUI 17 roles (`labelPrimary`, `labelSecondaryVariant`, `cardBackground`, ...) |
| Text roles | `main`, `body1/2`, `footnote1/2`, `title1..4` | `bodyXL/L/M/S/XS`, `headlineS/XS`, `displayS/XS`, ... |
| Material | MIUIX blur + CurrentMusic liquid glass | Official COUI 2.0 blur + CurrentMusic liquid glass |
| Navigation | Navigation 3 runtime + MIUIX navigation3 adapter | Navigation 3 runtime/UI, CurrentMusic transitions preserved |
| minSdk | 26 | 33 |

## 2. Main changes

- `app/build.gradle.kts`: replaced MIUIX artifacts with `coui-ui-android`, `coui-blur-android`, `coui-icons-android`, `coui-preference-android`, `coui-squircle-android`, and added `navigation3-ui` (previously supplied transitively by the MIUIX navigation adapter). `minSdk` is now 33.
- About 190 Kotlin files had MIUIX imports; 211 Kotlin files were rewritten in total (imports, `Miuix*` symbols, color slots, text styles).
- `ui/theme/Theme.kt`: theme now uses `COUITheme`; removed the global transparent `LocalIndication` override so COUI's press/hover/focus feedback is active.
- `ui/theme/AppearanceColors.kt`: rebuilt on `io.github.suqi8.coui.kmp.theme.Colors`. Monet/system seed, custom accent, pure black, card/background levels and label opacity are preserved through COUI roles.
- `app/src/main/AndroidManifest.xml`: removed `tools:overrideLibrary="top.yukonga.miuix.kmp.blur"`. No SDK override is used.
- `SettingsScreen`: rebuilt as a dedicated COUI `Scaffold` with a 116dp/40sp collapsible SettingsHome header, in-place `TopAppBarSearchBar`, `topAppBarSearchContent`/`topAppBarSearchResults`, `PreferenceCategory`, `Card`, `CardListGroup`, `ArrowPreference`, and `COUIIcons`. The previous floating-header host is no longer used for the settings home.
- Settings account row now shows the signed-in CurrentMusic account avatar and nickname, with the ColorOS-style summary `管理账号、云服务、查找`; tapping it opens the existing profile tab.
- Settings neutrals now use the ColorOS palette instead of the wallpaper-derived hue: light `#F2F5F7` pages with white cards, dark near-black/pure-black pages with `#1B1C1D` cards, neutral dividers and labels. The user's accent color is retained for controls.
- Settings entry icons use COUI semantic colors (blue, orange, green, mint, violet, yellow, red) while remaining the official `COUIIcons` set.
- Settings home cards use flat ColorOS card surfaces and omit the former `个性化` / `音乐` / `应用` group captions, matching the system Settings card stack without an artificial outline or shadow line.
- `THIRD_PARTY_NOTICES.md` and `app/src/main/assets/legal/NOTICES.md`: MIUIX attribution replaced with COUI 2.0 attribution.

## 3. Blur and compatibility

`coui-blur-android:2.0.0` is now used directly and the application minimum SDK is 33. The former `coui-blur-compat` module and its manifest/dependency wiring were removed. The old `tools:overrideLibrary` entry is also absent.

The settings page records its scrolling content with `rememberLayerBackdrop` and passes it to COUI's `TopAppBarGlow`. The scroll-driven condensed-light overlay provides the ColorOS 17 progressive blur, 30dp alpha threshold and 24dp fade strip below the bar. A dedicated collapsing header uses the 116dp/40sp SettingsHome metrics, but removes the generic `TopAppBar` 52dp collapsed toolbar slot: the title height reaches zero and the pinned search bar sits directly below the status bar, matching the system Settings page without an empty band. Without runtime shader support, `TopAppBarGlow` falls back to the opaque page surface.

## 4. Preserved custom components

These are CurrentMusic product features rather than generic look-alikes, so they were kept and now consume COUI colors/text styles:

- Liquid glass: `GlassMaterial`, `GlassMenu`, `GlassButtons`, `GlassSwitch`, `GlassSlider`, `PageGlassScene`, `CombinedBackdrop`.
- Custom subpage settings chrome: `SettingsPage`, `SettingsTopBar`, `SettingsPreference`, `SettingsGroup` (COUI typography, colors, press feedback and spacing).
- Independent search sheet, player/lyrics visuals, launch animations, playlist ribbon/shelf and update page visuals.

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

The debug and signed release APKs report `minSdkVersion 33`, `targetSdkVersion 36`, and contain official COUI blur classes with zero `top.yukonga.miuix` classes.

After the settings visual changes, `:app:compileDebugKotlin`, `:app:assembleDebug`, `:app:assembleRelease`, `:app:lintRelease` and `:app:minifyReleaseWithR8` were rerun and passed.

### Native build blocker

Every `configureCMakeDebug[abi]` task aborts with Windows exit code `-1073740791` immediately after "The CXX compiler identification is Clang 21.0.0". This is not caused by the migration:

- It reproduces on a fresh ASCII-path project with a two-line `CMakeLists.txt`.
- It reproduces with the bundled CMake 4.1.2, a portable CMake 3.31.6 and CMake 3.22.1.
- NDK 30 clang and lld compile/link the same probe file successfully when invoked directly.

The signed APK can still be assembled locally when the CMake tasks are excluded because the required native libraries are committed under `jniLibs`. A clean native rebuild/AAB still needs a working CMake host or CI.

## 6. Device verification

The signed release (`com.bileizhen.currentmusic`, certificate SHA-256 `FC:3E:63:07:9B:8E:69:AE:A7:AB:73:48:28:46:DA:09:39:D6:CD:12:DA:F0:AF:FF:AA:9A:2E:93:FB:FC:CF:35`) was installed over the user's existing package with `adb install -r`. Login state, avatar, username, mini-player state and account data were preserved. The isolated verification package remains installed for regression checks. Verified visually:

- Light home page with search capsule, recommendation cards and floating dock.
- Settings home rebuilt with a COUI `Scaffold`, collapsible large title, in-place search and native preference cards.
- Scrolled settings use `TopAppBarGlow` progressive blur with the large title fully collapsed and the search bar directly below the status bar, matching the ColorOS 17 Settings material band without a 52dp empty toolbar.
- Account card shows the real `bileizhen` avatar and nickname; tapping it opens `我的`.
- Search opens from the top bar, fades the settings content, filters rows in a separate results list, and cancels back to the unchanged page.
- Settings page light and dark palettes, pinned search bar and scrolled/collapsed state.
- Desktop-lyrics subpage inheriting the shared COUI `Card` group container.
- Home and library section headings using COUI `PreferenceCategory`.
- Settings toggles using `SwitchPreference` on the interface-effects card.
- Shared settings section headers using `PreferenceCategory` and row dividers using `HorizontalDivider`.
- Appearance page, light and dark themes.
- Custom color sheet (`OverlayBottomSheet`) with preview, HEX field and color grid.
- Pure dark palette switches correctly and keeps contrast.

Both the updated production package and the verification package are intentionally left installed until the user confirms the visual result.

Not verified: player/lyrics animation, audio output, Bluetooth/notification controls, API 33+ devices other than the connected ColorOS 17 device, landscape/wide layouts, and account mutations beyond opening the profile tab.

## 7. Known risks / follow-up

- The settings home, search, account card, shared settings cards and home/library section headings are now structurally COUI. Player controls and remaining dialogs still need their visual pass.
- A previous attempt that placed a COUI `TopAppBar` inside the existing floating-header backdrop host produced a native RenderThread stack overflow. The settings home now owns a separate COUI `Scaffold`; repeated open/search/cancel/account/back navigation on the production build produced no fatal exception or RenderThread recursion.
- Official COUI blur is now exercised on the connected Android 17 device. Additional API 33+ devices remain unverified.
- No performance comparison was measured; the visual pass only checks that page navigation and search remain responsive on the connected device.
- `docs/` is ignored by the repository; this report must be force-added.

## 8. Suggested PR

Title: `refactor(ui): migrate CurrentMusic to COUI 17`

The PR should be opened by the user after reviewing the report and completing device acceptance.
