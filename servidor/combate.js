// Reglas del combate. Funciones puras: reciben `aleatorio` para poder probarlas.
const NIVEL = 50;
const PROBABILIDAD_CRITICO = 1 / 24;

// Stats al nivel 50 sin entrenamiento: PS = base + 60, el resto = base + 5.
function crearCombatiente(pokemon, movimientos) {
  const base = pokemon.stats;
  const psMax = base.ps + NIVEL + 10;
  return {
    ...pokemon,
    movimientos,
    psMax,
    ps: psMax,
    debilitado: false,
    ataque: base.ataque + 5,
    defensa: base.defensa + 5,
    ataqueEspecial: base.ataqueEspecial + 5,
    defensaEspecial: base.defensaEspecial + 5,
    velocidad: base.velocidad + 5
  };
}

function efectividad(tipoAtaque, tiposDefensor, tabla) {
  const relaciones = tabla[tipoAtaque];
  if (!relaciones) {
    return 1;
  }
  return tiposDefensor.reduce((total, tipo) => {
    if (relaciones.nulo.includes(tipo)) return 0;
    if (relaciones.doble.includes(tipo)) return total * 2;
    if (relaciones.mitad.includes(tipo)) return total * 0.5;
    return total;
  }, 1);
}

function calcularDanio(atacante, defensor, movimiento, tabla, aleatorio = Math.random) {
  const fisico = movimiento.clase === "fisico";
  const ataque = fisico ? atacante.ataque : atacante.ataqueEspecial;
  const defensa = fisico ? defensor.defensa : defensor.defensaEspecial;
  const base = Math.floor(Math.floor((2 * NIVEL / 5 + 2) * movimiento.poder * ataque / defensa) / 50) + 2;

  const stab = atacante.tipos.some((t) => t.clave === movimiento.tipo.clave) ? 1.5 : 1;
  const multiplicador = efectividad(movimiento.tipo.clave, defensor.tipos.map((t) => t.clave), tabla);
  const critico = aleatorio() < PROBABILIDAD_CRITICO;
  const variacion = 0.85 + aleatorio() * 0.15;

  const danio = multiplicador === 0
    ? 0
    : Math.max(1, Math.floor(base * stab * multiplicador * (critico ? 1.5 : 1) * variacion));
  return { danio, efectividad: multiplicador, critico };
}

function activo(jugador) {
  return jugador.equipo[jugador.activo];
}

// Evento de "entra un Pokémon" con sus PS en ese momento para que las apps lo animen.
function entrada(jugadores, indiceJugador) {
  const jugador = jugadores[indiceJugador];
  return {
    texto: `${jugador.nombre} envió a ${activo(jugador).nombre}.`,
    jugador: indiceJugador,
    indice: jugador.activo,
    ps: activo(jugador).ps,
    entra: true
  };
}

// Resuelve un turno. `acciones[i]` es { accion: "atacar", movimiento } o { accion: "cambiar", indice }.
// Modifica a los jugadores y devuelve los eventos en orden y el índice del ganador (o null).
function resolverTurno(jugadores, acciones, tabla, aleatorio = Math.random) {
  const eventos = [];

  // 1. Los cambios de Pokémon van antes que cualquier ataque.
  for (const i of [0, 1]) {
    if (acciones[i].accion === "cambiar") {
      const jugador = jugadores[i];
      eventos.push({ texto: `${jugador.nombre} retiró a ${activo(jugador).nombre}.` });
      jugador.activo = acciones[i].indice;
      eventos.push(entrada(jugadores, i));
    }
  }

  // 2. Ataca primero el más veloz; si empatan, se decide al azar.
  let orden = [0, 1].filter((i) => acciones[i].accion === "atacar");
  if (orden.length === 2) {
    const [v0, v1] = orden.map((i) => activo(jugadores[i]).velocidad);
    const primero = v0 > v1 ? 0 : v1 > v0 ? 1 : (aleatorio() < 0.5 ? 0 : 1);
    orden = [primero, 1 - primero];
  }

  for (const i of orden) {
    const atacante = activo(jugadores[i]);
    const rival = jugadores[1 - i];
    const defensor = activo(rival);
    if (atacante.debilitado) {
      continue;
    }

    // `efecto` le dice a las apps qué animar: la embestida, el golpe, el fallo o el debilitado.
    const movimiento = atacante.movimientos[acciones[i].movimiento];
    const tipo = movimiento.tipo.clave;
    eventos.push({ texto: `${atacante.nombre} de ${jugadores[i].nombre} usó ${movimiento.nombre}.`, jugador: i, efecto: "ataque", tipo });

    if (movimiento.precision !== null && aleatorio() * 100 >= movimiento.precision) {
      eventos.push({ texto: `¡El ataque de ${atacante.nombre} falló!`, jugador: 1 - i, efecto: "fallo" });
      continue;
    }

    const resultado = calcularDanio(atacante, defensor, movimiento, tabla, aleatorio);
    if (resultado.efectividad === 0) {
      eventos.push({ texto: `No afecta a ${defensor.nombre}…`, jugador: 1 - i, efecto: "inmune" });
      continue;
    }

    defensor.ps = Math.max(0, defensor.ps - resultado.danio);
    eventos.push({
      texto: `${defensor.nombre} recibió ${resultado.danio} de daño.`,
      jugador: 1 - i,
      ps: defensor.ps,
      efecto: "golpe",
      tipo,
      danio: resultado.danio,
      efectividad: resultado.efectividad,
      critico: resultado.critico
    });
    if (resultado.critico) eventos.push({ texto: "¡Golpe crítico!" });
    if (resultado.efectividad > 1) eventos.push({ texto: "¡Es muy eficaz!" });
    if (resultado.efectividad < 1) eventos.push({ texto: "No es muy eficaz…" });

    if (defensor.ps === 0) {
      defensor.debilitado = true;
      eventos.push({ texto: `¡${defensor.nombre} de ${rival.nombre} se debilitó!`, jugador: 1 - i, efecto: "debilitado" });
    }
  }

  // 3. Quien se quede sin Pokémon pierde; si no, entra el siguiente vivo.
  const conVida = jugadores.map((j) => j.equipo.some((p) => !p.debilitado));
  if (!conVida[0] || !conVida[1]) {
    const ganador = conVida[0] ? 0 : 1;
    eventos.push({ texto: `¡${jugadores[ganador].nombre} ganó el combate!` });
    return { eventos, ganador };
  }

  for (const i of [0, 1]) {
    const jugador = jugadores[i];
    if (activo(jugador).debilitado) {
      jugador.activo = jugador.equipo.findIndex((p) => !p.debilitado);
      eventos.push(entrada(jugadores, i));
    }
  }
  return { eventos, ganador: null };
}

module.exports = {
  crearCombatiente,
  efectividad,
  calcularDanio,
  resolverTurno,
  entrada
};
