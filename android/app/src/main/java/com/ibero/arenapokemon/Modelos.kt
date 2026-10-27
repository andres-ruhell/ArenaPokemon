package com.ibero.arenapokemon

import org.json.JSONArray
import org.json.JSONObject

// Datos que envía el servidor por el WebSocket (ver el protocolo en el README).

data class Tipo(val clave: String, val nombre: String)

data class Stats(
    val ps: Int,
    val ataque: Int,
    val defensa: Int,
    val ataqueEspecial: Int,
    val defensaEspecial: Int,
    val velocidad: Int
)

data class Movimiento(
    val nombre: String,
    val tipo: Tipo,
    val poder: Int,
    val precision: Int?,
    val clase: String
)

data class PokemonResumen(
    val id: Int,
    val nombre: String,
    val sprite: String,
    val spriteFrente: String,
    val spriteEspalda: String,
    val tipos: List<Tipo>,
    val stats: Stats
)

data class PokemonCombate(
    val id: Int,
    val nombre: String,
    val spriteFrente: String,
    val spriteEspalda: String,
    val tipos: List<Tipo>,
    val psMax: Int,
    val ps: Int,
    val debilitado: Boolean,
    // Solo vienen en los Pokémon propios.
    val movimientos: List<Movimiento>
)

data class LadoPropio(val nombre: String, val activo: Int, val equipo: List<PokemonCombate>)

data class LadoRival(val nombre: String, val activo: PokemonCombate, val vivos: Int, val total: Int)

data class EstadoCombate(val turno: Int, val yo: LadoPropio, val rival: LadoRival)

// `lado` es "yo" o "rival": de qué lado del campo pasa el evento.
// `efecto` dice qué animar: "ataque", "golpe", "fallo", "inmune" o "debilitado".
data class Evento(
    val texto: String,
    val lado: String?,
    val ps: Int?,
    val pokemon: PokemonCombate?,
    val efecto: String?,
    val tipo: String?,
    val danio: Int?,
    val efectividad: Double?,
    val critico: Boolean
)

enum class EstiloImpacto { NORMAL, EFICAZ, POCO_EFICAZ, CRITICO, SIN_EFECTO }

// Número que flota sobre el Pokémon golpeado. `tipo` colorea el destello; null si no hubo golpe.
data class Impacto(val id: Int, val texto: String, val estilo: EstiloImpacto, val tipo: String?)

private fun <T> JSONArray?.lista(convertir: (JSONObject) -> T): List<T> =
    if (this == null) emptyList() else (0 until length()).map { convertir(getJSONObject(it)) }

private fun JSONObject.enteroOpcional(campo: String): Int? =
    if (has(campo) && !isNull(campo)) getInt(campo) else null

fun tipoDe(json: JSONObject) = Tipo(json.getString("clave"), json.getString("nombre"))

fun movimientoDe(json: JSONObject) = Movimiento(
    nombre = json.getString("nombre"),
    tipo = tipoDe(json.getJSONObject("tipo")),
    poder = json.getInt("poder"),
    precision = json.enteroOpcional("precision"),
    clase = json.getString("clase")
)

fun pokemonResumenDe(json: JSONObject): PokemonResumen {
    val stats = json.getJSONObject("stats")
    return PokemonResumen(
        id = json.getInt("id"),
        nombre = json.getString("nombre"),
        sprite = json.getString("sprite"),
        spriteFrente = json.getString("spriteFrente"),
        spriteEspalda = json.getString("spriteEspalda"),
        tipos = json.getJSONArray("tipos").lista(::tipoDe),
        stats = Stats(
            ps = stats.getInt("ps"),
            ataque = stats.getInt("ataque"),
            defensa = stats.getInt("defensa"),
            ataqueEspecial = stats.getInt("ataqueEspecial"),
            defensaEspecial = stats.getInt("defensaEspecial"),
            velocidad = stats.getInt("velocidad")
        )
    )
}

fun pokemonCombateDe(json: JSONObject) = PokemonCombate(
    id = json.getInt("id"),
    nombre = json.getString("nombre"),
    spriteFrente = json.getString("spriteFrente"),
    spriteEspalda = json.getString("spriteEspalda"),
    tipos = json.getJSONArray("tipos").lista(::tipoDe),
    psMax = json.getInt("psMax"),
    ps = json.getInt("ps"),
    debilitado = json.getBoolean("debilitado"),
    movimientos = json.optJSONArray("movimientos").lista(::movimientoDe)
)

fun estadoCombateDe(json: JSONObject): EstadoCombate {
    val yo = json.getJSONObject("yo")
    val rival = json.getJSONObject("rival")
    return EstadoCombate(
        turno = json.getInt("turno"),
        yo = LadoPropio(
            nombre = yo.getString("nombre"),
            activo = yo.getInt("activo"),
            equipo = yo.getJSONArray("equipo").lista(::pokemonCombateDe)
        ),
        rival = LadoRival(
            nombre = rival.getString("nombre"),
            activo = pokemonCombateDe(rival.getJSONObject("activo")),
            vivos = rival.getInt("vivos"),
            total = rival.getInt("total")
        )
    )
}

fun eventosDe(json: JSONObject): List<Evento> = json.optJSONArray("eventos").lista { evento ->
    Evento(
        texto = evento.getString("texto"),
        lado = evento.optString("lado").ifEmpty { null },
        ps = evento.enteroOpcional("ps"),
        pokemon = evento.optJSONObject("pokemon")?.let(::pokemonCombateDe),
        efecto = evento.optString("efecto").ifEmpty { null },
        tipo = evento.optString("tipo").ifEmpty { null },
        danio = evento.enteroOpcional("danio"),
        efectividad = if (evento.has("efectividad")) evento.getDouble("efectividad") else null,
        critico = evento.optBoolean("critico")
    )
}

fun opcionesDe(json: JSONObject): List<PokemonResumen> =
    json.optJSONArray("opciones").lista(::pokemonResumenDe)
