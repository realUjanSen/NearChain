package com.nearchain.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Valid transfers waiting to be included in a block, in arrival order. */
public final class Mempool {
    private final Chain chain;
    private final Map<String, Tx> pending = new LinkedHashMap<>();

    public Mempool(Chain chain) {
        this.chain = chain;
    }

    public synchronized List<Tx> pending() {
        return new ArrayList<>(pending.values());
    }

    public synchronized int size() {
        return pending.size();
    }

    /** The nonce the next transfer from this sender must use, counting pending transfers. */
    public synchronized long pendingNonce(String sender) {
        long next = chain.nextNonce(sender);
        for (Tx t : pending.values()) {
            if (t.sender.equals(sender)) {
                next++;
            }
        }
        return next;
    }

    /** Adds a transfer if it is valid on top of the chain and the pending transfers. Returns null, or the reason. */
    public synchronized String add(Tx tx) {
        String id = tx.id();
        if (pending.containsKey(id)) {
            return "already pending";
        }
        Map<String, Long> bal = chain.balances();
        Map<String, Long> non = chain.nonces();
        for (Tx p : pending.values()) {
            Chain.applyTransfer(p, bal, non);
        }
        String reason = Chain.validateTransfer(tx, bal, non);
        if (reason == null) {
            pending.put(id, tx);
        }
        return reason;
    }

    /** Call after a block is added to the chain. Drops mined transfers and anything no longer valid. */
    public synchronized void removeMined(Block block) {
        for (Tx t : block.txs) {
            pending.remove(t.id());
        }
        revalidate();
    }

    /** Re-checks every pending transfer against the current chain, keeping those still valid. */
    public synchronized void revalidate() {
        Map<String, Long> bal = chain.balances();
        Map<String, Long> non = chain.nonces();
        Map<String, Tx> kept = new LinkedHashMap<>();
        for (Map.Entry<String, Tx> entry : pending.entrySet()) {
            if (Chain.validateTransfer(entry.getValue(), bal, non) == null) {
                Chain.applyTransfer(entry.getValue(), bal, non);
                kept.put(entry.getKey(), entry.getValue());
            }
        }
        pending.clear();
        pending.putAll(kept);
    }
}
