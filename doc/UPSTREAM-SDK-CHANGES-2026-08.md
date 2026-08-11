# Upstream SDK-usage changes (merge of `onyx-intl/OnyxAndroidDemo` @ `689ff7f`, 2026-08-04)

Merged commits, on top of our previous base `3fb2b55` (2026-06-29):

| Commit | Date | Subject |
|---|---|---|
| `58a7702` | 2026-07-03 | fix: query failed above android 11 and adjust UI |
| `5366d7d` | 2026-08-03 | feat(OnyxPenDemo): add more erase mode |
| `d066182` | 2026-08-04 | fix: fountain pen end render issue |
| `689ff7f` | 2026-08-04 | merge PR #138 |

No SDK **version** bump: still `onyxsdk-pen:1.5.4`, `onyxsdk-device:1.3.5`. Everything below
is new/changed *usage* of APIs that already exist in those artifacts. Upstream's AGP bump
(3.6.2 → 3.6.4) was dropped in favour of our 8.13.2.

---

## 1. Newly-exercised SDK API surface

These calls did not appear anywhere in the demo before this merge. If a Notable-style app
wants eraser behaviour that matches stock Boox Notes, this is the list that matters.

### `TouchHelper` (onyxsdk-pen)

| API | What it does | Where used now |
|---|---|---|
| `setEraserRawDrawingEnabled(boolean, int strokeStyle)` | Arms the **firmware/SurfaceFlinger-side eraser track**. The stroke-style arg picks what the SF layer paints while erasing. | `PenManager.setEraserRawDrawingEnabled` |
| `setBrushRawDrawingEnabled(boolean)` | Independently gates the *brush* raw-draw path, so brush and eraser can be armed/disarmed separately. | `PenManager.setBrushRawDrawingEnabled` |
| `TouchHelper.STROKE_STYLE_DASH` | Dashed lasso outline — used for **area (lasso) erase** preview. | `PenManager.applySfAreaTouchHelperParams` |
| `isRawDrawingCreated()` / `restartRawDrawing()` | Detect a dead raw session and rebuild it instead of assuming it survived. | `PenManager.applyCurrentPenState` |

### `com.onyx.android.sdk.pen.style.StrokeStyle`

New import. `StrokeStyle.SOFT_ERASER` is the stroke style used for **stroke erase** and
**move (width) erase** SF tracks. Previously the demo had no notion of an eraser stroke style
at all — it just cleared pixels app-side.

### `Device.currentDevice()` stroke parameters (onyxsdk-device)

```java
float[] params = Device.currentDevice().getStrokeParameters(strokeStyle);
Device.currentDevice().setStrokeParameters(strokeStyle, params);
```

Two distinct uses:

1. **Eraser tuning** (`PenManager.warmDashDeviceParameters`):
   ```java
   Device.currentDevice().setStrokeParameters(
       TouchHelper.STROKE_STYLE_DASH, new float[]{PenConstant.DASH_STROKE_WIDTH});
   Device.currentDevice().setStrokeParameters(
       StrokeStyle.SOFT_ERASER, new float[]{eraseWidthPx, 0.5f /*opacity*/, 0.1f /*black opacity*/});
   ```
   The SOFT_ERASER param array is `{width, opacity, blackOpacity}`. These must be pushed
   **before** enabling the eraser raw drawing, otherwise the first stroke uses stale values.

2. **Fountain-pen tuning** (`PenManager.applyStrokeParameters` +
   `PenInfoUtils.mergeStrokeParameters`): for `SHAPE_BRUSH_SCRIBBLE` the demo now reads the
   device's current params, overwrites `[0] = pressure sensitivity` and `[1] = smooth level`,
   and writes them back. Guarded by the new flag `PenConstant.ENABLE_CONFIG_PEN_PRESSURE_SENSITIVITY`.
   This is now invoked from `StrokeStyleChangeRequest` on every pen switch.

### `com.onyx.android.sdk.pen.utils.PenUtils`

New: `PenUtils.KEPLER_DEFAULT_PRESSURE_SENSITIVITY`, `PenUtils.DEFAULT_SMOOTH_LEVEL`.
These are the values fed into `setStrokeParameters` and into `createNeoPenV2`.

### `EpdController` partial refresh

`PenManager.refreshPartial(RectF)` is new and shows the intended pattern for repainting only
the erased region:

