// Fila de espera, partidas y protocolo WebSocket de la arena.
const { WebSocket } = require("ws");
const pokeapi = require("./pokeapi");
const { crearCombatiente, resolverTurno, entrada } = require("./combate");
const { crearCPU } = require("./cpu");

const CANTIDAD_OPCIONES = 10;
const TAMANO_EQUIPO = 3;
const LARGO_NOMBRE = 16;
// Si en este tiempo no llega otro jugador, el que espera juega contra la CPU.
const ESPERA_RIVAL_MS = 10000;
// Tiempo para elegir cada movimiento, contado desde que la app termina de narrar el turno.
// Quien no elige a tiempo ataca al azar.
const TIEMPO_TURNO_MS = 10000;
// Pausas con que las apps narran cada evento (las mismas en iOS y Android).
const PAUSA_ATAQUE_MS = 450;
const PAUSA_EVENTO_MS = 900;
// Holgura para la red antes de elegir por el jugador.
const MARGEN_MS = 1500;

const enEspera = [];
const partidas = new Set();

function enviar(socket, mensaje) {
  if (socket.readyState === WebSocket.OPEN) {
    socket.send(JSON.stringify(mensaje));
  }
}

function enviarError(socket, mensaje) {
  enviar(socket, { tipo: "error", mensaje });
}

function conectar(socket) {
  socket.partida = null;
  socket.on("message", (datos) => {
    let mensaje;
    try {
      mensaje = JSON.parse(datos);
    } catch {
      return enviarError(socket, "Mensaje inválido.");
    }
    manejar(socket, mensaje).catch((error) => {
      console.error(error);
      enviarError(socket, "Ocurrió un error en el servidor.");
    });
  });
  socket.on("close", () => salir(socket));
}

async function manejar(socket, mensaje) {
  switch (mensaje?.tipo) {
    case "unirse":
      return unirse(socket, mensaje.nombre);
    case "jugarCPU":
      return jugarContraCPU(socket, mensaje.nombre);
    case "elegirEquipo":
      return elegirEquipo(socket, mensaje.ids);
    case "accion":
      return recibirAccion(socket, mensaje);
    case "salir":
      return salir(socket);
    default:
      return enviarError(socket, "Tipo de mensaje desconocido.");
  }
}

function limpiarNombre(nombre) {
  const limpio = typeof nombre === "string" ? nombre.trim().slice(0, LARGO_NOMBRE) : "";
  return limpio || "Entrenador";
}

function unirse(socket, nombre) {
  salir(socket);
  socket.nombre = limpiarNombre(nombre);
  enEspera.push(socket);
  enviar(socket, { tipo: "esperando", segundos: ESPERA_RIVAL_MS / 1000 });
  socket.esperaCPU = setTimeout(() => {
    sacarDeLaFila(socket);
    iniciarContraCPU(socket).catch(console.error);
  }, ESPERA_RIVAL_MS);
  emparejar();
}

function jugarContraCPU(socket, nombre) {
  salir(socket);
  socket.nombre = limpiarNombre(nombre);
  return iniciarContraCPU(socket);
}

// Sin fila de espera: el rival es la CPU del servidor, que contesta por el mismo protocolo.
function iniciarContraCPU(socket) {
  const cpu = crearCPU((jugador, mensaje) => manejar(jugador, mensaje).catch(console.error));
  return iniciarPartida([socket, cpu]);
}

function sacarDeLaFila(socket) {
  clearTimeout(socket.esperaCPU);
  socket.esperaCPU = null;
  const posicion = enEspera.indexOf(socket);
  if (posicion !== -1) {
    enEspera.splice(posicion, 1);
  }
}

// Junta a los dos primeros de la fila.
async function emparejar() {
  while (enEspera.length >= 2) {
    const pareja = enEspera.slice(0, 2);
    pareja.forEach(sacarDeLaFila);
    await iniciarPartida(pareja);
  }
}

// Crea la partida y les reparte 10 Pokémon a cada jugador.
async function iniciarPartida(sockets) {
  const nombres = sockets.map((s) => s.nombre);
  if (nombres[0] === nombres[1]) {
    sockets[1].nombre = nombres[1] = `${nombres[1]} 2`;
  }

  const partida = {
    fase: "cargando",
    turno: 0,
    jugadores: sockets.map((socket) => ({
      socket,
      nombre: socket.nombre,
      opciones: [],
      equipo: null,
      cargando: false,
      activo: 0,
      accion: null
    }))
  };
  sockets.forEach((socket, indice) => {
    socket.partida = partida;
    socket.indice = indice;
  });
  partidas.add(partida);
  console.log(`Nueva partida: ${nombres[0]} contra ${nombres[1]}`);

  try {
    const opciones = await Promise.all(sockets.map(() => pokeapi.obtenerAleatorios(CANTIDAD_OPCIONES)));
    if (partida.fase !== "cargando") {
      return;
    }
    partida.fase = "eligiendo";
    partida.jugadores.forEach((jugador, i) => {
      jugador.opciones = opciones[i];
      enviar(jugador.socket, {
        tipo: "partida",
        nombre: jugador.nombre,
        rival: partida.jugadores[1 - i].nombre,
        opciones: opciones[i].map(pokeapi.resumen)
      });
    });
  } catch (error) {
    console.error(error);
    terminar(partida);
    sockets.forEach((socket) => enviarError(socket, "No se pudo consultar la PokeAPI. Intenta de nuevo."));
  }
}

