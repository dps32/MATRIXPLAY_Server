# MatrixPlay Server

## 📡 Protocolo de Comunicación WebSocket

### Formato Base

Todos los mensajes siguen el formato JSON:

```json
{
  "type": "messageType"
  // ... campos específicos del mensaje
}
```

---

## 📥 Mensajes: Cliente → Servidor

### `url`

Envía una URL al servidor.

**Request:**

```json
{
  "type": "url",
  "data": "https://example.com"
}
```

**Comportamiento:**

- El servidor procesa la URL recibida
- Actualmente solo se registra en logs

---

## 📤 Mensajes: Servidor → Cliente

### `welcome`

Mensaje de bienvenida enviado cuando un cliente se conecta.

**Enviado:** Automáticamente al conectarse un nuevo cliente (broadcast a todos).

**Response:**

```json
{
  "type": "welcome",
  "message": "Hola"
}
```

---

## 🔧 Estructura del Proyecto

```
src/main/java/com/server/
├── Main.java           - Servidor WebSocket principal
└── ClientRegistry.java - Gestión de clientes conectados (DEPRECATED)
```

---

## 🔌 Sistema de Clientes

Los clientes se identifican mediante **IDs numéricos auto-incrementales**:

- Primer cliente: `Client#1`
- Segundo cliente: `Client#2`
- etc.

No se requiere autenticación ni registro previo.

---

## 🧪 Cliente de Prueba (JavaScript)

```javascript
const ws = new WebSocket("ws://localhost:3000");

ws.onopen = () => {
  console.log("Connected to server");

  // Enviar URL de ejemplo
  ws.send(
    JSON.stringify({
      type: "url",
      data: "https://example.com",
    })
  );
};

ws.onmessage = (event) => {
  const message = JSON.parse(event.data);
  console.log("Received:", message);

  switch (message.type) {
    case "welcome":
      console.log("Welcome message:", message.message);
      break;

    default:
      console.log("Unknown message type:", message.type);
  }
};

ws.onerror = (error) => {
  console.error("WebSocket error:", error);
};

ws.onclose = () => {
  console.log("Disconnected from server");
};
```

---

## 📊 Logs del Servidor

El servidor registra todas las conexiones y mensajes:

```
Client connected: Client#1
Message from Client#1: {"type":"url","data":"https://example.com"}
Client disconnected: Client#1
```

---

## ⚙️ Configuración

### Puerto del Servidor

Por defecto: `3000`

Para cambiar el puerto, modifica la constante en `Main.java`:

```java
public static final int DEFAULT_PORT = 3000;
```
