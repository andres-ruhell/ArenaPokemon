// Consultas a la PokeAPI con caché en memoria (pide cada recurso una sola vez).
const BASE = "https://pokeapi.co/api/v2";
const TIEMPO_LIMITE = 10000;

// Cambia a 151 para usar solo la primera generación.
const MAX_ID = 1025;
const MOVIMIENTOS_POR_POKEMON = 4;
const PODER_MAXIMO = 120;

const CLAVES_TIPOS = [
  "normal", "fighting", "flying", "poison", "ground", "rock",
  "bug", "ghost", "steel", "fire", "water", "grass",
  "electric", "psychic", "ice", "dragon", "dark", "fairy"
];

// Ataque de respaldo para Pokémon sin movimientos de daño (Ditto, Magikarp…).
const FORCEJEO = {
  nombre: "Forcejeo",
  tipo: { clave: "sin-tipo", nombre: "Sin tipo" },
  poder: 50,
  precision: null,
  clase: "fisico"
};

const pokemones = new Map();
const movimientos = new Map();
const tablaTipos = {};
let cargaTipos = null;

async function pedir(ruta) {
  const respuesta = await fetch(`${BASE}${ruta}`, {
    headers: { Accept: "application/json" },
    signal: AbortSignal.timeout(TIEMPO_LIMITE)
  });
  if (!respuesta.ok) {
    throw new Error(`La PokeAPI respondió ${respuesta.status} en ${ruta}`);
  }
  return respuesta.json();
}

// Guarda la promesa para que dos consultas simultáneas compartan la misma petición.
function enCache(cache, clave, crear) {
  if (!cache.has(clave)) {
    cache.set(clave, crear().catch((error) => {
      cache.delete(clave);
      throw error;
    }));
  }
  return cache.get(clave);
}

function formatearNombre(nombre) {
  return nombre
    .split("-")
    .map((parte) => parte.charAt(0).toUpperCase() + parte.slice(1))
    .join(" ");
}

function nombreEnEspanol(nombres, clave) {
  return nombres.find((n) => n.language.name === "es")?.name ?? formatearNombre(clave);
}

function tipoConNombre(clave) {
  return { clave, nombre: tablaTipos[clave]?.nombre ?? formatearNombre(clave) };
}

function revolver(lista, aleatorio = Math.random) {
  const copia = [...lista];
  for (let i = copia.length - 1; i > 0; i--) {
    const j = Math.floor(aleatorio() * (i + 1));
    [copia[i], copia[j]] = [copia[j], copia[i]];
  }
  return copia;
}

// Nombre en español y relaciones de daño de los 18 tipos.
function cargarTipos() {
  if (!cargaTipos) {
    cargaTipos = Promise.all(CLAVES_TIPOS.map(async (clave) => {
      const datos = await pedir(`/type/${clave}`);
      const relaciones = datos.damage_relations;
      tablaTipos[clave] = {
        nombre: nombreEnEspanol(datos.names, clave),
        doble: relaciones.double_damage_to.map((t) => t.name),
        mitad: relaciones.half_damage_to.map((t) => t.name),
        nulo: relaciones.no_damage_to.map((t) => t.name)
      };
    })).catch((error) => {
      cargaTipos = null;
      throw error;
    });
  }
  return cargaTipos;
}

function obtenerTablaTipos() {
  return tablaTipos;
}

function obtenerPokemon(id) {
  return enCache(pokemones, id, async () => {
    await cargarTipos();
    const datos = await pedir(`/pokemon/${id}`);
    const stat = (nombre) => datos.stats.find((s) => s.stat.name === nombre)?.base_stat ?? 50;
    const sprites = datos.sprites;
    const frente = sprites.front_default ?? sprites.other?.["official-artwork"]?.front_default ?? "";

    return {
      id: datos.id,
      // La especie quita la forma: "giratina-altered" → "Giratina".
      nombre: formatearNombre(datos.species.name),
      sprite: sprites.other?.["official-artwork"]?.front_default ?? frente,
      spriteFrente: frente,
      spriteEspalda: sprites.back_default ?? frente,
      tipos: [...datos.types]
        .sort((a, b) => a.slot - b.slot)
        .map((t) => tipoConNombre(t.type.name)),
      stats: {
        ps: stat("hp"),
        ataque: stat("attack"),
        defensa: stat("defense"),
        ataqueEspecial: stat("special-attack"),
        defensaEspecial: stat("special-defense"),
        velocidad: stat("speed")
      },
      // Solo se usa en el servidor para elegir sus ataques.
      clavesMovimientos: datos.moves.map((m) => m.move.name)
    };
  });
}

// Lo que ven las apps de un Pokémon (sin la lista completa de movimientos).
function resumen(pokemon) {
  const { id, nombre, sprite, spriteFrente, spriteEspalda, tipos, stats } = pokemon;
  return { id, nombre, sprite, spriteFrente, spriteEspalda, tipos, stats };
}

async function obtenerAleatorios(cantidad, aleatorio = Math.random) {
  const ids = new Set();
  while (ids.size < cantidad) {
    ids.add(1 + Math.floor(aleatorio() * MAX_ID));
  }
  return Promise.all([...ids].map(obtenerPokemon));
}

function obtenerMovimiento(clave) {
  return enCache(movimientos, clave, async () => {
    const datos = await pedir(`/move/${clave}`);
    const clases = { physical: "fisico", special: "especial" };
    return {
      nombre: nombreEnEspanol(datos.names, clave),
      tipo: tipoConNombre(datos.type.name),
      poder: datos.power,
      precision: datos.accuracy,
      clase: clases[datos.damage_class.name] ?? "estado"
    };
  });
}

function esDeDanio(movimiento) {
  return movimiento.clase !== "estado" &&
    movimiento.poder > 0 &&
    movimiento.poder <= PODER_MAXIMO;
}

// Cuatro ataques reales al azar, con preferencia por los del mismo tipo del Pokémon.
async function obtenerMovimientos(pokemon, aleatorio = Math.random) {
  await cargarTipos();
  const candidatos = revolver(pokemon.clavesMovimientos, aleatorio).slice(0, 40);
  const validos = [];

  for (let i = 0; i < candidatos.length && validos.length < 8; i += 8) {
    const lote = await Promise.all(candidatos.slice(i, i + 8).map(obtenerMovimiento));
    validos.push(...lote.filter(esDeDanio));
  }

  const propios = validos.filter((m) => pokemon.tipos.some((t) => t.clave === m.tipo.clave));
  const otros = validos.filter((m) => !propios.includes(m));
  const elegidos = [...propios.slice(0, 2), ...otros, ...propios.slice(2)]
    .slice(0, MOVIMIENTOS_POR_POKEMON);

  return elegidos.length > 0 ? elegidos : [FORCEJEO];
}

module.exports = {
  cargarTipos,
  obtenerTablaTipos,
  obtenerAleatorios,
  obtenerMovimientos,
  resumen,
  revolver
};
