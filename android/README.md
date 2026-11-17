# NearChain Android client

Gradle project in Java. Open the `android/` folder in Android Studio, or build from the command line.

## Build

With Java 17 and Gradle 8.9 on the PATH (GitHub Actions does this for you):

```
cd android
gradle testDebugUnitTest assembleDebug
```

The debug APK is written to `android/app/build/outputs/apk/debug/`.

To create the Gradle wrapper locally, run `gradle wrapper --gradle-version 8.9` inside `android/`.
The wrapper jar is not in the repository.

## Layout

- `app/src/main/java/com/nearchain/core/`: consensus classes (Emission, Difficulty, Pow, Header, Block, Chain, Miner). These mirror `src/nearchain/` in Python.
- `app/src/main/java/com/nearchain/app/`: MainActivity (Miner Mode and Trader Mode), MiningService (foreground mining), NodeHolder (shared chain state), Identity (mining address).
- `app/src/test/java/com/nearchain/core/CoreVectorsTest.java`: checks the Java core against hashes produced by the Python core.

## What works now

- Mining on the phone in a foreground service, with a notification.
- Block validation, emission cap, LWMA difficulty, proof of work. The results match the Python core on the test vectors.
- Miner Mode and Trader Mode screens, with balance and reward history for the phone's address.

## Not yet built

- Transfers from the phone. They need Ed25519 signing, which is not ported.
- Networking. The phone does not sync with desktop nodes yet.
- Persistence. The chain starts again from genesis when the app restarts.
- Bluetooth.

## Checking

The unit tests run on the JVM and do not need a phone. The CI workflow in `.github/workflows/ci.yml` runs them on every push.
