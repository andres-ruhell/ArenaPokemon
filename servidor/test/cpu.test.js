const test = require("node:test");
const assert = require("node:assert/strict");
const { elegirEquipo, elegirAccion } = require("../cpu");

const tabla = {
  fire: { doble: ["grass"], mitad: ["water", "fire"], nulo: [] },
  water: { doble: ["fire"], mitad: ["grass", "water"], nulo: [] },
  normal: { doble: [], mitad: ["rock"], nulo: ["ghost"] }
};

const tipo = (clave) => ({ clave, nombre: clave });
const ataque = (nombre, clave, poder, precision = 100) => ({ nombre, tipo: tipo(clave), poder, precision, clase: "fisico" });
const pokemon = (tipos, movimientos, debilitado = false) => ({ tipos: tipos.map(tipo), movimientos, debilitado });

function estado(equipo, tiposRival, activo = 0) {
  return { yo: { activo, equipo }, rival: { activo: { tipos: tiposRival.map(tipo) } } };
}

// Siempre elige el mejor ataque (no "se equivoca").
const sinErrores = () => 0;

test("elige 3 Pokémon distintos entre los 5 con más stats", () => {
  const stats = (total) => ({ ps: total, ataque: 0, defensa: 0, ataqueEspecial: 0, defensaEspecial: 0, velocidad: 0 });
  const opciones = Array.from({ length: 10 }, (_, i) => ({ id: i + 1, stats: stats(i * 10) }));
  const ids = elegirEquipo(opciones);
  assert.equal(ids.length, 3);
  assert.equal(new Set(ids).size, 3);
  assert.ok(ids.every((id) => id >= 6), `eligió ${ids}`);
});

test("usa el ataque más efectivo contra el rival", () => {
  const equipo = [pokemon(["water"], [ataque("Placaje", "normal", 80), ataque("Pistola Agua", "water", 40)])];
  // Agua contra Fuego: 40 × 1.5 (STAB) × 2 = 120 > Placaje 80.
  assert.deepEqual(elegirAccion(estado(equipo, ["fire"]), tabla, sinErrores), { accion: "atacar", movimiento: 1 });
});

test("toma en cuenta la precisión", () => {
  const equipo = [pokemon(["normal"], [ataque("Seguro", "normal", 60, 100), ataque("Arriesgado", "normal", 80, 50)])];
  assert.equal(elegirAccion(estado(equipo, ["water"]), tabla, sinErrores).movimiento, 0);
});

test("cambia de Pokémon si ningún ataque afecta al rival", () => {
  const equipo = [
    pokemon(["normal"], [ataque("Placaje", "normal", 40)]),
    pokemon(["fire"], [ataque("Ascuas", "fire", 40)], true),
    pokemon(["water"], [ataque("Pistola Agua", "water", 40)])
  ];
  assert.deepEqual(elegirAccion(estado(equipo, ["ghost"]), tabla, sinErrores), { accion: "cambiar", indice: 2 });
});

test("a veces elige un ataque al azar", () => {
  const equipo = [pokemon(["water"], [ataque("A", "normal", 10), ataque("B", "water", 90), ataque("C", "normal", 20)])];
  const valores = [0.9, 0.0];
  const accion = elegirAccion(estado(equipo, ["fire"]), tabla, () => valores.shift());
  assert.deepEqual(accion, { accion: "atacar", movimiento: 0 });
});