```java
EpdController.setViewDefaultUpdateMode(surfaceView, UpdateMode.HAND_WRITING_REPAINT_MODE);
// ... render clipped to rect ...
EpdController.resetViewUpdateMode(surfaceView);
```

with a heuristic that falls back to a **full** refresh once the dirty rect exceeds 60 % of the
bitmap area (`PARTIAL_REFRESH_MAX_AREA_RATIO`). Worth copying — the partial path is much
cheaper but degenerates badly on large lassos.

### Capability probe via reflection

`EraserTrackHelper` probes whether the firmware supports the SF eraser track at all:

```java
Class<?> c = ReflectUtil.classForName("android.onyx.ViewUpdateHelper");
Method m = ReflectUtil.getMethodSafely(c, "setEraserRawDrawingEnabled", boolean.class, int.class);
boolean supported = (m != null);
```

**This is the important one for us.** `TouchHelper.setEraserRawDrawingEnabled` exists in the
SDK on every device, but on older firmware the underlying `android.onyx.ViewUpdateHelper`
method is missing, so the call silently does nothing and you get *no* eraser track. Upstream's
answer is: probe, cache the result, and fall back to an app-drawn track (`Layer.APP`) when the
probe fails. Three-state result: `Layer.SF` / `Layer.APP` / `Layer.NONE`.

---

## 2. Fountain-pen (`NeoPenV2`) API change — breaking

`BrushScribbleShape.renderFountainV2` changed shape. Old:

```java
NeoPen pen = FountainShapes.INSTANCE.createNeoPenV2(w, MIN_W, 1,1,1,1, null, true, null);
NeoPenRender r = new NeoPenRender(pen);
r.render(canvas, paint, renderPoints);           // points passed to render()
```

New:

```java
NeoPen pen = FountainShapes.INSTANCE.createNeoPenV2(w, MIN_W, 1,1,1,1,
        PenInfoUtils.getDefaultPressureSensitivity(shapeType),   // was null
        true,
        PenInfoUtils.getDefaultSmoothLevel(shapeType));          // was null
NeoPenRender r = new NeoPenRender(pen);
r.onTouchPointList(renderPoints);                // points fed separately first
r.render(canvas, paint);                         // then render
```

The two-step `onTouchPointList()` → `render()` split is what fixes the "fountain pen end
render issue" (the stroke tail was rendered with the wrong taper). Passing `null` for
pressure/smooth also made replayed strokes differ from live-drawn ones.

`BrushScribbleShape` additionally now saves/restores `paint.getStyle()` and
`paint.getStrokeWidth()` around the fountain render, because it force-sets `FILL` /
width `0` and was leaking that onto whatever shape rendered next.

---

## 3. Erase architecture — three modes

`StrokeErasingRequest` and `StrokesEraseFinishedRequest` are **deleted**. Replaced by a new
`…pen.demo.erase` package. Modes (`EraseTypes`):

| Mode | Const | Track layer | Stroke style |
|---|---|---|---|
| Stroke erase (whole stroke on touch) | `ERASER_STROKE` = 0 | SF if supported, else app | `SOFT_ERASER` |
| Move erase (width-based partial erase) | `ERASER_MOVE` = 1 | SF if supported, else app | `SOFT_ERASER` |
| Area / lasso erase | `ERASER_AREA` = 2 | SF | `STROKE_STYLE_DASH` |

Notable pieces:

- **`ShapeSplitter`** — move-erase no longer deletes whole strokes; it splits a `TouchPointList`
  into surviving fragments (`SplitShapeResult`). Pure geometry, no SDK dependency.
- **`ShapeUtils.cloneShape` / `cloneTouchPointList`** — fragments must be rebuilt with the
  *concrete* pen subclass and `shapeType`, otherwise every fragment renders as a pencil. There
  is an explicit `resolveShapeType()` fallback for shapes whose `shapeType` field was never
  written (default `0` is ambiguous with `SHAPE_PENCIL_SCRIBBLE`).
- **`Shape.fastHitTest(x, y, radius)`** — new bounding-rect prefilter before the O(n) polyline
  `hitTest`. `hitTest` was also made public.
- **`Shape.getShapeType()` / `getTexture()` / `getTouchPointList()`** — new accessors, needed
  by the splitter.
