package com.server;

import org.java_websocket.server.WebSocketServer;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.exceptions.WebsocketNotConnectedException;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;


// import com.shared.ClientData;
// import com.shared.GameObject;

/**
 * Servidor WebSocket que manté l'estat complet dels clients i objectes seleccionables.
 *
 * Protocol simplificat:
 *  - Client -> Server:  { "type": "clientData", "data": { ...ClientData... } }
 *  - Server -> Clients: { "type": "state", "clientId": <clientId>, "clients": [ ...ClientData... ], "gameObjects": { ... }, "countdown": n? }
 */
public class Main extends WebSocketServer {

    /** Port per defecte on escolta el servidor. */
    public static final int DEFAULT_PORT = 3000;

    /** Llista de noms disponibles per als clients connectats. */
    private static final List<String> PLAYER_NAMES = Arrays.asList(
        "Bulbasaur", "Charizard", "Blaziken", "Umbreon", "Mewtwo", "Pikachu", "Wartortle"
    );

    /** Llista de colors disponibles per als clients connectats. */
    private static final List<String> PLAYER_COLORS = Arrays.asList(
        "GREEN", "ORANGE", "RED", "GRAY", "PURPLE", "YELLOW", "BLUE"
    );

    /** Nombre de clients necessaris per iniciar el compte enrere. */
    private static final int REQUIRED_CLIENTS = 2;

    // Claus JSON
    private static final String K_TYPE = "type";
    private static final String K_VALUE = "value";
    private static final String K_CLIENT_NAME = "clientName";
    private static final String K_CLIENTS_LIST = "clientsList";             
    private static final String K_OBJECTS_LIST = "objectsList"; 

    // Tipus de missatge nous i (alguns) heretats
    private static final String T_CLIENT_MOUSE_MOVING = "clientMouseMoving";  // client -> server
    private static final String T_CLIENT_OBJECT_MOVING = "clientObjectMoving";// client -> server
    private static final String T_SERVER_DATA = "serverData";                 // server -> clients
    private static final String T_COUNTDOWN = "countdown";                    // server -> clients

    /** Registre de clients i assignació de noms (pool integrat). */
    private final ClientRegistry clients;

    /** Mapa d’estat per client (source of truth del servidor). Clau = name/id. */
    // private final Map<String, ClientData> clientsData = new HashMap<>();

    /** Mapa d'objectes seleccionables compartits. */
    // private final Map<String, GameObject> gameObjects = new HashMap<>();

    private volatile boolean countdownRunning = false;

    /** Freqüència d’enviament de l’estat (frames per segon). */
    private static final int SEND_FPS = 30;
    private final ScheduledExecutorService ticker;

    /**
     * Crea un servidor WebSocket que escolta a l'adreça indicada.
     *
     * @param address adreça i port d'escolta del servidor
     */
    public Main(InetSocketAddress address) {
        super(address);
        this.clients = new ClientRegistry(PLAYER_NAMES);
        initializegameObjects();

        ThreadFactory tf = r -> {
            Thread t = new Thread(r, "ServerTicker");
            t.setDaemon(true);
            return t;
        };
        this.ticker = Executors.newSingleThreadScheduledExecutor(tf);
    }

    /**
     * Inicialitza els objectes seleccionables predefinits.
     */
    private void initializegameObjects() {
        // String objId = "O0";
        // GameObject obj0 = new GameObject(objId, 300, 50, 4, 1);
        // gameObjects.put(objId, obj0);

        // objId = "O1";
        // GameObject obj1 = new GameObject(objId, 300, 100, 1, 3);
        // gameObjects.put(objId, obj1);
    }

    /**
     * Obté el color per un nom de client.
     *
     * @return color assignat
     */
    private synchronized String getColorForName(String name) {
        int idx = PLAYER_NAMES.indexOf(name);
        if (idx < 0) idx = 0; // fallback si el nom no està a la llista
        return PLAYER_COLORS.get(idx % PLAYER_COLORS.size());
    }

