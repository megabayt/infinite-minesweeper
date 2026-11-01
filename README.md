# Infinite Minesweeper

A native Android Minesweeper game with an effectively infinite minefield, independent 8 × 8 blocks, scoring, persistent runs, and three game modes. The app uses Java and a custom Android `View`; it has no third-party runtime dependencies and requires no network permission.

## Game modes

| Mode | Rules |
| --- | --- |
| **Explore** | Infinite lives and 10 mines per block. A mine hit is revealed without being flagged and increments the mistakes counter. There is no game over. |
| **Survival** | Fixed 10-mine blocks. Every 5 cleared blocks award an extra life. A life saves the run and flags the hit mine; without a life, the run ends. |
| **Gauntlet** | Survival rules with increasing difficulty. Blocks gain one mine per ring farther from the starting block, from 10 up to 24 mines. |

Every cleared block awards 100 points. Best scores are stored separately for each mode.

## Controls

- **Tap:** Reveal a tile or chord an open number.
- **Long-press:** Place or remove a flag.
- **Drag:** Pan around the infinite field.
- **Pinch:** Zoom out to 12 dp or in to 120 dp tiles.
- **Gear icon:** Choose a difficulty, toggle haptics, or reset best scores.
- **Help icon:** Open the rules.
- **New game:** Start over in the current mode.

The first revealed tile and its surrounding tiles are safe. Numbers and flood fills work across block boundaries.

## Persistence

The current game saves automatically after gameplay changes and when the app pauses. Saved state includes blocks, mines, revealed tiles, flags, score, lives or mistakes, mode, game-over state, camera position, and zoom. Writes are atomic, and existing save-format versions are migrated when possible.

## Build

Requirements:

- JDK 17
- Android SDK Platform 35
- Android SDK Build-Tools

Use the included Gradle wrapper:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

The installable debug APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Minimum supported Android version: **Android 6.0 (API 23)**. Target and compile SDK: **API 35**.

## Tests

The JVM test suite covers deterministic generation, negative coordinates, first-click safety, flags, flood fills, chording, scoring, extra lives, Explore mistakes, Gauntlet scaling, game over, and save/load round trips.

Run it with:

```bash
./gradlew testDebugUnitTest
```

## Android CI

[`.github/workflows/android-ci.yml`](.github/workflows/android-ci.yml) runs on pushes, pull requests, and manual dispatches. It uses JDK 17 to run unit tests, Android lint, and a debug build. The resulting debug APK is uploaded as a workflow artifact for seven days.

## Automatic GitHub releases

[`.github/workflows/android-release.yml`](.github/workflows/android-release.yml) runs when a tag beginning with `v` is pushed. It runs tests and lint, builds a release APK, zipaligns and signs it, verifies the signature, and publishes the APK to GitHub Releases with generated release notes.

### Configure signing once

Create the following **Actions repository secrets** under **Settings → Secrets and variables → Actions**:

| Secret | Value |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Base64-encoded contents of the release keystore |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | Alias of the release key |
| `ANDROID_KEY_PASSWORD` | Release key password |

On Linux, encode an existing keystore without adding it to the repository:

```bash
base64 -w 0 release-keystore.jks
```

Keep the keystore and passwords private. The workflow deliberately fails instead of publishing an unsigned release when credentials are missing.

### Publish a release

Update `versionCode` and `versionName` in `app/build.gradle`, commit that change, then tag the desired commit:

```bash
git tag v1.0.0
git push origin v1.0.0
```

The signed APK is named `InfiniteMinesweeper-<tag>.apk` and attached to the corresponding GitHub release. Re-running the workflow for an existing tag replaces the APK attachment.

## Project structure

- `app/src/main/java/com/infiniteminesweeper/Minefield.java` — generation, rules, scoring, modes, and snapshot serialization
- `app/src/main/java/com/infiniteminesweeper/GameView.java` — rendering, gestures, viewport, and haptics
- `app/src/main/java/com/infiniteminesweeper/MainActivity.java` — interface, settings, persistence, and best scores
- `app/src/test/java/com/infiniteminesweeper/MinefieldTest.java` — JVM gameplay and persistence tests