- Per-mode persisted settings in `PenBundle`: erase width and "show track" are now
  `Map<Integer, …>` keyed by erase type, not single fields.
- `EraseUnits` converts erase width between px and **mm** via `densityDpi`
  (`ResManager.getAppContext()`), with per-mode clamps: move erase 1.0–8.0 mm,
  stroke erase 0.5–8.0 mm. Useful reference if we want widths that feel like stock Notes.

---

## 4. Raw-session lifecycle fixes (relevant regardless of erase)

`PenManager` gained real session state tracking, which fixes classes of bugs we'd hit too:

- `hostSurfaceAttached` + `isHostSurfaceValid(view)` — `attachHostView` used to skip re-attach
  whenever the `SurfaceView` object was identical, even if its `Surface` had been destroyed
  (e.g. after backgrounding). Now it checks `view.getHolder().getSurface().isValid()`.
- `releaseRawSession()` / `needsRawSessionRestart()` — explicit pause/resume, instead of
  tearing everything down.
- `restoreDrawShapesToBitmap()` / `redrawAllShapesToBitmap()` — replay shapes onto a fresh
  bitmap after a surface loss.
- `RenderContext.recycleBitmap()` now also nulls `canvas` (it previously left a `Canvas`
  pointing at a recycled `Bitmap`), and `getCanvas()` rebinds lazily instead of constructing a
  throwaway `Canvas` each call. **This was a real leak/crash source.**
- `ResumeRawDrawingRequest` was gutted: it no longer re-derives pen params itself, it just
  calls `penManager.applyErasePenParams()`, and it now explicitly disables raw-drawing render
  when neither resume flag is set (previously it returned early, leaving render enabled).

---

## 5. `EraseRenderer` null-hardening

Every access to `renderContext.eraseArgs` is now null-checked, and the old
`erasePoint.getX() <= 0 || getY() <= 0` guard was removed — it wrongly suppressed the eraser
circle near the top/left edges of the screen. New `drawEraseDashLine()` renders the app-side
lasso/track when the SF layer isn't available.

`EraseArgs` moved package: `…pen.demo.bean.EraseArgs` → `…pen.demo.erase.bean.EraseArgs`.

---

## 6. Android 11+ package visibility (`OnyxAndroidDemo`)

Dictionary lookup silently returned nothing on API 30+. Fix is a manifest `<queries>` block:

```xml
<queries>
    <provider android:authorities="com.onyx.dict.DictionaryProvider" />
    <package android:name="com.onyx.dict" />
</queries>
```

**Any app calling `DictionaryUtil` (or reaching any other Onyx `ContentProvider` /
cross-app component) on API 30+ needs this.** Without it the provider is invisible and the
query fails with no useful error.

`DictionaryActivity` was also reworked: it now handles `DICT_STATE_NO_DATA` and
`DICT_STATE_PARAM_ERROR` explicitly instead of only `DICT_STATE_QUERY_SUCCESSFUL`, and renders
explanations in a `WebView` (they contain HTML) with a spinner to pick between dictionaries.

---

## 7. Takeaways for a Notable-style app

1. **Eraser track** — if you want the eraser trail drawn by the system rather than by your own
   canvas, that is `TouchHelper.setEraserRawDrawingEnabled(true, StrokeStyle.SOFT_ERASER)`,
   plus `Device.currentDevice().setStrokeParameters(StrokeStyle.SOFT_ERASER, {w, 0.5f, 0.1f})`
   pushed first. Probe `android.onyx.ViewUpdateHelper#setEraserRawDrawingEnabled` before relying
   on it, and keep an app-drawn fallback.
2. **Fountain pen** — if you call `createNeoPenV2`, switch to
   `onTouchPointList()` + `render(canvas, paint)` and stop passing `null` for
   pressure/smooth; and save/restore `Paint` style+width around it.
3. **Pen parameters are global device state** — `setStrokeParameters` is per-`strokeStyle` and
   persists; re-apply on every tool switch (upstream does it from `StrokeStyleChangeRequest`).
4. **Partial refresh** — `HAND_WRITING_REPAINT_MODE` + clipped render, with a full-refresh
   fallback above ~60 % dirty area.
5. **Surface validity** — check `getHolder().getSurface().isValid()`, not object identity,
   before deciding a raw-drawing session is still usable.
6. **API 30+** — add `<queries>` for any Onyx provider/package you talk to.
