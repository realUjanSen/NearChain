# NearChain consensus rules (version 1)

Every node applies these rules. They are compiled in (`src/nearchain/consensus.py`)
and cannot be changed by configuration files.

## Units and supply

- Amounts are whole CDC. There are no fractions.
- Maximum supply: 21,000,000,000 CDC.
- Block reward: 1,000 CDC per block. The target block time is 60 seconds, so the intended
  rate is 1,000 CDC per minute.
- Total issued through height h: `min(1000 × h, 21,000,000,000)`.
- A block at height h may pay `reward_at(h) = issued(h) − issued(h−1)` plus the fees
  of its transactions. The cap is reached at height 21,000,000. After that, the subsidy is 0.
- Emission is per block, not per wall-clock minute. Clock time is not part of the
  consensus, so nodes cannot disagree about rewards because their clocks differ.
  If blocks come slower than 60 seconds on average, the cap arrives later in calendar time.

## Blocks

A header contains: version, height, previous hash, Merkle root, timestamp,
difficulty, and nonce. The header hash is SHA-256 applied twice to the text
`version|height|prev_hash|merkle_root|timestamp|difficulty|nonce`.

The Merkle root covers the coinbase digest and each transaction digest. An odd
node is paired with itself.

The genesis block has height 0, a zero previous hash, no transactions, and a reward of 0.

## Proof of work

A header meets its target when the first 16 hex characters of its hash, read as a
number, are at most `(2^64 − 1) / difficulty`. A difficulty of d needs about d
attempts on average.

## Difficulty (LWMA)

The next difficulty uses the last 60 block intervals. Recent intervals carry more weight.
Each interval is clamped to between 1 second and 6 × 60 seconds. The result is clamped
to between 1 and 2^48. The function depends on timestamps and difficulties only,
never on height.

Consequence: if blocks arrive exactly on schedule, difficulty does not change.
Faster blocks raise difficulty. Slower blocks lower it.

## Validation

For every block after genesis, a node checks: height, protocol version, previous
hash, timestamp (strictly later than the previous block), difficulty, Merkle root,
miner address, proof of work, every transaction, and the coinbase reward.

Each transaction checks: network ID, integer fields, positive amount within the supply,
minimum fee, valid addresses, no self-transfer, the correct nonce, sufficient balance,
and a valid Ed25519 signature from the sender's key.

## Chain selection

A node adopts a peer's chain only after validating every block from genesis, and only
if the cumulative work is strictly greater than its own. Cumulative work is the sum of
difficulties. Equal work keeps the current chain.
