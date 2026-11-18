package com.nearchain.net;

import com.nearchain.core.Block;
import com.nearchain.core.Chain;
import com.nearchain.core.ChainException;
import com.nearchain.core.Consensus;
import com.nearchain.core.Mempool;
import com.nearchain.core.Tx;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Wi-Fi peer protocol. Same messages as src/nearchain/network.py:
 * hello, get_chain, chain, block, tx.
 */
public final class NetNode {
    private final Chain chain;
    private final Mempool mempool;
    private final Log log;
    private final Events events;
    private final List<Peer> peers = new CopyOnWriteArrayList<>();
    private volatile ServerSocket server;
    private volatile boolean running;

    public NetNode(Chain chain, Mempool mempool, Log log, Events events) {
        this.chain = chain;
        this.mempool = mempool;
        this.log = log;
        this.events = events;
    }

    /** Listen for peers on this port. Returns the port actually bound. */
    public synchronized int start(int port) throws IOException {
        if (running) {
            return server.getLocalPort();
        }
        ServerSocket socket = new ServerSocket();
        socket.setReuseAddress(true);
        socket.bind(new InetSocketAddress(port));
        server = socket;
        running = true;
        Thread accept = new Thread(this::acceptLoop, "nearchain-accept");
        accept.setDaemon(true);
        accept.start();
        Thread rebroadcast = new Thread(this::rebroadcastLoop, "nearchain-rebroadcast");
        rebroadcast.setDaemon(true);
        rebroadcast.start();
        log.log("listening for peers on port " + socket.getLocalPort());
        return socket.getLocalPort();
    }

    public void connect(String host, int port) {
        Thread t = new Thread(() -> {
            try {
                Socket socket = new Socket();
                socket.connect(new InetSocketAddress(host, port), 5000);
                addPeer(socket);
            } catch (IOException e) {
                log.log("could not connect to " + host + ":" + port + ": " + e.getMessage());
            }
        }, "nearchain-connect");
        t.setDaemon(true);
        t.start();
    }

    public int peerCount() {
        return peers.size();
    }

    /** Pending transfers are sent again every 30 seconds until a block confirms them. */
    private void rebroadcastLoop() {
        while (running) {
            try {
                Thread.sleep(30000L);
            } catch (InterruptedException e) {
                return;
            }
            for (Tx t : mempool.pending()) {
                broadcastTx(t, null);
            }
        }
    }

    /** One line per peer, for the UI. */
    public String describePeers() {
        StringBuilder sb = new StringBuilder();
        for (Peer p : peers) {
            sb.append(p.name()).append("  height ").append(p.height).append('\n');
        }
        return sb.length() == 0 ? "No peers connected." : sb.toString();
    }

    private void acceptLoop() {
        while (running) {
            try {
                addPeer(server.accept());
            } catch (IOException e) {
                if (running) {
                    log.log("accept failed: " + e.getMessage());
                }
                break;
            }
        }
    }

    private void addPeer(Socket socket) throws IOException {
        socket.setTcpNoDelay(true);
        Peer peer = new Peer(socket);
        peers.add(peer);
        log.log("peer connected: " + peer.name());
        send(peer, hello());
        // A peer that connects later still needs transfers that were sent before it arrived.
        for (Tx t : mempool.pending()) {
            send(peer, txMessage(t));
        }
        Thread reader = new Thread(() -> readLoop(peer), "nearchain-peer");
        reader.setDaemon(true);
        reader.start();
    }

    private void readLoop(Peer peer) {
        try {
            String line;
            while ((line = peer.readLine()) != null) {
                handle(peer, new JSONObject(line));
            }
            log.log("peer " + peer.name() + " disconnected");
        } catch (Exception e) {
            log.log("peer " + peer.name() + " dropped: " + e.getMessage());
        } finally {
            peer.close();
            peers.remove(peer);
        }
    }

    private void handle(Peer peer, JSONObject msg) throws Exception {
        String type = msg.getString("type");
        switch (type) {
            case "hello":
                onHello(peer, msg);
                break;
            case "get_chain":
                send(peer, chainMessage());
                break;
            case "chain":
                onChain(msg);
                break;
            case "block":
                onBlock(peer, msg);
                break;
            case "tx":
                onTransfer(peer, msg);
                break;
            default:
                throw new IOException("unknown message type " + type);
        }
    }

