package com.server;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.io.File;

public class Database {

    private static final String DB_PATH = "data/matrixplay.db";
    private Connection conn;
    private boolean isConnected = false;

    public void connect() throws SQLException {
        // Crear directorio data/ si no existe
        File dataDir = new File("data");
        if (!dataDir.exists()) {
            boolean created = dataDir.mkdirs();
            System.out.println("Data directory created: " + created);
        }
        
        // Conectar a la db
        String url = "jdbc:sqlite:" + DB_PATH;
        conn = DriverManager.getConnection(url);
        isConnected = true;
        System.out.println("Database connected: " + DB_PATH);
    }

    public void disconnect() {
        if (conn != null) {
            try {
                conn.close();
                isConnected = false;
                System.out.println("Database disconnected");
            } catch (SQLException e) {
                System.err.println("Error closing connection: " + e.getMessage());
            }
        }
    }

    // crear tablas si no existen
    public void createTables() throws SQLException {
        String createLoggingTable = """
            CREATE TABLE IF NOT EXISTS logging (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                timestamp DATETIME DEFAULT CURRENT_TIMESTAMP,
                event_type TEXT NOT NULL,
                message TEXT,
                player_id INTEGER,
                player_name TEXT
            )
            """;

        try (Statement stmt = conn.createStatement()) {
            stmt.execute(createLoggingTable);
            System.out.println("Tables created");
        }
    }

    public int updateQuery(String sql, Object... params) throws SQLException {
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                pstmt.setObject(i + 1, params[i]);
            }
            return pstmt.executeUpdate();
        }
    }

    public ResultSet selectQuery(String sql, Object... params) throws SQLException {
        PreparedStatement pstmt = conn.prepareStatement(sql);
        for (int i = 0; i < params.length; i++) {
            pstmt.setObject(i + 1, params[i]);
        }
        return pstmt.executeQuery();
    }

    public Connection getConnection() {
        return conn;
    }

    public void log(String eventType, String message, Integer playerId, String playerName) {
        if (!isConnected) {
            return; // Silenciosamente ignorar si DB no está disponible
        }
        try {
            updateQuery(
                "INSERT INTO logging (event_type, message, player_id, player_name) VALUES (?, ?, ?, ?)",
                eventType, message, playerId, playerName
            );
        } catch (SQLException e) {
            System.err.println("Error logging event: " + e.getMessage());
        }
    }

    public void log(String eventType, String message) {
        log(eventType, message, null, null);
    }
}
