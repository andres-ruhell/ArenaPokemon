import Foundation

// Datos que envía el servidor por el WebSocket (ver el protocolo en el README).

struct Tipo: Decodable, Hashable {
    let clave: String
    let nombre: String
}

struct Stats: Decodable, Hashable {
    let ps: Int
    let ataque: Int
    let defensa: Int
    let ataqueEspecial: Int
    let defensaEspecial: Int
    let velocidad: Int
}

struct Movimiento: Decodable, Hashable {
    let nombre: String
    let tipo: Tipo
    let poder: Int
    let precision: Int?
    let clase: String
}

struct PokemonResumen: Decodable, Identifiable, Hashable {
    let id: Int
    let nombre: String
    let sprite: String
    let spriteFrente: String
    let spriteEspalda: String
    let tipos: [Tipo]
    let stats: Stats
}

struct PokemonCombate: Decodable, Identifiable, Hashable {
    let id: Int
    let nombre: String
    let sprite: String
    let spriteFrente: String
    let spriteEspalda: String
    let tipos: [Tipo]
    let psMax: Int
    var ps: Int
    var debilitado: Bool
    // Solo vienen en los Pokémon propios.
    let movimientos: [Movimiento]?
}

struct LadoPropio: Decodable {
    let nombre: String
    let activo: Int
    let equipo: [PokemonCombate]
}

struct LadoRival: Decodable {
    let nombre: String
    let activo: PokemonCombate
    let vivos: Int
    let total: Int
}

struct EstadoCombate: Decodable {
    let turno: Int
    let yo: LadoPropio
    let rival: LadoRival
}

struct Evento: Decodable {
    let texto: String
    // "yo" o "rival": de qué lado del campo pasa el evento.
    let lado: String?
    let ps: Int?
    let pokemon: PokemonCombate?
    // Qué animar: "ataque", "golpe", "fallo", "inmune" o "debilitado".
    let efecto: String?
    let tipo: String?
    let danio: Int?
    let efectividad: Double?
    let critico: Bool?
}

// Número que flota sobre el Pokémon golpeado.
struct Impacto: Equatable {
    enum Estilo {
        case normal, eficaz, pocoEficaz, critico, sinEfecto
    }

    let id: Int
    let texto: String
    let estilo: Estilo
    // Tipo del ataque para el color del destello; nil si no hubo golpe.
    let tipo: String?
}

struct MensajeServidor: Decodable {
    let tipo: String
    let nombre: String?
    let rival: String?
    let opciones: [PokemonResumen]?
    let estado: EstadoCombate?
    let eventos: [Evento]?
    let ganador: String?
    let mensaje: String?
    let segundos: Int?
    let tiempo: Int?
}
