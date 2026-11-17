# NearChain

NearChain is a small peer-to-peer proof-of-work cryptocurrency. Its coin is NearChainCoin (CDC),
with a 21,000,000,000 CDC cap and a reward of 1,000 CDC per block (60-second target).

## Project layout

```
nearchain-project/
  README.md
  requirements.txt          Python dependencies
  run_tests.py              runs the full test suite
  config/
    nearchain.yaml         shared node settings (port, peers, data folder)
    windows.yaml            desktop mining and UI settings
    android.yaml            planned Android settings (not read yet)
  src/nearchain/           consensus core, wallet, network, miner, config
    consensus.py            protocol constants (not configurable)
    emission.py             issuance schedule and cap
    difficulty.py           LWMA difficulty adjustment
    block.py                headers, blocks, Merkle roots, proof of work
    chain.py                validation, state, block building, chain selection
    wallet.py               keys, addresses, signing, encrypted keystore
    network.py              TCP peer protocol and mempool
    miner.py                background miner
    config.py               YAML loading and validation
  desktop/
    nearchain_desktop.py   Windows/desktop client with Miner, Trader, Explorer, Network tabs
  tests/                    unit and network tests
  build/
    build_windows.bat       builds NearChain.exe with PyInstaller
  android/                  Android client (Gradle, Java) — see android/README.md
  .github/workflows/ci.yml  GitHub Actions: Python tests, Android build, Windows .exe
  docs/                     consensus, networking, wallet/mining, security notes
```

## Run the tests

Requires Python 3.10 or newer.

```
pip install -r requirements.txt
python run_tests.py
```

## Run the desktop app

```
python desktop/nearchain_desktop.py
```

On first run you will be asked to create a wallet and choose its password. Go to
Miner Mode and press Start mining. Blocks appear in the Explorer tab and your balance
updates in the status bar.

## Build the Android app

See `android/README.md`. CI builds the debug APK on every push.

## Build the Windows executable

From the project root in a Windows command prompt:

```
build\build_windows.bat
```

The program is written to `dist\NearChain\NearChain.exe`. This script has not been
run on Windows yet, so check its output on your machine.

## Two nodes on two machines

See `docs/NETWORK.md`.

## Status

| Part                     | Status |
|--------------------------|--------|
| Consensus, emission, LWMA | done, tested |
| Wallet, signed transfers  | done, tested |
| TCP sync and tx relay     | done, tested on localhost |
| Miner Mode / Trader Mode (desktop) | written; GUI not run in this environment |
| Windows .exe build        | script written; runs in CI (windows-latest) |
| Android client (Java)     | mining, validation and screens written; Java core checked against Python vectors; app not compiled here, CI builds it |
| Android transfers, networking | not started |
| Bluetooth                 | not started |

Read `docs/SECURITY_AND_LIMITS.md` before doing anything else with the code.
TestUpdate