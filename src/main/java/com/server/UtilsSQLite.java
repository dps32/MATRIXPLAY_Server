package com.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Classe d'utilitats per gestionar la connexió i les operacions
 * amb una base de dades SQLite, aplicant bones pràctiques i logging amb SLF4J.
 */
public class UtilsSQLite {

    // 1. Instància del logger de SLF4J per a aquesta classe.
    private static final Logger logger = LoggerFactory.getLogger(UtilsSQLite.class);

    /**
     * Estableix una connexió amb la base de dades SQLite.
     *
     * @param filePath La ruta a l'arxiu de la base de dades.
     * @return L'objecte Connection.
     * @throws SQLException Si hi ha un error en la connexió.
     */
    public static Connection connect(String filePath) throws SQLException {
        String url = "jdbc:sqlite:" + filePath;
        Connection conn = DriverManager.getConnection(url);
        
        // S'ha substituït System.out.println per logger.info
        logger.info("BBDD SQLite connectada a {}", filePath);
        DatabaseMetaData meta = conn.getMetaData();
        logger.info("BBDD driver: {}", meta.getDriverName());
        
        return conn;
    }

    /**
     * Tanca la connexió a la base de dades.
     *
     * @param conn La connexió a tancar.
     */
    public static void disconnect(Connection conn) {
        if (conn != null) {
            try {
                conn.close();
                logger.info("DDBB SQLite desconnectada");
            } catch (SQLException e) {
                // S'ha substituït System.err.println per logger.error
                logger.error("Error en tancar la connexió: {}", e.getMessage());
            }
        }
    }

    /**
     * Llista totes les taules de la base de dades.
     *
     * @param conn La connexió.
     * @return Una llista amb els noms de les taules.
     * @throws SQLException Si hi ha un error de base de dades.
     */
    public static List<String> listTables(Connection conn) throws SQLException {
        List<String> tables = new ArrayList<>();
        try (ResultSet rs = conn.getMetaData().getTables(null, null, null, null)) {
            while (rs.next()) {
                tables.add(rs.getString("TABLE_NAME"));
            }
        }
        logger.debug("S'han trobat {} taules.", tables.size());
        return tables;
    }

    // =================================================================
    // Mètodes amb Statement (Vulnerables a Injecció SQL)
    // =================================================================

    /**
     * ⚠️ ATENCIÓ: Mètode vulnerable a injecció SQL.
     * Executa una consulta d'actualització (INSERT, UPDATE, DELETE).
     * Es recomana fer servir queryUpdatePS() per a més seguretat.
     *
     * @param conn La connexió a la BBDD.
     * @param sql La consulta SQL completa.
     * @return El nombre de files afectades.
     * @throws SQLException Si hi ha un error de base de dades.
     */
    public static int queryUpdate(Connection conn, String sql) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            int affectedRows = stmt.executeUpdate(sql);
            logger.debug("Executada queryUpdate. Files afectades: {}. SQL: {}", affectedRows, sql);
            return affectedRows;
        }
    }

    /**
     * ⚠️ ATENCIÓ: Mètode vulnerable a injecció SQL.
     * Executa una consulta de selecció (SELECT).
     * Qui cridi aquest mètode ÉS RESPONSABLE DE TANCAR el ResultSet retornat.
     *
     * @param conn La connexió a la BBDD.
     * @param sql La consulta SQL completa.
     * @return Un objecte ResultSet amb els resultats.
     * @throws SQLException Si hi ha un error de base de dades.
     */
    public static ResultSet querySelect(Connection conn, String sql) throws SQLException {
        Statement stmt = conn.createStatement();
        logger.debug("Executant querySelect. SQL: {}", sql);
        return stmt.executeQuery(sql);
    }
    
    // =================================================================
    // Mètodes amb PreparedStatement (Segurs i Recomanats)
    // =================================================================
    
    /**
     * Executa una consulta d'actualització (INSERT, UPDATE, DELETE) de forma segura.
     *
     * @param conn La connexió a la BBDD.
     * @param sql La consulta SQL amb marcadors de posició '?'.
     * @param params Els paràmetres per a la consulta.
     * @return El nombre de files afectades.
     * @throws SQLException Si hi ha un error de base de dades.
     */
    public static int queryUpdatePS(Connection conn, String sql, Object... params) throws SQLException {
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                pstmt.setObject(i + 1, params[i]);
            }
            int affectedRows = pstmt.executeUpdate();
            logger.debug("Executada queryUpdatePS. Files afectades: {}. SQL: {}", affectedRows, sql);
            return affectedRows;
        }
    }

    /**
     * Executa una consulta de selecció (SELECT) de forma segura.
     * Qui cridi aquest mètode ÉS RESPONSABLE DE TANCAR el ResultSet retornat.
     *
     * @param conn La connexió a la BBDD.
     * @param sql La consulta SQL amb marcadors de posició '?'.
     * @param params Els paràmetres per a la consulta.
     * @return Un objecte ResultSet amb els resultats.
     * @throws SQLException Si hi ha un error de base de dades.
     */
    public static ResultSet querySelectPS(Connection conn, String sql, Object... params) throws SQLException {
        PreparedStatement pstmt = conn.prepareStatement(sql);
        try {
            for (int i = 0; i < params.length; i++) {
                pstmt.setObject(i + 1, params[i]);
            }
            logger.debug("Executant querySelectPS. SQL: {}", sql);
            return pstmt.executeQuery();
        } catch (SQLException e) {
            pstmt.close();
            throw e;
        }
    }
}