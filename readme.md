# MatrixPlay Server

WebSocket server for a Pong game. Handles 2 players + displays.

## 🚀 Run

**Local:**

```powershell
.\run.ps1 com.server.Main
```

**Build:**

```powershell
.\run.ps1 com.server.Main build
```

**Deploy:**

```bash
cd proxmox/Windows
./proxmoxRunFromWindows.sh
```

## 📡 Messages

Port: `3000`. All messages are JSON. Positions go from 0.0 to 1.0.

### Client → Server

**Join game:**

```json
{
  "type": "clientConfirmation"
}
```

Response: `{ "type": "playerAssigned", "playerId": 1 }`

- First 2 clients = players (playerId 1 or 2)
- Others = displays (playerId 0)
- Game starts when 2 players join

**Move paddle:**

```json
{
  "type": "paddleMove",
  "y": 0.45
}
```

- `y` from 0.0 (top) to 1.0 (bottom)
- Only players can move paddles

**Get server URL:**

```json
{ "type": "url" }
```

**Get group name:**

```json
{ "type": "groupname" }
```

### Server → Client

**Welcome:**

```json
{ "type": "welcome", "message": "Hola" }
```

**Countdown:**

```json
{ "type": "countdown", "number": 3 }
```

**Game state (60/s):**

```json
{
  "type": "gameState",
  "ball": { "x": 0.5, "y": 0.3 },
  "paddle1": { "y": 0.4 },
  "paddle2": { "y": 0.6 },
  "score": { "player1": 2, "player2": 1 },
  "running": true
}
```

- All values from 0.0 to 1.0
- Multiply by screen size to get pixels

**Error:**

```json
{ "type": "error", "message": "..." }
```

## 🎮 Game Rules

- **2 players**: Left paddle (Player 1) vs Right paddle (Player 2)
- **Updates**: 60 times per second

## ⚙️ Config

Edit `src/main/resources/config.json`:

```json
{
  "serverUrl": "wss://matrixplay1.ieti.site:443",
  "groupName": "Schizofrenicy"
}
```
