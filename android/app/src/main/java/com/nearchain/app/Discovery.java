package com.nearchain.app;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;

import com.nearchain.net.Log;
import com.nearchain.net.NetNode;

import java.util.UUID;

/**
 * Finds other NearChain nodes on the same Wi-Fi network using mDNS (Android NSD) and
 * connects to them. Desktop nodes do not advertise yet, so they are added by hand.
 */
final class Discovery {
    static final String SERVICE_TYPE = "_nearchain._tcp.";

    private final NsdManager nsd;
    private final NetNode node;
    private final Log log;
    private final String ownName;
    private NsdManager.RegistrationListener registration;
    private NsdManager.DiscoveryListener discovery;

    Discovery(Context context, NetNode node, Log log) {
        this.nsd = (NsdManager) context.getSystemService(Context.NSD_SERVICE);
        this.node = node;
        this.log = log;
        this.ownName = "NearChain-" + UUID.randomUUID().toString().substring(0, 8);
    }

    void start(int port) {
        NsdServiceInfo info = new NsdServiceInfo();
        info.setServiceName(ownName);
        info.setServiceType(SERVICE_TYPE);
        info.setPort(port);

        registration = new NsdManager.RegistrationListener() {
            @Override
            public void onServiceRegistered(NsdServiceInfo serviceInfo) {
                log.log("advertising on the local network as " + serviceInfo.getServiceName());
            }

            @Override
            public void onRegistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
                log.log("could not advertise on the local network (error " + errorCode + ")");
            }

            @Override
            public void onServiceUnregistered(NsdServiceInfo serviceInfo) {
            }

            @Override
            public void onUnregistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
            }
        };
        nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, registration);

        discovery = new NsdManager.DiscoveryListener() {
            @Override
            public void onDiscoveryStarted(String serviceType) {
            }

            @Override
            public void onDiscoveryStopped(String serviceType) {
            }

            @Override
            public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                log.log("local network search failed (error " + errorCode + ")");
            }

            @Override
            public void onStopDiscoveryFailed(String serviceType, int errorCode) {
            }

            @Override
            public void onServiceFound(NsdServiceInfo found) {
                if (found.getServiceName().equals(ownName) || !found.getServiceType().contains("_nearchain")) {
                    return;
                }
                nsd.resolveService(found, new NsdManager.ResolveListener() {
                    @Override
                    public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
                        log.log("could not resolve " + serviceInfo.getServiceName());
                    }

                    @Override
                    public void onServiceResolved(NsdServiceInfo resolved) {
                        if (resolved.getServiceName().equals(ownName)) {
                            return;
                        }
                        node.connect(resolved.getHost().getHostAddress(), resolved.getPort());
                    }
                });
            }

            @Override
            public void onServiceLost(NsdServiceInfo serviceInfo) {
            }
        };
        nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discovery);
    }

    void stop() {
        try {
            if (discovery != null) {
                nsd.stopServiceDiscovery(discovery);
            }
            if (registration != null) {
                nsd.unregisterService(registration);
            }
        } catch (IllegalArgumentException ignored) {
            // already stopped
        }
    }
}
