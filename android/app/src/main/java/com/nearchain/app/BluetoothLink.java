package com.nearchain.app;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import com.nearchain.net.Log;
import com.nearchain.net.NetNode;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Bluetooth Classic (RFCOMM) link between phones. One phone listens; another connects to its MAC address.
 * Android pairs the two phones the first time, so the system shows a pairing prompt.
 */
final class BluetoothLink {
    static final UUID SERVICE_UUID = UUID.fromString("8f3c2a10-5b7e-4c9a-9d1e-6a2b3c4d5e6f");

    private final NetNode node;
    private final Log log;
    private final BluetoothAdapter adapter;
    private final List<String> found = new CopyOnWriteArrayList<>();
    private BluetoothServerSocket server;
    private BroadcastReceiver discoveryReceiver;

    BluetoothLink(NetNode node, Log log) {
        this.node = node;
        this.log = log;
        this.adapter = BluetoothAdapter.getDefaultAdapter();
    }

    /** False if the phone has no Bluetooth hardware, or Bluetooth is switched off. */
    boolean available() {
        return adapter != null && adapter.isEnabled();
    }

    /** Starts listening for phones that connect over Bluetooth. Needs the Bluetooth permissions. */
    void startServer() throws IOException {
        if (server != null) {
            return;
        }
        server = adapter.listenUsingRfcommWithServiceRecord("NearChain", SERVICE_UUID);
        Thread accept = new Thread(() -> {
            while (server != null) {
                try {
                    BluetoothSocket socket = server.accept();
                    addSocket(socket);
                } catch (IOException e) {
                    log.log("Bluetooth listening stopped: " + e.getMessage());
                    server = null;
                }
            }
        }, "nearchain-bluetooth-accept");
        accept.setDaemon(true);
        accept.start();
        log.log("Bluetooth link listening");
    }

    /** Connects to the phone with this MAC address, in the background. */
    void connect(String mac) {
        Thread t = new Thread(() -> {
            try {
                BluetoothDevice device = adapter.getRemoteDevice(mac);
                adapter.cancelDiscovery();
                BluetoothSocket socket = device.createRfcommSocketToServiceRecord(SERVICE_UUID);
                socket.connect();
                addSocket(socket);
            } catch (IOException | SecurityException | IllegalArgumentException e) {
                log.log("could not connect over Bluetooth to " + mac + ": " + e.getMessage());
            }
        }, "nearchain-bluetooth-connect");
        t.setDaemon(true);
        t.start();
    }

    private void addSocket(BluetoothSocket socket) throws IOException {
        BluetoothDevice device = socket.getRemoteDevice();
        String name = "bluetooth " + deviceName(device) + " " + device.getAddress();
        node.addStreamPeer(socket.getInputStream(), socket.getOutputStream(), name, socket);
    }

    /** Looks for nearby phones for about 12 seconds. Results appear in discovered(). */
    void startDiscovery(Context context) {
        if (discoveryReceiver == null) {
            discoveryReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context ctx, Intent intent) {
                    if (BluetoothDevice.ACTION_FOUND.equals(intent.getAction())) {
                        BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                        if (device != null) {
                            String line = deviceName(device) + "  " + device.getAddress();
                            if (!found.contains(line)) {
                                found.add(line);
                            }
                        }
                    }
                }
            };
            IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
            context.registerReceiver(discoveryReceiver, filter);
        }
        found.clear();
        adapter.startDiscovery();
    }

    void stopDiscovery(Context context) {
        adapter.cancelDiscovery();
        if (discoveryReceiver != null) {
            try {
                context.unregisterReceiver(discoveryReceiver);
            } catch (IllegalArgumentException ignored) {
                // already unregistered
            }
            discoveryReceiver = null;
        }
    }

    /** Phones found by the last discovery, as "name  MAC". */
    List<String> discovered() {
        return found;
    }

    private static String deviceName(BluetoothDevice device) {
        String name = device.getName();
        return name == null || name.isEmpty() ? "(no name)" : name;
    }
}
