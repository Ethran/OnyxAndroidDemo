# Upstream SDK-usage changes (merge of `onyx-intl/OnyxAndroidDemo` @ `ec512c7`, 2026-09-10)

Merged commits, on top of our previous base `689ff7f` (2026-08-04):

| Commit | Date | Subject |
|---|---|---|
| `7f73129` | 2026-08-11 | fix: improve dictionary query status |
| `3baff3b` | 2026-09-07 | merge PR #140 |
| `4a0299a` | 2026-09-07 | Create LICENSE |
| `aaf65d4` | 2026-09-09 | update onyxsdk-base, onyxsdk-device and onyxsdk-pen version |
| `60f3587` | 2026-09-09 | Fix INSTALL_FAILED_NO_MATCHING_ABIS by including arm64-v8a ABI |
| `f1a346d` | 2026-09-09 | merge PR #142 |
| `ec512c7` | 2026-09-10 | Update LICENSE owner |

Merged cleanly; no pen/drawing code changed. Nothing here needs porting to Notable.

---

## 1. SDK version bumps

| Artifact | Before | After | Module |
|---|---|---|---|
| `onyxsdk-device` | 1.3.5 | 1.3.5.3 | OnyxAndroidDemo |
| `onyxsdk-pen` | 1.5.4 | 1.5.4.4 | OnyxAndroidDemo, OnyxPenDemo |
| `onyxsdk-base` | 1.8.5 | 1.8.5.5 | daydreamdemo |

These are four-part patch releases on the old release lines. Notable is already on newer lines
(`device` 1.3.6, `pen` 1.5.8, `base` 1.8.9).

## 2. `DictionaryQuery` states (onyxsdk-device)

`DictionaryActivity` now handles two states that existed in the SDK but that the demo
had not handled before:

| State | Meaning | Demo handling |
|---|---|---|
| `DictionaryQuery.DICT_STATE_LOADING` | Dictionary service is still loading its data | Re-query after 2 s, give up after 5 tries |
| `DictionaryQuery.DICT_STATE_QUERY_FAILED` | Query ran but produced no definition | Toast |

`DICT_STATE_ERROR` is now named explicitly alongside `default`. The pending retry is cancelled
in `onDestroy()` and on a new query, and `onPostExecute` bails out if the activity is finishing.

## 3. ABI filters

`OnyxAndroidDemo` now ships `arm64-v8a` alongside `armeabi-v7a`, fixing
`INSTALL_FAILED_NO_MATCHING_ABIS` on 64-bit-only devices. (`OnyxPenDemo` already had both in
our fork.)

## 4. License

Upstream added an Apache-2.0 `LICENSE`, `Copyright 2026 ONYX INTERNATIONAL INC.`. Code copied
from the demo now has a clear license: Apache 2.0 with attribution.
