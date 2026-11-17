# Networking

Nodes connect over TCP. Each message is one line of UTF-8 JSON, up to 16 MB.

| Message     | Fields                       | Purpose                                  |
|-------------|------------------------------|------------------------------------------|
| `hello`     | network, version, height, work | Sent by both sides on connect           |
| `get_chain` | none                         | Ask the peer for its full chain          |
| `chain`     | blocks                       | Reply with every block                   |
| `tx`        | tx                           | Relay a transaction                      |
| `block`     | block                        | Relay a newly mined block                |

Rules:
- A peer on another network or protocol version is disconnected.
- If a peer's `work` is higher than ours, we ask for its chain.
- Received blocks are validated before they are stored or relayed.
- Invalid transactions are refused and not relayed.
- Malformed messages or oversized lines disconnect the peer.

Peer discovery in this version is manual: enter `host:port` in the Network tab, or
list peers in `config/nearchain.yaml`. Nodes do not yet find each other on their own.

## Running two nodes on separate machines

1. On machine A, start NearChain. Note its IP address and the port in `nearchain.yaml`
   (default 8333). Allow that port through the Windows firewall when prompted.
2. On machine B, open the Network tab, enter `A_IP:8333`, and click Connect.
3. Both sides should show the peer, and B should sync A's chain.
4. Mine on either machine. Blocks and transactions relay to the other.

Both machines must be on the same network and reachable from each other.
