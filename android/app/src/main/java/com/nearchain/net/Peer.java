package com.nearchain.net;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.math.BigInteger;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/** One TCP connection. Messages are single lines of JSON. */
final class Peer {
    static final int MAX_LINE_CHARS = 16 * 1024 * 1024;

    final Socket socket;
    private final BufferedReader in;
    private final BufferedWriter out;
    volatile BigInteger work = BigInteger.ZERO;
    volatile long height;

    Peer(Socket socket) throws IOException {
        this.socket = socket;
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
    }

    String name() {
        return socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
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
            socket.close();
        } catch (IOException ignored) {
            // already closed
        }
    }
}
