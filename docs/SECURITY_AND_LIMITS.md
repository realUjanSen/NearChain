# Security and known limitations

Do not use it to store or trade anything of real value.

- Not audited. The cryptography is standard, but the protocol design has not been reviewed.
- Small networks are weak. One machine with a large share of the hash rate can rewrite recent
  history, double-spend, or censor transactions (51% attack).
- Partitions: two groups of nodes that mine separately produce competing chains. The one with
  more cumulative work wins when they reconnect, and transactions in the losing branch return
  to the mempool only if the node keeps them.
- Eclipse and Sybil attacks: a node that only talks to attacker-controlled peers can be fed a
  fake view of the chain. Manual peer lists reduce this risk but do not remove it.
- No transaction fee market. Blocks include pending transactions in arrival order.
- No persistent peer list, no NAT traversal, no encryption on the wire.
- The wallet has no recovery phrase. Losing `wallet.json` or its password loses the coins.
- Consensus is implemented in Python only. The Android client is not built.
