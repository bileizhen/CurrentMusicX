# Shared native UI controls

Compose adaptations of FlClash `7fd417fb9a9a277c6a9339a8ac87f907df7c25f7` (GPL-3.0).
Source attribution is in `THIRD_PARTY_NOTICES.md` and the bundled legal notices.

- `ElasticPress` owns only paint deformation. Layout and click targets remain fixed.
- List rows follow `DecorationListItem -> CommonCard -> FilledButton`: `settingsPressFeedback`
  translates material_ui 1.5.0 `InkSparkle`/`InkHighlight`, using the original 617ms shader wave,
  touch-to-center motion, 200ms reversible highlight and .10/.08/.10 foreground overlays.
  Palette dots use transparent `Material`/`InkWell`'s separate ThemeData ink colors.
  Release/cancel does not truncate InkSparkle; no additional quick-tap hold is inserted.
  Android 8–12/software rendering retains the wave without the white sparkle shader pass.
- `GlassIconButton` and `GlassActionButton` share press growth, rubber-band drag and material.
- `GlassMenu` owns opening/returning droplets, drag selection, cancellation, dwell and nested levels.
  Supply `GlassMenuItem` data and callbacks; a leaf closes before invoking its callback unless
  `keepOpen` is set. `onEnter` can initialize a draft. `popOnSelect` returns a radio choice to its parent.
  `leadingContent` accepts status badges or other custom leading content. Set `animateSubItems`
  on a parent to animate keyed child rows when their order changes; keep item IDs stable.
  `checkable` renders an independent checked menu item with checkbox accessibility semantics;
  `toggle` renders the glass switch instead. Both use `checked` for their state.
  Nested panes keep their full-size capture and row layout while the painted outline folds.
  The last navigation stack stays cached, with retired panes unplaced and removed from semantics.
- `GlassSwitch` works independently or decoratively (`onCheckedChange=null`) inside a parent row.
  It follows `CommonSwitch`'s separate raw-toggle and painted-position controllers: a 300ms
  directional back curve for taps, direct linear drag/handoff, a 150ms cubic lift, and a 200ms
  tap-lift hold. Owner/row changes lift too. Material's off-thumb outline role maps to the
  theme's muted foreground (Miuix's outline is a dim border);
  lens strength fades with lift, and disabled/glass-off states keep their solid material.
- `GlassSlider` reuses the button's optical material and `ElasticPress` for its thumb, with a
  separate capture of the track for refraction. At rest it retains the original Miuix track,
  rounded fill and opaque 20.16dp knob; only pressing fades in and enlarges the glass lens,
  and releasing fades it back to the original appearance. This is a native extension; FlClash's own
  `SliderDefaultsM3` uses Material track/handle shapes. Horizontal drags preview and save once
  on release, cancellation restores the preview, and vertical swipes stay with the page.
  It supports ranges, steps/key points, RTL, keyboard and accessibility adjustments, disabled
  state and the existing liquid-glass switch. The touch area stays 48dp while the thumb grows.
  The pressed lens is laid out at its actual 40dp size like `GlassSwitch`; it is not a smaller
  captured lens scaled after rendering. The lens has no extra button press-light overlay.
- `SettingsPage`, `SettingsTopBar`, `SettingsGroup`, `SettingsPreference` and `SettingsToggle`
  provide the common settings layout. `SettingsOptionsRow` opens the shared glass menu from its
  value and closes after a selection. `SettingsUiScope` opts existing preference adapters into
  these components without restyling music/library rows elsewhere in the application.
  Place option rows inside `SettingsPage`, which hosts their menus above the scrolling content.
  Pass stable option IDs and save callbacks; rows contain no repositories or feature state.
  Settings pickers pass `menuWidth=null` for FlClash's intrinsic 160–280dp width. Their value
  anchors open on down, carry that pointer into the menu, and keep an unmoved release open.
  The modal barrier covers the settings content so an outside tap cannot edit another row.
  Menu descriptions use 13/18sp and at most two lines. Root menus show at most 7.5 plain rows
  and reveal the selected row when opened. The separate outer rim and the wide 29dp shadow
  (8dp downward, alpha .118 / .177) follow `LiquidGlassRim.menu` and `menuShadows`.
  Hosts can pass `bottomInset` to keep panes clear of floating controls. `SettingsPage`
  supplies its existing mini-player clearance so a low anchor cannot hide the last choice.

`SettingsFloatingHeader` directly ports FlClash's `FloatingHeader` surface fade: .9 alpha,
eight ease-in/out steps, fade start at the status-bar foot and a 16dp overhang. It uses no blur,
offscreen mask or alternate fallback. The effect remains a scrim when blur/glass are disabled,
as in the original. Settings content scrolls behind the header, with the initial inset kept.
Pushed-page titles use the original 17/22sp headline inside a 72dp toolbar and 44dp back button.
`SettingsScrollLayout` provides the same structure to custom settings lists. Keep the visual
overhang pointer-transparent and the header outside the controls' backdrop capture.

Use `rememberGlassBackdrop()` and record **sibling content** with `Modifier.layerBackdrop`.
Never include a consuming glass control in the capture it reads: that creates a RenderNode cycle.
`LocalGlassBackdrop` supplies a common capture to buttons and switches. Place `GlassMenu` in a
full-window `Box` and report the trigger's window bounds through `onBoundsChanged`.

`LeiTheme` connects `LocalLiquidGlassEnabled` to the existing appearance switch. Enabled uses
the ultra optical profile directly: circular rim refraction, smooth-min droplet normals,
adaptive tint, rim light, and the switch lens's channel dispersion. There is no quality selector.
`LocalGlassBlurEnabled` controls Gaussian frost independently. Android versions below 13 use
a filled material with the same layout and touch interactions.

`assets/shaders/liquid_glass.agsl` translates the upstream optical calculations to AGSL.
Hairline blending is enabled for the entire outline when either style coefficient is positive.
A zero directional darkening still blends the undarkened backdrop into the hairline; skipping
that pixel instead creates a raised straight strip where a capsule's sides meet its endcaps.
The native capture supplies Gaussian frost; Compose circular corners replace Flutter's rounded
superellipses. The implementation is a native adaptation, not pixel-identical Flutter rendering.
