package com.server;

import org.json.JSONObject;



public class GameState {
    
    // Posición y velocidad de la pelota
    private float ballX = 0.5f;
    private float ballY = 0.5f;
    private float ballVelX = 0.01f;
    private float ballVelY = 0.005f;
    
    // Posición de las palas
    private float paddle1Y = 0.5f;
    private float paddle2Y = 0.5f;
    
    // Tamaño de las palas
    private static final float PADDLE_HEIGHT = 0.2f;
    
    // Puntuaciones
    private int score1 = 0;
    private int score2 = 0;
    
    // Estado del juego
    private boolean gameRunning = false;
    private int pauseFrames = 0; // Contador para pausar después de reset
    
    public void update() {
        if (!gameRunning) return;
        
        // Si está en pausa, decrementar contador y no mover pelota
        if (pauseFrames > 0) {
            pauseFrames--;
            return;
        }
        
        // Mover pelota
        ballX += ballVelX;
        ballY += ballVelY;
        
        // Colisión con arriba y abajo
        if (ballY <= 0 || ballY >= 1.0f) {
            ballVelY = -ballVelY;
            ballY = Math.max(0, Math.min(1.0f, ballY));
        }
        
        // Colisión con pala izquierda (jugador 1)
        if (ballX <= 0.05f && ballY >= paddle1Y - PADDLE_HEIGHT/2 && ballY <= paddle1Y + PADDLE_HEIGHT/2) {
            ballVelX = Math.abs(ballVelX);
            ballX = 0.05f;
            
            // Calcular ángulo según posición de impacto
            float hitPosition = (ballY - paddle1Y) / (PADDLE_HEIGHT/2);
            ballVelY += hitPosition * 0.008f;
        }
        
        // Colisión con pala derecha (jugador 2)
        if (ballX >= 0.95f && ballY >= paddle2Y - PADDLE_HEIGHT/2 && ballY <= paddle2Y + PADDLE_HEIGHT/2) {
            ballVelX = -Math.abs(ballVelX);
            ballX = 0.95f;
            
            // Calcular ángulo según posición de impacto
            float hitPosition = (ballY - paddle2Y) / (PADDLE_HEIGHT/2);
            ballVelY += hitPosition * 0.008f;
        }
        
        // Puntos
        if (ballX < 0) {
            score2++;
            reset();
        } else if (ballX > 1.0f) {
            score1++;
            reset();
        }
    }
    
    // Resetear pelota al centro
    public void reset() {
        ballX = 0.5f;
        // Aparecer arriba (0.0) o abajo (1.0) aleatoriamente
        ballY = Math.random() > 0.5 ? 0.0f : 1.0f;
        
        // Dirección hacia izquierda (0.05, 0.5) o derecha (0.95, 0.5)
        boolean toRight = Math.random() > 0.5;
        float targetX = toRight ? 0.95f : 0.05f;
        float targetY = 0.5f;
        
        // Calcular vctor de dirección
        float dirX = targetX - ballX;
        float dirY = targetY - ballY;
        
        // Normalizar y escalar a velocidad deseada
        float length = (float)Math.sqrt(dirX * dirX + dirY * dirY);
        float speed = 0.01f;
        ballVelX = (dirX / length) * speed;
        ballVelY = (dirY / length) * speed;
        
        // Pausar 1 segundo (60 frames a 60 FPS)
        pauseFrames = 60;
    }
    

    // Iniciar juego
    public void start() {
        gameRunning = true;
        reset();
    }
    
    // Parar juego
    public void stop() {
        gameRunning = false;
    }


    
    // Setear posición de pala
    public void setPaddleY(int playerId, float y) {
        y = Math.max(PADDLE_HEIGHT/2, Math.min(1.0f - PADDLE_HEIGHT/2, y));
        if (playerId == 1) {
            paddle1Y = y;
        } else if (playerId == 2) {
            paddle2Y = y;
        }
    }
    

    // Convertir a JSON para broadcast
    public JSONObject toJSON() {
        JSONObject json = new JSONObject();
        json.put("type", "gameState");
        
        JSONObject ball = new JSONObject();
        ball.put("x", ballX);
        ball.put("y", ballY);
        json.put("ball", ball);
        
        JSONObject paddle1 = new JSONObject();
        paddle1.put("y", paddle1Y);
        json.put("paddle1", paddle1);
        
        JSONObject paddle2 = new JSONObject();
        paddle2.put("y", paddle2Y);
        json.put("paddle2", paddle2);
        
        JSONObject score = new JSONObject();
        score.put("player1", score1);
        score.put("player2", score2);
        json.put("score", score);
        
        json.put("running", gameRunning);
        
        return json;
    }

    
    public boolean isRunning() {
        return gameRunning;
    }

    
    public int getScore1() {
        return score1;
    }
    
    public int getScore2() {
        return score2;
    }
    

    // Resetear todo el juego
    public void resetGame() {
        score1 = 0;
        score2 = 0;
        paddle1Y = 0.5f;
        paddle2Y = 0.5f;
        pauseFrames = 0;
        reset();
        gameRunning = false;
    }
}