    /** Envia un compte enrere (5..0) com a part del mateix STATE.
     *  Evita comptes simultanis i es cancel·la si baixa el nombre de clients. */
    private void sendCountdown() {
        synchronized (this) {
            if (countdownRunning) return;
            if (clients.snapshot().size() != REQUIRED_CLIENTS) return;
            countdownRunning = true;
        }

        new Thread(() -> {
            try {
                for (int i = 5; i >= 0; i--) {
                    // Si durant el compte enrere ja no hi ha els clients requerits, cancel·la
                    if (clients.snapshot().size() < REQUIRED_CLIENTS) {
                        break;
                    }

                    sendCountdownToAll(i);
                    if (i > 0) Thread.sleep(750); // ritme del compte enrere
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            } finally {
                countdownRunning = false;
            }
        }, "CountdownThread").start();
    }

    // ----------------- Helpers JSON -----------------

    /** Crea un objecte JSON amb el camp type inicialitzat. */
    private static JSONObject msg(String type) {
        return new JSONObject().put(K_TYPE, type);
    }

    /** Envia de forma segura un payload i, si el socket no està connectat, el neteja del registre. */
    private void sendSafe(WebSocket to, String payload) {
        if (to == null) return;
        try {
            to.send(payload);
        } catch (WebsocketNotConnectedException e) {
            String name = clients.cleanupDisconnected(to);
            // clientsData.remove(name);
            System.out.println("Client desconnectat durant send: " + name);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** Envia un missatge a tots els clients excepte l'emissor. */
    private void broadcastExcept(WebSocket sender, String payload) {
        for (Map.Entry<WebSocket, String> e : clients.snapshot().entrySet()) {
            WebSocket conn = e.getKey();
            if (!Objects.equals(conn, sender)) sendSafe(conn, payload);
        }
    }

    private void broadcastStatus() {

        JSONArray arrClients = new JSONArray();
        // for (ClientData c : clientsData.values()) {
        //     arrClients.put(c.toJSON());
        // }

        JSONArray arrObjects = new JSONArray();
        // for (GameObject obj : gameObjects.values()) {
        //     arrObjects.put(obj.toJSON());
        // }

        JSONObject rst = msg(T_SERVER_DATA)
                        .put(K_CLIENTS_LIST, arrClients)
                        .put(K_OBJECTS_LIST, arrObjects);

        for (Map.Entry<WebSocket, String> e : clients.snapshot().entrySet()) {
            WebSocket conn = e.getKey();
            String name = clients.nameBySocket(conn);
            rst.put(K_CLIENT_NAME, name);
            sendSafe(conn, rst.toString());
        }
    }

    /** Envia a tots els clients el compte enrere. */
    private void sendCountdownToAll(int n) {
        JSONObject rst = msg(T_COUNTDOWN).put(K_VALUE, n);
        broadcastExcept(null, rst.toString());
    }

    // ----------------- WebSocketServer overrides -----------------

    /** Assigna un nom i color al client i envia l’STATE complet. */
    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        String name = clients.add(conn);
        String color = getColorForName(name);

        // clientsData.put(name, new ClientData(name, color));

        System.out.println("WebSocket client connected: " + name + " (" + color + ")");
        sendCountdown();
    }

    /** Elimina el client del registre i envia l’STATE complet. */
    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        String name = clients.remove(conn);
        // clientsData.remove(name);
        System.out.println("WebSocket client disconnected: " + name);
    }

    /** Processa els missatges rebuts. */
    @Override
    public void onMessage(WebSocket conn, String message) {
        JSONObject obj;
        try {
            obj = new JSONObject(message);
        } catch (Exception ex) {
            return; // JSON invàlid
        }

        String type = obj.optString(K_TYPE, "");
        switch (type) {
            case T_CLIENT_MOUSE_MOVING -> {
                // String clientName = clients.nameBySocket(conn);
                // clientsData.put(clientName, ClientData.fromJSON(obj.getJSONObject(K_VALUE))); 
            }

            case T_CLIENT_OBJECT_MOVING -> {
                // GameObject objData = GameObject.fromJSON(obj.getJSONObject(K_VALUE));
                // gameObjects.put(objData.id, objData);
            }

            default -> {
                // Ignora altres tipus
            }
        }
    }

    /** Log d'error global o de socket concret. */
    @Override
    public void onError(WebSocket conn, Exception ex) {
        ex.printStackTrace();
    }

    /** Arrencada: log i configuració del timeout de connexió perduda. */
    @Override
    public void onStart() {
        System.out.println("WebSocket server started on port: " + getPort());
        setConnectionLostTimeout(100);
        startTicker();
    }

    // ----------------- Lifecycle util -----------------

    /** Registra un shutdown hook per aturar netament el servidor en finalitzar el procés. */
    private static void registerShutdownHook(Main server) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Aturant servidor (shutdown hook)...");
            try {
                server.stopTicker();      // <- atura el bucle periòdic
                server.stop(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
                Thread.currentThread().interrupt();
            }
            System.out.println("Servidor aturat.");
        }));
    }

    /** Bloqueja el fil principal indefinidament fins que sigui interromput. */
    private static void awaitForever() {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            latch.await();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }


    // ----------------- Ticker util -----------------

    private void startTicker() {
        long periodMs = Math.max(1, 1000 / SEND_FPS);
        ticker.scheduleAtFixedRate(() -> {
            try {
                // Opcional: si no hi ha clients, evita enviar
                if (!clients.snapshot().isEmpty()) {
                    broadcastStatus();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, 0, periodMs, TimeUnit.MILLISECONDS);
    }

    private void stopTicker() {
        try {
            ticker.shutdownNow();
            ticker.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    /** Punt d'entrada. */
    public static void main(String[] args) {
        Main server = new Main(new InetSocketAddress(DEFAULT_PORT));
        server.start();
        registerShutdownHook(server);

        System.out.println("Server running on port " + DEFAULT_PORT + ". Press Ctrl+C to stop it.");
        awaitForever();
    }
}