async function elegirEquipo(socket, ids) {
  const partida = socket.partida;
  if (!partida || partida.fase !== "eligiendo") {
    return enviarError(socket, "No estás eligiendo equipo.");
  }
  const jugador = partida.jugadores[socket.indice];
  if (jugador.equipo || jugador.cargando) {
    return enviarError(socket, "Ya elegiste tu equipo.");
  }

  const elegidos = Array.isArray(ids)
    ? ids.map((id) => jugador.opciones.find((p) => p.id === id))
    : [];
  if (elegidos.length !== TAMANO_EQUIPO ||
      elegidos.includes(undefined) ||
      new Set(ids).size !== TAMANO_EQUIPO) {
    return enviarError(socket, `Elige ${TAMANO_EQUIPO} Pokémon distintos de tus opciones.`);
  }

  jugador.cargando = true;
  try {
    const movimientos = await Promise.all(elegidos.map((p) => pokeapi.obtenerMovimientos(p)));
    jugador.equipo = elegidos.map((p, i) => crearCombatiente(p, movimientos[i]));
  } catch (error) {
    console.error(error);
    return enviarError(socket, "No se pudieron cargar los ataques. Intenta de nuevo.");
  } finally {
    jugador.cargando = false;
  }

  if (partida.fase !== "eligiendo") {
    return;
  }
  const rival = partida.jugadores[1 - socket.indice];
  if (!rival.equipo) {
    return enviar(socket, { tipo: "esperandoRival" });
  }

  partida.fase = "combate";
  partida.turno = 1;
  const eventos = [{ texto: "¡Comienza el combate!" }, entrada(partida.jugadores, 0), entrada(partida.jugadores, 1)];
  enviarATodos(partida, "inicioCombate", eventos);
  iniciarReloj(partida, eventos);
}

// El reloj del turno empieza a contar cuando las apps terminan de narrar los eventos.
function iniciarReloj(partida, eventos) {
  clearTimeout(partida.reloj);
  const narracion = eventos.reduce((total, e) => total + (e.efecto === "ataque" ? PAUSA_ATAQUE_MS : PAUSA_EVENTO_MS), 0);
  partida.reloj = setTimeout(() => agotarTiempo(partida), narracion + TIEMPO_TURNO_MS + MARGEN_MS);
}

function agotarTiempo(partida) {
  if (partida.fase !== "combate") {
    return;
  }
  const avisos = [];
  for (const jugador of partida.jugadores) {
    if (!jugador.accion) {
      const pokemon = jugador.equipo[jugador.activo];
      jugador.accion = { accion: "atacar", movimiento: Math.floor(Math.random() * pokemon.movimientos.length) };
      avisos.push({ texto: `${jugador.nombre} tardó demasiado: se eligió un ataque al azar.` });
    }
  }
  resolver(partida, avisos);
}

function recibirAccion(socket, mensaje) {
  const partida = socket.partida;
  if (!partida || partida.fase !== "combate") {
    return enviarError(socket, "No hay un combate en curso.");
  }
  const jugador = partida.jugadores[socket.indice];
  if (jugador.accion) {
    return enviarError(socket, "Ya elegiste tu acción en este turno.");
  }

  const pokemon = jugador.equipo[jugador.activo];
  if (mensaje.accion === "atacar") {
    if (!Number.isInteger(mensaje.movimiento) ||
        mensaje.movimiento < 0 ||
        mensaje.movimiento >= pokemon.movimientos.length) {
      return enviarError(socket, "Ese ataque no existe.");
    }
    jugador.accion = { accion: "atacar", movimiento: mensaje.movimiento };
  } else if (mensaje.accion === "cambiar") {
    const destino = jugador.equipo[mensaje.indice];
    if (!Number.isInteger(mensaje.indice) || !destino || destino.debilitado || mensaje.indice === jugador.activo) {
      return enviarError(socket, "No puedes cambiar a ese Pokémon.");
    }
    jugador.accion = { accion: "cambiar", indice: mensaje.indice };
  } else {
    return enviarError(socket, "Acción desconocida.");
  }

  const rival = partida.jugadores[1 - socket.indice];
  if (!rival.accion) {
    return enviar(socket, { tipo: "accionRecibida" });
  }
  resolver(partida);
}

