# NearChain

NearChain is a small proof-of-work cryptocurrency for Android. Phones on the same Wi-Fi/Bluetooth network mine blocks, send CDC (NearChainCoin) to each other, and sync one shared chain. It is built for proof of concept.

## What the app does

**Mining**
- Mine blocks in a foreground service, with a notification so Android doesn't stop it.
- Mining rewards pay the phone's wallet. Each block pays 1,000 CDC plus the fees of the transfers it includes.
- The **Start mining** button is green when idle and grey when mining. **Stop mining** is red when mining and grey when idle.
- The **Activity log** shows mining, wallet, transfer, network, and update events. You can set how many lines to keep (20 to 5,000), and it scrolls to the newest line.
- The **Hash rate** and **Current nonce** show how fast the phone is hashing and where it is in the search for a valid block.

**Difficulty**
- Starts at 135,000 hashes per block, about 1.5 seconds per block at 90,000 hashes per second.
- Rises by 1% every 10 blocks. It never falls, so blocks take longer as the chain grows.
- Every phone computes the same value from the chain, so a phone can't set its own difficulty.

**Wallets**
- Create a wallet with a password. The private key is encrypted with that password on the phone.
- The **Unlock wallet / Lock wallet** button is green when locked and red when unlocked.
- Fingerprint or face unlock (Android 9 and newer): after a password unlock, the app offers to turn it on. The password is then stored encrypted with a hardware-backed key that only a fingerprint or face can use.
- **Delete wallet** asks for confirmation and removes the wallet and any saved fingerprint key. Coins at that address are lost unless the key was backed up.
- The wallet status is green when unlocked and red when locked or missing.

**Sending and receiving CDC**
- **Send CDC** signs a transfer with the wallet's Ed25519 key and puts it in a pending pool.
- The transfer is sent to every connected peer, and again to peers that connect later. It's re-sent every 30 seconds until a block confirms it.
- The status says how many peers received the transfer, or that none are connected.
- A miner includes valid pending transfers in the blocks it mines. Every phone checks each transfer: the signature, the sender's balance, the nonce, and the fee.
- A toast appears when a transfer to your address shows up on the network, and another when a block confirms it.
- **Show my address as QR** displays your address as a QR code. **Scan QR to fill recipient** reads one with the camera and fills in the recipient field.

**Blockchain viewer**
- Balances for every wallet the phone has seen, largest first. Your own wallet is marked.
- Every block, with its hash, miner, reward, and number of transfers.
- Every transfer and mining reward, with the sender, recipient, amount, and fee.

**Networking (Wi-Fi and Bluetooth)**
- Phones on the same Wi-Fi find each other automatically over mDNS (Android NSD).
- You can also connect over Wi-Fi by typing `host:port`.
- **Bluetooth (Classic, RFCOMM):** tap **Turn on Bluetooth link** on each phone, then **Find nearby Bluetooth phones**, or type a phone's Bluetooth address and tap **Connect over Bluetooth**. Android asks you to pair the phones the first time.
- Each peer shows the wallet address it announced during the handshake.
- Phones sync the chain with each other. A longer chain with more total work wins, and each block is checked before it's accepted.
- Blocks, transfers, and sync requests use newline-delimited JSON, over TCP on port 8333 for Wi-Fi and over the Bluetooth RFCOMM channel for Bluetooth.

**Updates**
- **Check for update** asks GitHub for the newest release and compares its build number with the installed one.
- If a newer release exists, the app downloads it and hands it to Android's installer. The installer asks you to confirm.
- After the update, the app stops, and a notification opens the new version.

## What isn't built

- **Persistent storage.** The chain lives in memory. After the app restarts, it starts from genesis and syncs again from peers. Pending transfers you made are lost on restart.
- **Re-sending on the blockchain side.** Pending transfers are re-sent from the phone that made them. A phone that never gets a block confirming a transfer can't show that transfer as confirmed.
- **Bluetooth Low Energy and desktop Bluetooth.** Only phone-to-phone Bluetooth Classic is built.
- **Peer dropdown.** Peers are listed as text, not as a dropdown to pick from.
- **Choosing a version.** The updater installs the latest release. It doesn't let you pick an older one.

## Desktop client

The Python desktop client in `src/nearchain/` and `desktop/` predates the current Android rules. It uses an older difficulty rule and the earlier genesis block, so it does not follow the current chain. It is no longer maintained. Its tests still run with `python run_tests.py`, but they test the old rules.

## Project layout

```
android/                  Android app (Gradle, Java)
  app/src/main/java/com/nearchain/
    app/                  screens, mining service, updater, QR scanner, biometric unlock, explorer
    core/                 blocks, chain, transfers, mempool, difficulty, emission, proof of work
    net/                  Wi-Fi peer protocol, discovery, block and transfer JSON
    wallet/               keys, encrypted keystore, transfer signature check (BouncyCastle)
  app/src/main/res/layout/   activity_main.xml, activity_explorer.xml, activity_scan.xml
  app/src/test/           JUnit tests for the core
  app/keystore.properties and nearchain-release.jks   release signing key
src/nearchain/            Python core (desktop, unmaintained)
desktop/                  Python desktop app (unmaintained)
config/                   YAML settings for the desktop app
.github/workflows/ci.yml  CI: Python tests, Android unit tests and release APK, Windows exe, GitHub release
```

## Build and release

- **Android:** `cd android && gradle testDebugUnitTest assembleRelease`. CI passes the run number as the version code, and the GitHub release uses the tag `v0.1.<run number>`.
- **Signing:** the release key is `android/app/nearchain-release.jks`, and its passwords are in `android/app/keystore.properties`. Both are in the repository. Anyone who has the key can sign an update that installed copies of the app will accept.
- **Updates on the phone:** the first install of a newly signed build replaces the old one only if the signatures match. If a phone has an older build signed with a different key, uninstall it once.

## Testing

- The core package has JUnit tests in `android/app/src/test/java/com/nearchain/core/`. They check the genesis hash, block hashes, the reward schedule, the difficulty rule, and chain validation.
- The transfer code was checked on a desktop JVM against a transfer signed by the Python code. The Android build, the Wi-Fi code, and the screens are checked by CI and have not been run on a phone.

## Status

| Part | Status |
|---|---|
| Mining, difficulty, emission | Built and tested in the core |
| Wallets, encrypted keystore, delete, lock/unlock | Built, not yet run on a phone |
| Fingerprint and face unlock | Built, Android 9 and newer only |
| Transfers (sign, pool, relay, mine, notify) | Built, not yet run on a phone |
| QR show and scan | Built, not yet run on a phone |
| Wi-Fi discovery and sync | Built, not yet run on a phone |
| Bluetooth Classic link (phone to phone) | Built, not yet run on a phone |
| Blockchain viewer | Built, not yet run on a phone |
| Self-update from GitHub | Built, not yet run on a phone |
| Persistent chain storage | Not built |
| Desktop client | Unmaintained; follows older rules |
