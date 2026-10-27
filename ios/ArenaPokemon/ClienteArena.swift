import Foundation
import Observation
import SwiftUI

// Conexión WebSocket con el servidor y estado de la partida que muestran las pantallas.
@Observable
@MainActor
final class ClienteArena {
    enum Fase {
        case inicio, buscando, eligiendo, esperandoRival, combate, fin
    }

    private(set) var fase = Fase.inicio
    private(set) var miNombre = ""
    private(set) var rival = ""
    private(set) var opciones: [PokemonResumen] = []
    private(set) var estado: EstadoCombate?
    private(set) var preparandoEquipo = false

    // Lo que se ve en el campo mientras se narran los eventos de un turno.
    private(set) var miPokemon: PokemonCombate?
    private(set) var pokemonRival: PokemonCombate?
    private(set) var narracion = ""
    private(set) var narrando = false
    private(set) var golpesMios = 0
    private(set) var golpesRival = 0
    private(set) var accionEnviada = false

    private(set) var ganador: String?
    private(set) var motivoFin = ""
    var mensajeError: String?

    @ObservationIgnored private var tarea: URLSessionWebSocketTask?
    private let pausaEvento = Duration.milliseconds(900)

    var gane: Bool { ganador == miNombre }
    var puedeActuar: Bool { fase == .combate && !narrando && !accionEnviada }

    func conectar(nombre: String, servidor: String) {
        desconectar()
        mensajeError = nil
        let direccion = servidor.trimmingCharacters(in: .whitespaces)
        guard !direccion.isEmpty, let url = URL(string: "ws://\(direccion)/arena") else {
            mensajeError = "Escribe la dirección del servidor, por ejemplo 127.0.0.1:3000."
            return
        }

        let tarea = URLSession.shared.webSocketTask(with: url)
        self.tarea = tarea
        tarea.resume()
        miNombre = nombre
        fase = .buscando
        enviar(["tipo": "unirse", "nombre": nombre])
        Task { await escuchar(tarea) }
    }

    func cancelar() {
        desconectar()
        fase = .inicio
    }

    func elegirEquipo(_ ids: [Int]) {
        preparandoEquipo = true
        enviar(["tipo": "elegirEquipo", "ids": ids])
    }

    func atacar(_ movimiento: Int) {
        accionEnviada = true
        enviar(["tipo": "accion", "accion": "atacar", "movimiento": movimiento])
    }

    func cambiar(a indice: Int) {
        accionEnviada = true
        enviar(["tipo": "accion", "accion": "cambiar", "indice": indice])
    }

    private func desconectar() {
        let anterior = tarea
        tarea = nil
        anterior?.cancel(with: .goingAway, reason: nil)
    }

    private func enviar(_ mensaje: [String: Any]) {
        guard let tarea,
              let datos = try? JSONSerialization.data(withJSONObject: mensaje),
              let texto = String(data: datos, encoding: .utf8) else { return }
        Task { try? await tarea.send(.string(texto)) }
    }

    // Procesa los mensajes uno por uno; la narración de un turno termina antes de leer el siguiente.
    private func escuchar(_ tarea: URLSessionWebSocketTask) async {
        do {
            while true {
                let recibido = try await tarea.receive()
                guard case .string(let texto) = recibido,
                      let mensaje = try? JSONDecoder().decode(MensajeServidor.self, from: Data(texto.utf8)) else {
                    continue
                }
                await procesar(mensaje)
            }
        } catch {
            guard tarea === self.tarea else { return }
            self.tarea = nil
            if fase != .fin {
                mensajeError = "Se perdió la conexión con el servidor."
                fase = .inicio
            }
        }
    }

    private func procesar(_ mensaje: MensajeServidor) async {
        switch mensaje.tipo {
        case "esperando":
            fase = .buscando
        case "partida":
            miNombre = mensaje.nombre ?? miNombre
            rival = mensaje.rival ?? ""
            opciones = mensaje.opciones ?? []
            estado = nil
            miPokemon = nil
            pokemonRival = nil
            ganador = nil
            fase = .eligiendo
        case "esperandoRival":
            preparandoEquipo = false
            fase = .esperandoRival
        case "inicioCombate", "turno":
            preparandoEquipo = false
            fase = .combate
            await narrar(mensaje.eventos ?? [])
            if let estado = mensaje.estado {
                aplicar(estado)
            }
        case "fin":
            await narrar(mensaje.eventos ?? [])
            if let estado = mensaje.estado {
                aplicar(estado)
            }
            ganador = mensaje.ganador
            motivoFin = mensaje.eventos?.last?.texto ?? ""
            desconectar()
            fase = .fin
        case "error":
            mensajeError = mensaje.mensaje
            preparandoEquipo = false
            accionEnviada = false
            if fase == .buscando {
                cancelar()
            }
        default:
            break
        }
    }

    private func narrar(_ eventos: [Evento]) async {
        narrando = true
        for evento in eventos {
            narracion = evento.texto
            if let pokemon = evento.pokemon {
                if evento.lado == "yo" { miPokemon = pokemon } else { pokemonRival = pokemon }
            } else if let ps = evento.ps {
                withAnimation(.easeInOut(duration: 0.6)) {
                    if evento.lado == "yo" {
                        miPokemon?.ps = ps
                        miPokemon?.debilitado = ps == 0
                        golpesMios += 1
                    } else {
                        pokemonRival?.ps = ps
                        pokemonRival?.debilitado = ps == 0
                        golpesRival += 1
                    }
                }
            }
            try? await Task.sleep(for: pausaEvento)
        }
        narrando = false
    }

    private func aplicar(_ estado: EstadoCombate) {
        self.estado = estado
        miPokemon = estado.yo.equipo[estado.yo.activo]
        pokemonRival = estado.rival.activo
        accionEnviada = false
    }
}
