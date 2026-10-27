// Rival controlado por el servidor. Se comporta como el WebSocket de una app: recibe los
// mismos mensajes (partida, turno…) y contesta con elegirEquipo y accion.
const { WebSocket } = require("ws");
const pokeapi = require("./pokeapi");
const { efectividad } = require("./combate");

const NOMBRE = "CPU";
const PAUSA_EQUIPO = 1500;
const PAUSA_TURNO = 900;
const PROBABILIDAD_MEJOR_ATAQUE = 0.75;

// `responder(cpu, mensaje)` entrega su respuesta a la arena como si viniera de una app.
function crearCPU(responder) {
  const cpu = {
    nombre: NOMBRE,
    readyState: WebSocket.OPEN,
    partida: null,
    send(texto) {
      const mensaje = JSON.parse(texto);
      if (mensaje.tipo === "partida") {
        setTimeout(() => responder(cpu, { tipo: "elegirEquipo", ids: elegirEquipo(mensaje.opciones) }), PAUSA_EQUIPO);
      } else if (mensaje.tipo === "inicioCombate" || mensaje.tipo === "turno") {
        setTimeout(() => responder(cpu, { tipo: "accion", ...elegirAccion(mensaje.estado) }), PAUSA_TURNO);
      }
    }
  };
  return cpu;
}

// Tres al azar entre los cinco con más stats en total.
function elegirEquipo(opciones, aleatorio = Math.random) {
  const total = (pokemon) => Object.values(pokemon.stats).reduce((suma, valor) => suma + valor, 0);
  const mejores = [...opciones].sort((a, b) => total(b) - total(a)).slice(0, 5);
  return pokeapi.revolver(mejores, aleatorio).slice(0, 3).map((pokemon) => pokemon.id);
}

// Daño esperado de un ataque contra el Pokémon activo del rival, sin contar las stats.
function valorDeAtaque(pokemon, movimiento, rival, tabla) {
  const stab = pokemon.tipos.some((t) => t.clave === movimiento.tipo.clave) ? 1.5 : 1;
  const precision = (movimiento.precision ?? 100) / 100;
  const multiplicador = efectividad(movimiento.tipo.clave, rival.tipos.map((t) => t.clave), tabla);
  return movimiento.poder * precision * stab * multiplicador;
}

// Casi siempre usa su mejor ataque; a veces se equivoca para que se le pueda ganar.
// Si ningún ataque le hace daño al rival, cambia a un compañero que sí pueda.
function elegirAccion(estado, tabla = pokeapi.obtenerTablaTipos(), aleatorio = Math.random) {
  const { equipo, activo } = estado.yo;
  const rival = estado.rival.activo;
  const valores = equipo[activo].movimientos.map((m) => valorDeAtaque(equipo[activo], m, rival, tabla));
  const mejor = valores.indexOf(Math.max(...valores));

  if (valores[mejor] === 0) {
    const util = equipo.findIndex((pokemon, i) =>
      i !== activo && !pokemon.debilitado &&
      pokemon.movimientos.some((m) => valorDeAtaque(pokemon, m, rival, tabla) > 0));
    if (util !== -1) {
      return { accion: "cambiar", indice: util };
    }
  }

  const movimiento = aleatorio() < PROBABILIDAD_MEJOR_ATAQUE
    ? mejor
    : Math.floor(aleatorio() * valores.length);
  return { accion: "atacar", movimiento };
}

module.exports = { crearCPU, elegirEquipo, elegirAccion };