// Ya están las dos acciones: resuelve el turno. `avisos` va antes de los eventos del turno.
function resolver(partida, avisos = []) {
  clearTimeout(partida.reloj);
  const acciones = partida.jugadores.map((j) => j.accion);
  partida.jugadores.forEach((j) => (j.accion = null));
  const resultado = resolverTurno(partida.jugadores, acciones, pokeapi.obtenerTablaTipos());
  const eventos = [...avisos, ...resultado.eventos];
  const { ganador } = resultado;

  if (ganador === null) {
    partida.turno += 1;
    enviarATodos(partida, "turno", eventos);
    iniciarReloj(partida, eventos);
  } else {
    enviarATodos(partida, "fin", eventos, partida.jugadores[ganador].nombre);
    console.log(`Fin de partida: ganó ${partida.jugadores[ganador].nombre}`);
    terminar(partida);
  }
}

// Si alguien se va a media partida, el rival gana.
function salir(socket) {
  sacarDeLaFila(socket);

  const partida = socket.partida;
  if (!partida || partida.fase === "fin") {
    return;
  }
  const rival = partida.jugadores[1 - socket.indice];
  const eventos = [{ texto: `${socket.nombre} abandonó la partida.` }];
  if (partida.fase === "combate") {
    enviar(rival.socket, { tipo: "fin", ganador: rival.nombre, eventos, estado: estadoPara(partida, 1 - socket.indice) });
  } else {
    enviar(rival.socket, { tipo: "fin", ganador: rival.nombre, eventos, estado: null });
  }
  console.log(`${socket.nombre} abandonó; gana ${rival.nombre}`);
  terminar(partida);
}

function terminar(partida) {
  clearTimeout(partida.reloj);
  partida.fase = "fin";
  partidas.delete(partida);
  for (const jugador of partida.jugadores) {
    if (jugador.socket.partida === partida) {
      jugador.socket.partida = null;
    }
  }
}

function enviarATodos(partida, tipo, eventos, ganador) {
  partida.jugadores.forEach((jugador, i) => {
    const mensaje = { tipo, eventos: eventosPara(partida, eventos, i), estado: estadoPara(partida, i) };
    if (ganador !== undefined) {
      mensaje.ganador = ganador;
    } else {
      mensaje.tiempo = TIEMPO_TURNO_MS / 1000;
    }
    enviar(jugador.socket, mensaje);
  });
}

// Un Pokémon visto por las apps. Los ataques solo se envían a su dueño.
function vista(pokemon, propio) {
  const datos = {
    ...pokeapi.resumen(pokemon),
    psMax: pokemon.psMax,
    ps: pokemon.ps,
    debilitado: pokemon.debilitado
  };
  if (propio) {
    datos.movimientos = pokemon.movimientos;
  }
  return datos;
}

// El estado desde la perspectiva del jugador `i`. Los Pokémon del rival que no han salido quedan ocultos.
function estadoPara(partida, i) {
  const yo = partida.jugadores[i];
  const rival = partida.jugadores[1 - i];
  return {
    turno: partida.turno,
    yo: {
      nombre: yo.nombre,
      activo: yo.activo,
      equipo: yo.equipo.map((p) => vista(p, true))
    },
    rival: {
      nombre: rival.nombre,
      activo: vista(rival.equipo[rival.activo], false),
      vivos: rival.equipo.filter((p) => !p.debilitado).length,
      total: rival.equipo.length
    }
  };
}

// Traduce "jugador 0/1" de los eventos a "yo"/"rival" para quien los recibe.
function eventosPara(partida, eventos, i) {
  return eventos.map((evento) => {
    const salida = { texto: evento.texto };
    if (evento.jugador !== undefined) {
      const propio = evento.jugador === i;
      salida.lado = propio ? "yo" : "rival";
      salida.ps = evento.ps;
      for (const campo of ["efecto", "tipo", "danio", "efectividad", "critico"]) {
        if (evento[campo] !== undefined) {
          salida[campo] = evento[campo];
        }
      }
      if (evento.entra) {
        const pokemon = partida.jugadores[evento.jugador].equipo[evento.indice];
        salida.pokemon = { ...vista(pokemon, propio), ps: evento.ps, debilitado: false };
        if (propio) {
          salida.indice = evento.indice;
        }
      }
    }
    return salida;
  });
}

function resumenEstado() {
  return { jugadoresEnEspera: enEspera.length, partidasActivas: partidas.size };
}

module.exports = { conectar, resumenEstado, ESPERA_RIVAL_MS, TIEMPO_TURNO_MS, PAUSA_EVENTO_MS, MARGEN_MS };
