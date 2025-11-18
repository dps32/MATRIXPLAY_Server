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



public class Main extends WebSocketServer {

    public static final int DEFAULT_PORT = 3000;

    // JSON Keys
    private static final String K_TYPE = "type";
    private static final String K_MESSAGE = "message";
    private static final String K_VALUE = "value";
    private static final String K_COUNTDOWN = "countdown";
    private static final String K_NUMBER = "number";

    // Message Types (Client -> Server)
    private static final String T_URL = "url";
    private static final String T_GROUPNAME = "groupname";
    private static final String T_PADDLE_MOVE = "paddleMove";
    private static final String T_CLIENT_CONFIRMATION = "clientConfirmation";
    
    // Message Types (Server -> Client)
    private static final String T_WELCOME = "welcome";
    private static final String T_GAME_STATE = "gameState";
    private static final String T_PLAYER_ASSIGNED = "playerAssigned";
    private static final String T_PLAYER_NAMES = "playerNames";

    private final Map<WebSocket, Integer> players = new ConcurrentHashMap<>(); // Jugadores 1 y 2
    private final Map<WebSocket, Boolean> allClients = new ConcurrentHashMap<>(); // Todos los clientes para hacer broadcast
    private final Map<WebSocket, Boolean> confirmedClients = new ConcurrentHashMap<>(); // Clientes confirmados
    private final Map<Integer, String> playerNames = new ConcurrentHashMap<>(); // Nombres de jugadores: playerId -> name

    private String serverUrl, groupName = "DefaultValue";
    
    private GameState gameState = new GameState();
    private Thread gameLoopThread;

    public Main(InetSocketAddress address) {
        super(address);
        loadConfig();
    }

    // Cargar configuración del json
    private void loadConfig() {
        try {
            java.io.InputStream is = getClass().getClassLoader().getResourceAsStream("config.json");
            if (is == null) {
                throw new Exception("config.json not found in resources");
            }
            
            String content = new String(is.readAllBytes());
            JSONObject config = new JSONObject(content);
            
            if (config.has("serverUrl"))
                serverUrl = config.getString("serverUrl");

            if (config.has("groupName"))
                groupName = config.getString("groupName");
            
            is.close();
        } catch (Exception e) {
            System.err.println("Error: Could not load config.json from resources.");
        }
    }

