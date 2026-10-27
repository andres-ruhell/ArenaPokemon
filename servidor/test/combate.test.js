const test = require("node:test");
const assert = require("node:assert/strict");
const { crearCombatiente, efectividad, calcularDanio, resolverTurno } = require("../combate");

const tabla = {
  fire: { doble: ["grass"], mitad: ["water", "fire"], nulo: [] },
  water: { doble: ["fire"], mitad: ["grass", "water"], nulo: [] },
  normal: { doble: [], mitad: ["rock"], nulo: ["ghost"] }
};

const tipo = (clave) => ({ clave, nombre: clave });
const ataque = (nombre, clave, poder, clase = "fisico", precision = 100) =>
  ({ nombre, tipo: tipo(clave), poder, precision, clase });

function pokemon(nombre, tipos, stats, movimientos) {
  const base = { ps: 50, ataque: 50, defensa: 50, ataqueEspecial: 50, defensaEspecial: 50, velocidad: 50, ...stats };
  return crearCombatiente({ id: nombre.length, nombre, tipos: tipos.map(tipo), stats: base }, movimientos);
}

function jugador(nombre, equipo) {
  return { nombre, equipo, activo: 0 };
}

// Sin crítico, sin fallar y con variación mínima (0.85).
const sinSuerte = () => 0.5;
const minimo = () => {
  let llamada = 0;
  return () => (llamada++ % 2 === 0 ? 0.5 : 0);
};

test("las stats se calculan al nivel 50", () => {
  const p = pokemon("Prueba", ["normal"], { ps: 45, velocidad: 90 }, []);
  assert.equal(p.psMax, 105);
  assert.equal(p.ps, 105);
  assert.equal(p.velocidad, 95);
});

test("efectividad combina los dos tipos del defensor", () => {
  assert.equal(efectividad("fire", ["grass"], tabla), 2);
  assert.equal(efectividad("fire", ["water"], tabla), 0.5);
  assert.equal(efectividad("fire", ["grass", "water"], tabla), 1);
  assert.equal(efectividad("normal", ["ghost"], tabla), 0);
  assert.equal(efectividad("sin-tipo", ["ghost"], tabla), 1);
});

test("el daño sigue la fórmula con STAB y efectividad", () => {
  const atacante = pokemon("Fuego", ["fire"], {}, []);
  const defensor = pokemon("Planta", ["grass"], {}, []);
  // base = floor(floor(22 * 40 * 55 / 55) / 50) + 2 = 19; × 1.5 STAB × 2 eficaz × 0.85
  const resultado = calcularDanio(atacante, defensor, ataque("Ascuas", "fire", 40, "especial"), tabla, minimo());
  assert.deepEqual(resultado, { danio: 48, efectividad: 2, critico: false });
});

test("un tipo inmune no recibe daño", () => {
  const atacante = pokemon("Normal", ["normal"], {}, []);
  const fantasma = pokemon("Fantasma", ["ghost"], {}, []);
  const resultado = calcularDanio(atacante, fantasma, ataque("Placaje", "normal", 40), tabla, sinSuerte);
  assert.equal(resultado.danio, 0);
});

test("ataca primero el más veloz y el debilitado ya no actúa", () => {
  const rapido = pokemon("Rapido", ["fire"], { velocidad: 120, ataque: 200 }, [ataque("Golpe", "fire", 120)]);
  const lento = pokemon("Lento", ["grass"], { velocidad: 20, ps: 10 }, [ataque("Latigo", "grass", 40)]);
  const jugadores = [jugador("Ash", [lento, pokemon("Reserva", ["water"], {}, [])]), jugador("Misty", [rapido])];

  const { eventos, ganador } = resolverTurno(jugadores, [
    { accion: "atacar", movimiento: 0 },
    { accion: "atacar", movimiento: 0 }
  ], tabla, sinSuerte);

  const textos = eventos.map((e) => e.texto);
  assert.equal(textos[0], "Rapido de Misty usó Golpe.");
  assert.ok(textos.includes("¡Lento de Ash se debilitó!"));
  assert.ok(!textos.some((t) => t.startsWith("Lento de Ash usó")));
  assert.equal(ganador, null);
  // Entra el siguiente Pokémon vivo.
  assert.equal(jugadores[0].activo, 1);
  assert.deepEqual(eventos.at(-1), { texto: "Ash envió a Reserva.", jugador: 0, indice: 1, ps: 110, entra: true });
});

test("el cambio de Pokémon ocurre antes del ataque", () => {
  const atacante = pokemon("Fuego", ["fire"], { velocidad: 200 }, [ataque("Ascuas", "fire", 40, "especial")]);
  const planta = pokemon("Planta", ["grass"], {}, [ataque("Latigo", "grass", 40)]);
  const agua = pokemon("Agua", ["water"], {}, [ataque("Pistola", "water", 40)]);
  const jugadores = [jugador("Ash", [atacante]), jugador("Misty", [planta, agua])];

  const { eventos } = resolverTurno(jugadores, [
    { accion: "atacar", movimiento: 0 },
    { accion: "cambiar", indice: 1 }
  ], tabla, sinSuerte);

  assert.equal(eventos[0].texto, "Misty retiró a Planta.");
  assert.equal(eventos[1].texto, "Misty envió a Agua.");
  assert.ok(eventos.some((e) => e.texto === "No es muy eficaz…"));
  assert.equal(planta.ps, planta.psMax);
  assert.ok(agua.ps < agua.psMax);
});

test("un ataque puede fallar según su precisión", () => {
  const a = pokemon("A", ["normal"], { velocidad: 100 }, [ataque("Hidrobomba", "water", 110, "especial", 80)]);
  const b = pokemon("B", ["normal"], {}, [ataque("Placaje", "normal", 40)]);
  const jugadores = [jugador("Ash", [a]), jugador("Misty", [b])];
  const { eventos } = resolverTurno(jugadores, [
    { accion: "atacar", movimiento: 0 },
    { accion: "atacar", movimiento: 0 }
  ], tabla, () => 0.9);
  assert.ok(eventos.some((e) => e.texto === "¡El ataque de A falló!"));
  assert.equal(b.ps, b.psMax);
});

test("gana quien deja al rival sin Pokémon", () => {
  const fuerte = pokemon("Fuerte", ["fire"], { ataque: 250, velocidad: 150 }, [ataque("Llamarada", "fire", 110)]);
  const debil = pokemon("Debil", ["grass"], { ps: 1 }, [ataque("Latigo", "grass", 40)]);
  const jugadores = [jugador("Ash", [fuerte]), jugador("Misty", [debil])];

  const { eventos, ganador } = resolverTurno(jugadores, [
    { accion: "atacar", movimiento: 0 },
    { accion: "atacar", movimiento: 0 }
  ], tabla, sinSuerte);

  assert.equal(ganador, 0);
  assert.equal(eventos.at(-1).texto, "¡Ash ganó el combate!");
});
