package com.server;

import org.java_websocket.server.WebSocketServer;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.exceptions.WebsocketNotConnectedException;
import org.json.JSONObject;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Main extends WebSocketServer {

    public static final int DEFAULT_PORT = 3000;

    // JSON Keys
    private static final String K_TYPE = "type";
    private static final String K_MESSAGE = "message";

    // Message Types (Client -> Server)
    private static final String T_URL = "url";

    // Message Types (Server -> Client)
    private static final String T_WELCOME = "welcome";
    private static final String T_PLAYERS_READY = "players_ready";
    private static final String T_COUNTDOWN = "countdown";

    private final Map<WebSocket, String> clients = new ConcurrentHashMap<>();

    public Main(InetSocketAddress address) {
        super(address);
    }

    private void sendSafe(WebSocket to, String payload) {
        if (to == null)
            return;
        try {
            to.send(payload);
        } catch (WebsocketNotConnectedException e) {
            clients.remove(to);
            System.out.println("Client disconnected during send.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void broadcastToAll(String payload) {
        for (WebSocket conn : clients.keySet()) {
            sendSafe(conn, payload);
        }
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        // Limitar a solo 2 jugadores
        if (clients.size() >= 2) {
            JSONObject fullMessage = new JSONObject()
                    .put(K_TYPE, "full")
                    .put(K_MESSAGE, "Sala llena. Solo se permiten 2 jugadores.");
            sendSafe(conn, fullMessage.toString());
            conn.close(1000, "Sala llena");
            System.out.println("Rejected extra client: Sala llena");
            return;
        }

        // Asignar PLAYER1 o PLAYER2 automáticamente
        String playerName = clients.isEmpty() ? "PLAYER1" : "PLAYER2";
        clients.put(conn, playerName);
        System.out.println("Client connected: " + playerName);

        // Enviar mensaje de bienvenida
        JSONObject message = new JSONObject()
                .put(K_TYPE, T_WELCOME)
                .put(K_MESSAGE, "Hola " + playerName);
        sendSafe(conn, message.toString());

        checkPlayersReady();
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        String player = clients.remove(conn);
        System.out.println("Client disconnected: " + player);
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        String player = clients.get(conn);
        System.out.println("Message from " + player + ": " + message);

        JSONObject obj;
        try {
            obj = new JSONObject(message);
        } catch (Exception ex) {
            System.err.println("Invalid JSON from " + player);
            return;
        }

        String type = obj.optString(K_TYPE, "");
        switch (type) {
            case T_URL:
                JSONObject response = new JSONObject()
                        .put(K_TYPE, T_URL)
                        .put(K_MESSAGE, "wss://matrixplay1.ieti.site:443");
                sendSafe(conn, response.toString());
                break;
            default:
                System.out.println("Unknown message type: " + type);
        }
    }

    private void checkPlayersReady() {
        if (clients.size() == 2) {
            String[] players = clients.values().toArray(new String[0]);

            // Enviar PLAYERS_READY a ambos
            int i = 0;
            for (WebSocket conn : clients.keySet()) {
                JSONObject readyMessage = new JSONObject()
                        .put(K_TYPE, T_PLAYERS_READY)
                        .put("opponentName", i == 0 ? players[1] : players[0]);
                sendSafe(conn, readyMessage.toString());
                i++;
            }

            // Enviar countdown inicial (3 segundos)
            JSONObject countdown = new JSONObject()
                    .put(K_TYPE, T_COUNTDOWN)
                    .put("number", 3);
            broadcastToAll(countdown.toString());
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

    public static void main(String[] args) {
        Main server = new Main(new InetSocketAddress(DEFAULT_PORT));
        server.start();
        System.out.println("Server running on port " + DEFAULT_PORT + ". Press Ctrl+C to stop.");
    }
}
