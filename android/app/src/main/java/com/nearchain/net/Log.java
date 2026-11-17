package com.nearchain.net;

/** Receives human-readable status messages from the network and update code. */
public interface Log {
    void log(String text);
}
