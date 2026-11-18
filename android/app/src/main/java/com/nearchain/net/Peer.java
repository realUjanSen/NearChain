package com.nearchain.net;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;

/** One connection, over Wi-Fi or Bluetooth. Messages are single lines of JSON. */
final class Peer {
    static final int MAX_LINE_CHARS = 16 * 1024 * 1024;

    private final BufferedReader in;
    private final BufferedWriter out;
    private final Closeable closer;
    private final String name;
    volatile String address = "";
    volatile BigInteger work = BigInteger.ZERO;
    volatile long height;

    Peer(InputStream input, OutputStream output, String name, Closeable closer) {
        this.in = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
        this.out = new BufferedWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8));
        this.name = name;
        this.closer = closer;
    }

    String name() {
        return name;
    }

    synchronized void sendLine(String line) throws IOException {
        out.write(line);
        out.write('\n');
        out.flush();
    }

    String readLine() throws IOException {
        String line = in.readLine();
        if (line != null && line.length() > MAX_LINE_CHARS) {
            throw new IOException("message too large");
        }
        return line;
    }

    void close() {
        try {
            closer.close();
        } catch (IOException ignored) {
            // already closed
        }
    }
}
