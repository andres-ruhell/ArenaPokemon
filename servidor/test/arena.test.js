const test = require("node:test");
const assert = require("node:assert/strict");
const { WebSocket } = require("ws");
const pokeapi = require("../pokeapi");
const arena = require("../arena");

// Pokémon y ataques de mentira para no consultar la PokeAPI.
const stats = { ps: 100, ataque: 50, defensa: 50, ataqueEspecial: 50, defensaEspecial: 50, velocidad: 50 };
const placaje = { nombre: "Placaje", tipo: { clave: "normal", nombre: "Normal" }, poder: 40, precision: 100, clase: "fisico" };
pokeapi.obtenerAleatorios = async (cantidad) =>
  Array.from({ length: cantidad }, (_, i) => ({ id: i + 1, nombre: `Pokémon ${i + 1}`, tipos: [], stats }));
pokeapi.obtenerMovimientos = async () => [placaje];
pokeapi.obtenerTablaTipos = () => ({});

// Un WebSocket falso: guarda lo que el servidor le manda y deja simular mensajes de la app.
function jugador(nombre) {
  const manejadores = {};
  const socket = {
    readyState: WebSocket.OPEN,
    recibidos: [],
    send: (texto) => socket.recibidos.push(JSON.parse(texto)),
    on: (evento, manejador) => (manejadores[evento] = manejador),
    decir: (mensaje) => manejadores.message(JSON.stringify(mensaje)),
    cerrar: () => manejadores.close()
  };
  arena.conectar(socket);
  socket.decir({ tipo: "unirse", nombre });
  return socket;
}

const mensajes = (socket, tipo) => socket.recibidos.filter((m) => m.tipo === tipo);
// Deja correr las promesas pendientes (la carga de Pokémon y ataques).
const procesar = () => new Promise((listo) => setImmediate(listo));

test.beforeEach((t) => {
  t.mock.timers.enable({ apis: ["setTimeout"] });
  t.mock.method(console, "log", () => {});
});

test("avisa cuántos segundos espera y, si nadie llega, lo pone contra la CPU", async (t) => {
  const ash = jugador("Ash");
  assert.deepEqual(ash.recibidos[0], { tipo: "esperando", segundos: arena.ESPERA_RIVAL_MS / 1000 });

  t.mock.timers.tick(arena.ESPERA_RIVAL_MS - 1);
  await procesar();
  assert.equal(mensajes(ash, "partida").length, 0);

  t.mock.timers.tick(1);
  await procesar();
  assert.equal(mensajes(ash, "partida").length, 1);
  assert.equal(mensajes(ash, "partida")[0].rival, "CPU");
  assert.equal(arena.resumenEstado().jugadoresEnEspera, 0);
  ash.cerrar();
});

test("dos jugadores que llegan a tiempo se enfrentan entre sí, sin CPU", async (t) => {
  const ash = jugador("Ash");
  t.mock.timers.tick(arena.ESPERA_RIVAL_MS / 2);
  const misty = jugador("Misty");
  await procesar();

  t.mock.timers.tick(arena.ESPERA_RIVAL_MS);
  await procesar();
  assert.equal(mensajes(ash, "partida").length, 1);
  assert.equal(mensajes(ash, "partida")[0].rival, "Misty");
  assert.equal(mensajes(misty, "partida").length, 1);
  assert.equal(mensajes(misty, "partida")[0].rival, "Ash");
  ash.cerrar();
  misty.cerrar();
});

test("quien sale de la fila antes de tiempo no recibe partida contra la CPU", async (t) => {
  const ash = jugador("Ash");
  ash.cerrar();
  t.mock.timers.tick(arena.ESPERA_RIVAL_MS);
  await procesar();
  assert.equal(mensajes(ash, "partida").length, 0);
  assert.equal(arena.resumenEstado().jugadoresEnEspera, 0);
});

// Junta a dos jugadores y los lleva hasta el primer turno del combate.
async function combate() {
  const ash = jugador("Ash");
  const misty = jugador("Misty");
  await procesar();
  ash.decir({ tipo: "elegirEquipo", ids: [1, 2, 3] });
  misty.decir({ tipo: "elegirEquipo", ids: [4, 5, 6] });
  await procesar();
  return { ash, misty };
}

// "¡Comienza el combate!" y la entrada de cada Pokémon: tres eventos que las apps narran antes de dejar elegir.
const narracionInicial = 3 * arena.PAUSA_EVENTO_MS;

test("cada turno avisa cuánto tiempo hay para elegir", async () => {
  const { ash, misty } = await combate();
  const inicio = mensajes(ash, "inicioCombate");
  assert.equal(inicio.length, 1);
  assert.equal(inicio[0].tiempo, arena.TIEMPO_TURNO_MS / 1000);
  ash.cerrar();
  misty.cerrar();
});

test("si un jugador no elige a tiempo, el servidor ataca al azar por él", async (t) => {
  const { ash, misty } = await combate();
  ash.decir({ tipo: "accion", accion: "atacar", movimiento: 0 });

  t.mock.timers.tick(narracionInicial + arena.TIEMPO_TURNO_MS + arena.MARGEN_MS - 1);
  assert.equal(mensajes(ash, "turno").length, 0);

  t.mock.timers.tick(1);
  const turno = mensajes(ash, "turno");
  assert.equal(turno.length, 1);
  assert.equal(turno[0].eventos[0].texto, "Misty tardó demasiado: se eligió un ataque al azar.");
  assert.ok(turno[0].eventos.some((e) => e.texto === "Pokémon 4 de Misty usó Placaje."));
  ash.cerrar();
  misty.cerrar();
});

test("si los dos eligen a tiempo, el turno se resuelve en ese momento y nadie ataca al azar", async () => {
  const { ash, misty } = await combate();
  ash.decir({ tipo: "accion", accion: "atacar", movimiento: 0 });
  misty.decir({ tipo: "accion", accion: "atacar", movimiento: 0 });

  const turno = mensajes(ash, "turno");
  assert.equal(turno.length, 1);
  assert.ok(!turno[0].eventos.some((e) => e.texto.includes("tardó demasiado")));
  ash.cerrar();
  misty.cerrar();
});
