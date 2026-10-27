const http = require("node:http");
const os = require("node:os");
const express = require("express");
const { WebSocketServer } = require("ws");
const arena = require("./arena");
const pokeapi = require("./pokeapi");

const port = Number(process.env.PORT) || 3000;
// 0.0.0.0 acepta al simulador, al emulador de Android y a teléfonos en la misma red.
const host = "0.0.0.0";

const app = express();

app.get("/", (req, res) => {
  res.json({ servicio: "Arena Pokémon", websocket: `/arena`, ...arena.resumenEstado() });
});

// Para revisar desde el navegador que la PokeAPI responde.
app.get("/pokemon/aleatorios", async (req, res) => {
  try {
    const pokemones = await pokeapi.obtenerAleatorios(10);
    res.json(pokemones.map(pokeapi.resumen));
  } catch (error) {
    res.status(502).json({ error: "No se pudo consultar la PokeAPI." });
  }
});

const servidor = http.createServer(app);
const wss = new WebSocketServer({ server: servidor, path: "/arena" });

wss.on("connection", (socket) => {
  socket.vivo = true;
  socket.on("pong", () => (socket.vivo = true));
  arena.conectar(socket);
});

// Cierra las conexiones que dejaron de responder (por ejemplo, una app que se cerró de golpe).
const latido = setInterval(() => {
  for (const socket of wss.clients) {
    if (!socket.vivo) {
      socket.terminate();
      continue;
    }
    socket.vivo = false;
    socket.ping();
  }
}, 30000);
wss.on("close", () => clearInterval(latido));

function direccionesLocales() {
  return Object.values(os.networkInterfaces())
    .flat()
    .filter((d) => d.family === "IPv4" && !d.internal)
    .map((d) => d.address);
}

servidor.listen(port, host, () => {
  console.log(`Arena Pokémon en http://localhost:${port}`);
  console.log(`  Simulador de iOS:     127.0.0.1:${port}`);
  console.log(`  Emulador de Android:  10.0.2.2:${port}`);
  for (const ip of direccionesLocales()) {
    console.log(`  Teléfono en tu red:   ${ip}:${port}`);
  }
  pokeapi.cargarTipos().catch(() => {
    console.warn("No se pudieron cargar los tipos de la PokeAPI; se reintentará al jugar.");
  });
});
