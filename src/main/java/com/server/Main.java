package com.server;

import org.java_websocket.server.WebSocketServer;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.exceptions.WebsocketNotConnectedException;

import org.json.JSONObject;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;



public class Main extends WebSocketServer {

    public static final int DEFAULT_PORT = 3000;

    // JSON Keys
    private static final String K_TYPE = "type";
    private static final String K_MESSAGE = "message";

    // Message Types (Client -> Server)
    private static final String T_URL = "url";
    
    // Message Types (Server -> Client)
    private static final String T_WELCOME = "welcome";

    private final Map<WebSocket, Integer> clients = new ConcurrentHashMap<>();
    private final AtomicInteger clientIdCounter = new AtomicInteger(1);

    public Main(InetSocketAddress address) {
        super(address);
    }

    /**
     * Envíar mensaje al cliente conectado
     */
    private void sendSafe(WebSocket to, String payload) {
        if (to == null) return;
        try {
            to.send(payload);
        } catch (WebsocketNotConnectedException e) {
            Integer clientId = clients.remove(to);
            System.out.println("Client disconnected during send: Client#" + clientId);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Envía un mensaje a todos los clientes conectados.
     */
    private void broadcastToAll(String payload) {
        for (WebSocket conn : clients.keySet()) {
            sendSafe(conn, payload);
        }
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        int clientId = clientIdCounter.getAndIncrement();
        clients.put(conn, clientId);
        System.out.println("Client connected: Client#" + clientId);
        
        // Enviamos hola al conectarse
        JSONObject message = new JSONObject()
            .put(K_TYPE, T_WELCOME)
            .put(K_MESSAGE, "Hola");
        broadcastToAll(message.toString());
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        Integer clientId = clients.remove(conn);
        System.out.println("Client disconnected: Client#" + clientId);
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        Integer clientId = clients.get(conn);
        System.out.println("Message from Client#" + clientId + ": " + message);
        
        JSONObject obj;
        try {
            obj = new JSONObject(message);
        } catch (Exception ex) {
            System.err.println("Invalid JSON from Client#" + clientId);
            return;
        }

        String type = obj.optString(K_TYPE, "");
        
        // Manejar los mensajes recibidos
        switch (type) {
            case T_URL: // Se solicita la url del server (no tiene ningun tipo de sentido)
                JSONObject response = new JSONObject()
                    .put(K_TYPE, T_URL)
                    .put(K_MESSAGE, "matrixplay1@ieticloudpro.ieti.cat");
                
                sendSafe(conn, response.toString());
                break;
            default:
                System.out.println("Unknown message type: " + type);
                break;
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        ex.printStackTrace();
        if (conn != null) {
            clients.remove(conn);
        }
    }

    @Override
    public void onStart() {
        System.out.println("WebSocket server started on port: " + getPort());
        setConnectionLostTimeout(100);
    }

    private static void registerShutdownHook(Main server) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Stopping server...");
            try {
                server.stop(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
                Thread.currentThread().interrupt();
            }
            System.out.println("Server stopped.");
        }));
    }

    private static void awaitForever() {
        try {
            new CountDownLatch(1).await();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    public static void main(String[] args) {
        Main server = new Main(new InetSocketAddress(DEFAULT_PORT));
        registerShutdownHook(server);
        server.start();
        System.out.println("Server running on port " + DEFAULT_PORT + ". Press Ctrl+C to stop.");
        awaitForever();
    }
}