    private void onHello(Peer peer, JSONObject msg) throws Exception {
        if (!Consensus.NETWORK_ID.equals(msg.getString("network"))) {
            throw new IOException("peer is on a different network");
        }
        if (msg.getLong("version") != Consensus.PROTOCOL_VERSION) {
            throw new IOException("protocol version mismatch");
        }
        peer.work = new BigInteger(msg.get("work").toString());
        peer.height = msg.getLong("height");
        if (peer.work.compareTo(chain.cumulativeWork()) > 0) {
            send(peer, new JSONObject().put("type", "get_chain"));
        }
    }

    private void onChain(JSONObject msg) throws Exception {
        JSONArray array = msg.getJSONArray("blocks");
        List<Block> blocks = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            blocks.add(BlockCodec.fromJson(array.getJSONObject(i)));
        }
        try {
            if (chain.consider(blocks)) {
                mempool.revalidate();
                log.log("synced to height " + chain.height());
            }
        } catch (ChainException e) {
            log.log("rejected peer chain: " + e.getMessage());
        }
    }

    private void onBlock(Peer peer, JSONObject msg) throws Exception {
        Block block = BlockCodec.fromJson(msg.getJSONObject("block"));
        boolean accepted = false;
        boolean needSync = false;
        synchronized (chain) {
            long tipHeight = chain.height();
            if (block.header.height <= tipHeight) {
                return; // already have it, or an older fork
            }
            if (block.header.height == tipHeight + 1 && block.header.prevHash.equals(chain.tip().hash())) {
                try {
                    chain.append(block);
                    accepted = true;
                } catch (ChainException e) {
                    log.log("rejected block " + block.header.height + ": " + e.getMessage());
                    return;
                }
            } else {
                needSync = true;
            }
        }
        if (accepted) {
            log.log("accepted block " + block.header.height + " from " + peer.name());
            mempool.removeMined(block);
            for (Tx t : block.txs) {
                events.transferConfirmed(t);
            }
            broadcastBlock(block, peer);
        }
        if (needSync) {
            send(peer, new JSONObject().put("type", "get_chain"));
        }
    }

    private void onTransfer(Peer peer, JSONObject msg) throws Exception {
        Tx tx = BlockCodec.txFromJson(msg.getJSONObject("tx"));
        String reason = mempool.add(tx);
        if ("already pending".equals(reason)) {
            return; // peers re-send pending transfers; nothing new to do
        }
        if (reason != null) {
            log.log("rejected transfer from " + peer.name() + ": " + reason);
            return;
        }
        log.log("transfer received: " + tx.amount + " CDC from " + tx.sender + " to " + tx.recipient);
        events.transferPending(tx);
        broadcastTx(tx, peer);
    }

    /** Send a newly mined or accepted block to every peer except `except`. */
    public void broadcastBlock(Block block, Peer except) {
        try {
            JSONObject msg = new JSONObject().put("type", "block").put("block", BlockCodec.toJson(block));
            for (Peer p : peers) {
                if (p != except) {
                    send(p, msg);
                }
            }
        } catch (Exception e) {
            log.log("could not relay block: " + e.getMessage());
        }
    }

    /** Send a transfer to every peer except `except`. */
    public void broadcastTx(Tx tx, Peer except) {
        try {
            JSONObject msg = txMessage(tx);
            for (Peer p : peers) {
                if (p != except) {
                    send(p, msg);
                }
            }
        } catch (Exception e) {
            log.log("could not relay transfer: " + e.getMessage());
        }
    }

    private static JSONObject txMessage(Tx tx) throws org.json.JSONException {
        return new JSONObject().put("type", "tx").put("tx", new JSONObject(tx.signedJson()));
    }

    private JSONObject hello() throws IOException {
        synchronized (chain) {
            try {
                return new JSONObject()
                        .put("type", "hello")
                        .put("network", Consensus.NETWORK_ID)
                        .put("version", Consensus.PROTOCOL_VERSION)
                        .put("height", chain.height())
                        .put("work", chain.cumulativeWork());
            } catch (org.json.JSONException e) {
                throw new IOException("could not build hello message", e);
            }
        }
    }

    private JSONObject chainMessage() throws IOException {
        try {
            JSONArray array = new JSONArray();
            for (Block b : chain.blocks()) {
                array.put(BlockCodec.toJson(b));
            }
            return new JSONObject().put("type", "chain").put("blocks", array);
        } catch (org.json.JSONException e) {
            throw new IOException("could not build chain message", e);
        }
    }

    private void send(Peer peer, JSONObject msg) {
        try {
            peer.sendLine(msg.toString());
        } catch (IOException e) {
            peer.close();
        }
    }
}
