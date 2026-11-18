package com.nearchain.net;

import com.nearchain.core.Tx;

/** Called when a transfer arrives on the network, and again when a block confirms it. */
public interface Events {
    void transferPending(Tx tx);

    void transferConfirmed(Tx tx);
}
