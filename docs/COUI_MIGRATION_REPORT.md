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

- `app/build.gradle.kts`: replaced MIUIX artifacts with `coui-ui-android`, `coui-blur-android`, `coui-preference-android`, `coui-squircle-android`, and added `navigation3-ui` (previously supplied transitively by the MIUIX navigation adapter). `minSdk` is now 33.
- About 190 Kotlin files had MIUIX imports; 211 Kotlin files were rewritten in total (imports, `Miuix*` symbols, color slots, text styles).
- `ui/theme/Theme.kt`: theme now uses `COUITheme`; removed the global transparent `LocalIndication` override so COUI's press/hover/focus feedback is active.
- `ui/theme/AppearanceColors.kt`: rebuilt on `io.github.suqi8.coui.kmp.theme.Colors`. Monet/system seed, custom accent, pure black, card/background levels and label opacity are preserved through COUI roles.
- `app/src/main/AndroidManifest.xml`: removed `tools:overrideLibrary="top.yukonga.miuix.kmp.blur"`. No SDK override is used.
- `THIRD_PARTY_NOTICES.md` and `app/src/main/assets/legal/NOTICES.md`: MIUIX attribution replaced with COUI 2.0 attribution.

## 3. Blur and compatibility

`coui-blur-android:2.0.0` is now used directly and the application minimum SDK is 33. The former `coui-blur-compat` module and its manifest/dependency wiring were removed. The old `tools:overrideLibrary` entry is also absent.

## 4. Preserved custom components

These are CurrentMusic product features rather than generic look-alikes, so they were kept and now consume COUI colors/text styles:

- Liquid glass: `GlassMaterial`, `GlassMenu`, `GlassButtons`, `GlassSwitch`, `GlassSlider`, `PageGlassScene`, `CombinedBackdrop`.
- Custom settings chrome: `SettingsPage`, `SettingsTopBar`, `SettingsPreference`, `SettingsGroup` (COUI typography, colors, press feedback and spacing).
- Independent search sheet, player/lyrics visuals, launch animations, playlist ribbon/shelf and update page visuals.

They were not replaced with COUI `TopAppBar` / `PreferenceCategory` / `CardListGroup` because doing so would remove the floating-glass interaction model that defines these screens. Settings data, DataStore keys and ViewModel state were not changed.

## 5. Build and test results

Environment: Windows, JDK 21.0.10, AGP 8.13.2, Kotlin 2.4.0, compileSdk 37.

| Task | Result |
| --- | --- |
| `:app:compileDebugKotlin` | PASS |
| `:app:compileReleaseKotlin` | PASS |
| `:app:testDebugUnitTest` | PASS |
| `:app:lintRelease` | PASS (HTML report generated) |
| `:app:minifyReleaseWithR8` | PASS |
| `:app:assembleDebug` | PASS when the local CMake task is excluded, as in the existing verification workflow |
| `:app:assembleRelease` | BLOCKED by the same CMake environment failure |

The verification debug APK reports `minSdkVersion 33`, `targetSdkVersion 36`, and contains official COUI blur classes with zero `top.yukonga.miuix` classes.

### Native build blocker

Every `configureCMakeDebug[abi]` task aborts with Windows exit code `-1073740791` immediately after "The CXX compiler identification is Clang 21.0.0". This is not caused by the migration:

- It reproduces on a fresh ASCII-path project with a two-line `CMakeLists.txt`.
- It reproduces with the bundled CMake 4.1.2, a portable CMake 3.31.6 and CMake 3.22.1.
- NDK 30 clang and lld compile/link the same probe file successfully when invoked directly.

Therefore the final signed APK/AAB still has to be produced on a working CMake host or CI.

## 6. Device verification

A verification build (`-PisolatedDebug=true`, package `com.bileizhen.currentmusic.verification`) was installed on the connected device (CMake tasks excluded for the reason above). Verified visually:

- Light home page with search capsule, recommendation cards and floating dock.
- Settings home rebuilt with `PreferenceCategory`, `Card`, `CardListGroup` and `ArrowPreference`, including COUI row dividers and chevrons.
- Desktop-lyrics subpage inheriting the shared COUI `Card` group container.
- Home and library section headings using COUI `PreferenceCategory`.
- Settings toggles using `SwitchPreference` on the interface-effects card.
- Shared settings section headers using `PreferenceCategory` and row dividers using `HorizontalDivider`.
- Appearance page, light and dark themes.
- Custom color sheet (`OverlayBottomSheet`) with preview, HEX field and color grid.
- Pure dark palette switches correctly and keeps contrast.

The verification package is intentionally left installed until the user confirms the visual result.

Not verified: player/lyrics animation, audio output, Bluetooth/notification controls, API 26/29/32 devices, landscape/wide layouts, input method behavior, and network/account flows.

## 7. Known risks / follow-up

- The settings home, shared settings cards and home/library section headings are now structurally COUI. Search, player controls and remaining dialogs still need their visual pass.
- A broader visual attempt that placed a COUI `TopAppBar` inside the existing floating-header backdrop host produced a native RenderThread stack overflow. That host keeps its custom glass toolbar until a safe COUI integration is designed.
- Official COUI blur is now exercised on the connected Android 17 device. Additional API 33+ devices remain unverified.
- No performance comparison was possible without a full native build and release APK.
- `docs/` is ignored by the repository; this report must be force-added.

## 8. Suggested PR

Title: `refactor(ui): migrate CurrentMusic to COUI 17`

The PR should be opened by the user after reviewing the report and completing device acceptance.
