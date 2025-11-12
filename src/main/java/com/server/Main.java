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
    private static final String K_COUNTDOWN = "countdown";
    private static final String K_NUMBER = "number";

    // Message Types (Client -> Server)
    private static final String T_URL = "url";
    private static final String T_GROUPNAME = "groupname";
    
    // Message Types (Server -> Client)
    private static final String T_WELCOME = "welcome";

    private final Map<WebSocket, Integer> clients = new ConcurrentHashMap<>();
    private final AtomicInteger clientIdCounter = new AtomicInteger(1);

    private String serverUrl, groupName = "DefaultValue";

    public Main(InetSocketAddress address) {
        super(address);
        loadConfig();
    }

    // Cargar configuración del json
    private void loadConfig() {
        try {
            String content = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("config.json")));
            JSONObject config = new JSONObject(content);
            
            if (config.has("serverUrl"))
                serverUrl = config.getString("serverUrl");

            if (config.has("groupName"))
                groupName = config.getString("groupName");
            
        } catch (Exception e) {
            System.err.println("Error: Could not load config.json.");
        }
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

        // Límite de 3 clientes
        if (clients.size() >= 3) { 
            System.out.println("Max clients reached. Closing new conn.");
            sendSafe(conn, new JSONObject()
                .put(K_TYPE, "error")
                .put(K_MESSAGE, "Maximum clients connected. Connection refused.")
                .toString());
            conn.close();

            listClients(); // listamos clientes conectados

            return;
        }

        // conectamos el cliente
        int clientId = clientIdCounter.getAndIncrement();
        clients.put(conn, clientId);
        System.out.println("Client connected: Client#" + clientId);

        // Enviamos hola al conectarse
        JSONObject hMessage = new JSONObject()
            .put(K_TYPE, T_WELCOME)
            .put(K_MESSAGE, "Hola");

        broadcastToAll(hMessage.toString());

        // countdown
        if (clients.size() >= 3) {
            JSONObject cMessage = new JSONObject()
                .put(K_TYPE, K_COUNTDOWN)
                .put(K_NUMBER, 3);

            broadcastToAll(cMessage.toString());
        }
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
        
        JSONObject response = null;
        // Manejar los mensajes recibidos
        switch (type) {
            case T_URL: // Se solicita la url del server (no tiene ningun tipo de sentido)
                response = new JSONObject()
                    .put(K_TYPE, T_URL)
                    .put(K_MESSAGE, serverUrl);
                
                sendSafe(conn, response.toString());

                break;
            case T_GROUPNAME: // Se solicita el nombre del grupo
                response = new JSONObject()
                    .put(K_TYPE, T_GROUPNAME)
                    .put(K_MESSAGE, groupName);
                
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

    // Listar clientes
    private void listClients() {
        for (WebSocket c : clients.keySet()) {
            Integer id = clients.get(c);
            System.out.println("[*] Online Client#" + id);
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