    // Enviar mensaje al cliente
    private void sendSafe(WebSocket to, String payload) {
        if (to == null) return;
        try {
            to.send(payload);
        } catch (WebsocketNotConnectedException e) {
            Integer playerId = players.remove(to);
            if (playerId != null) {
                System.out.println("Player disconnected during send: Player" + playerId);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Enviar mensaje a todos los clientes
    private void broadcastToAll(String payload) {
        for (WebSocket conn : allClients.keySet()) {
            sendSafe(conn, payload);
        }
    }


    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        allClients.put(conn, true);
        System.out.println("Client connected, waiting for confirmation...");

        // Enviar bienvenida
        JSONObject hMessage = new JSONObject()
            .put(K_TYPE, T_WELCOME)
            .put(K_MESSAGE, "Hola");

        sendSafe(conn, hMessage.toString());
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        allClients.remove(conn);
        confirmedClients.remove(conn);
        Integer playerId = players.remove(conn);
        
        if (playerId != null) {
            playerNames.remove(playerId);
            System.out.println("Player" + playerId + " disconnected");
            
            // Parar juego si no hay 2 jugadores
            if (players.size() < 2) {
                stopGameLoop();
            }
        } else {
            System.out.println("Display disconnected");
        }
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        Integer playerId = players.get(conn);
        String clientType = playerId != null ? "Player" + playerId : "Client";
        System.out.println("Message from " + clientType + ": " + message);
        
        JSONObject obj;
        try {
            obj = new JSONObject(message);
        } catch (Exception ex) {
            System.err.println("Invalid JSON from client");
            return;
        }

        String type = obj.optString(K_TYPE, "");
        
        JSONObject response = null;
        // Manejar los mensajes recibidos
        switch (type) {
            case T_URL: // Se solicita la url del server
                response = new JSONObject()
                    .put(K_TYPE, T_URL)
                    .put(K_VALUE, serverUrl);
                
                sendSafe(conn, response.toString());

                break;
                
            case T_GROUPNAME: // Se solicita el nombre del grupo
                response = new JSONObject()
                    .put(K_TYPE, T_GROUPNAME)
                    .put(K_VALUE, groupName);
                
                sendSafe(conn, response.toString());
                
                break;
            
            case T_CLIENT_CONFIRMATION: // Cliente confirma que está listo
                if (confirmedClients.containsKey(conn)) {
                    System.out.println("Client already confirmed, ignoring duplicate confirmation");
                    break;
                }
                
                // añadirlo a clientes confirmads
                confirmedClients.put(conn, true);
                
                String playerName = obj.optString("name", "Fabian");
                
                // Asignar playerId si hay espacio, sino se queda de display (raspberry)
                int assignedPlayerId;
                if (players.size() < 2) {
                    assignedPlayerId = players.size() + 1;
                    players.put(conn, assignedPlayerId);
                    playerNames.put(assignedPlayerId, playerName);
                    System.out.println("Player" + assignedPlayerId + " (" + playerName + ") confirmed");
                } else {
                    assignedPlayerId = 0; // Display
                    System.out.println("Display confirmed");
                }
                
                // Enviar id asignada del player
                response = new JSONObject()
                    .put(K_TYPE, T_PLAYER_ASSIGNED)
                    .put("playerId", assignedPlayerId);
                sendSafe(conn, response.toString());
                

                // Empezar juego si hay 2 jugadores
                if (players.size() >= 2) {
                    JSONObject cMessage = new JSONObject()
                        .put(K_TYPE, K_COUNTDOWN)
                        .put(K_NUMBER, 3);
                    broadcastToAll(cMessage.toString());
                    
                    // Enviar nombres de ambos jugadores
                    JSONObject namesMessage = new JSONObject()
                        .put(K_TYPE, T_PLAYER_NAMES)
                        .put("player1", playerNames.get(1))
                        .put("player2", playerNames.get(2));
                    broadcastToAll(namesMessage.toString());

                    try {
                        Thread.sleep(3000); // esperar 3 segundos
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    startGameLoop(); // empezar juego loop
                }
                break;
            
            case T_PADDLE_MOVE: // Movimiento de pala
                Integer pId = players.get(conn);
                if (pId != null) { // Solo si es jugador
                    float y = (float)obj.optDouble("y", 0.5);
                    gameState.setPaddleY(pId, y);
                    System.out.println("Player" + pId + " moved paddle to y=" + y);
                } else {
                    System.out.println("Non-player tried to move paddle, ignoring");
                }
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
            allClients.remove(conn);
            confirmedClients.remove(conn);
            players.remove(conn);
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

    // Iniciar game loop
    private void startGameLoop() {
        if (gameLoopThread != null && gameLoopThread.isAlive()) {
            return;
        }
        
        gameState.start();
        System.out.println("Game started!");
        
        gameLoopThread = new Thread(() -> {
            final int FPS = 60;
            final long frameTime = 1000 / FPS;
            
            while (gameState.isRunning() && players.size() >= 2) {
                long startTime = System.currentTimeMillis();
                
                // Actualizar estado del juego
                gameState.update();
                
                // el gameState a todos los clientes
                broadcastToAll(gameState.toJSON().toString());
                
                // Sleep para mantener FPS
                long elapsed = System.currentTimeMillis() - startTime;
                long sleepTime = frameTime - elapsed;
                if (sleepTime > 0) {
                    try {
                        Thread.sleep(sleepTime);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            
            gameState.stop();
            System.out.println("Game stopped.");
        });
        
        gameLoopThread.start();
    }

    
    // Parar game loop
    private void stopGameLoop() {
        gameState.stop();
        if (gameLoopThread != null) {
            gameLoopThread.interrupt();
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
