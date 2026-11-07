package com.restaurantmanager.core.printing;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

/** Sends raw ESC/POS bytes to a network printer (raw TCP, "JetDirect" port 9100). */
public interface PrinterTransport {
    void send(String host, int port, byte[] payload) throws IOException;

    @Component
    class Tcp implements PrinterTransport {
        private static final int CONNECT_TIMEOUT_MS = 3000;
        private static final int WRITE_TIMEOUT_MS = 5000;

        @Override
        public void send(String host, int port, byte[] payload) throws IOException {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
                socket.setSoTimeout(WRITE_TIMEOUT_MS);
                OutputStream out = socket.getOutputStream();
                out.write(payload);
                out.flush();
            }
        }
    }
}
