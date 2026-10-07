package com.tvremote.tvremote;

import dadb.AdbKeyPair;
import dadb.Dadb;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.regex.Pattern;

@Component
public class TvSession {

    private static final int CONNECT_TIMEOUT_MS = 4_000;
    private static final int SOCKET_TIMEOUT_MS = 8_000;

    private static final Pattern HOST = Pattern.compile(
            "^(?:(?:25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(?:25[0-5]|2[0-4]\\d|[01]?\\d\\d?)$"
                    + "|^localhost$"
                    + "|^(?=.{1,253}$)[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$");

    private String host;
    private int port;

    public String connect(String host, int port) throws Exception {
        String cleanHost = Objects.requireNonNull(host, "host").trim();
        if (cleanHost.isEmpty() || !HOST.matcher(cleanHost).matches()) {
            throw new IllegalArgumentException("Geçersiz IP veya host");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Port 1-65535 arasında olmalı");
        }

        try (Dadb dadb = open(cleanHost, port)) {
            String model = dadb.shell("getprop ro.product.model").getOutput().trim();
            synchronized (this) {
                this.host = cleanHost;
                this.port = port;
            }
            return model.isEmpty() ? "TV" : model;
        }
    }

    public synchronized void disconnect() {
        host = null;
        port = 0;
    }

    public synchronized boolean isConnected() {
        return host != null;
    }

    public synchronized String getHost() {
        return host;
    }

    public synchronized int getPort() {
        return port;
    }

    public Dadb open() {
        String currentHost;
        int currentPort;
        synchronized (this) {
            currentHost = host;
            currentPort = port;
        }
        if (currentHost == null) {
            throw new IllegalStateException("TV bağlantısı yok");
        }
        return open(currentHost, currentPort);
    }

    private static Dadb open(String host, int port) {
        return Dadb.create(
                host,
                port,
                AdbKeyPair.readDefault(),
                CONNECT_TIMEOUT_MS,
                SOCKET_TIMEOUT_MS,
                false
        );
    }
}
