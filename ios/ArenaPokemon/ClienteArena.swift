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
    private(set) var contraCPU = false
    // Cuándo el servidor pone a la CPU de rival si no llega otro jugador.
    private(set) var esperaHasta: Date?
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
    private(set) var ataquesMios = 0
    private(set) var ataquesRival = 0
    private(set) var impactoMio: Impacto?
    private(set) var impactoRival: Impacto?
    @ObservationIgnored private var impactos = 0
    private(set) var accionEnviada = false
    // Hasta cuándo hay para elegir el movimiento; después el servidor ataca al azar.
    private(set) var turnoHasta: Date?
    private(set) var turnoAgotado = false
    @ObservationIgnored private var relojTurno: Task<Void, Never>?

    private(set) var ganador: String?
    private(set) var motivoFin = ""
    var mensajeError: String?

    @ObservationIgnored private var tarea: URLSessionWebSocketTask?
    @ObservationIgnored private var ultimoServidor = ""
    private let pausaEvento = Duration.milliseconds(900)
    // Tras la embestida el golpe llega enseguida.
    private let pausaAtaque = Duration.milliseconds(450)

    var gane: Bool { ganador == miNombre }
    var puedeActuar: Bool { fase == .combate && !narrando && !accionEnviada && !turnoAgotado }

    // `servidor` es "ip:puerto". Con `contraCPU` el servidor pone de rival a su CPU en vez de esperar a otro jugador.
    func conectar(nombre: String, servidor: String, contraCPU: Bool = false) {
        desconectar()
        mensajeError = nil
        guard let url = URL(string: "ws://\(servidor)/arena") else {
            mensajeError = "No se encontró el servidor."
            return
        }

        let tarea = URLSession.shared.webSocketTask(with: url)
        self.tarea = tarea
        tarea.resume()
        ultimoServidor = servidor
        miNombre = nombre
        self.contraCPU = contraCPU
        esperaHasta = nil
        fase = .buscando
        enviar(["tipo": contraCPU ? "jugarCPU" : "unirse", "nombre": nombre])
        Task { await escuchar(tarea) }
    }

    func jugarOtraVez() {
        conectar(nombre: miNombre, servidor: ultimoServidor, contraCPU: contraCPU)
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
        detenerReloj()
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
            if let segundos = mensaje.segundos {
                esperaHasta = .now.addingTimeInterval(TimeInterval(segundos))
            }
            fase = .buscando
        case "partida":
            miNombre = mensaje.nombre ?? miNombre
            rival = mensaje.rival ?? ""
            opciones = mensaje.opciones ?? []
            estado = nil
            miPokemon = nil
            pokemonRival = nil
            impactoMio = nil
            impactoRival = nil
            ganador = nil
            fase = .eligiendo
        case "esperandoRival":
            preparandoEquipo = false
            fase = .esperandoRival
        case "inicioCombate", "turno":
            detenerReloj()
            preparandoEquipo = false
            fase = .combate
            await narrar(mensaje.eventos ?? [])
            if let estado = mensaje.estado {
                aplicar(estado)
            }
            iniciarReloj(segundos: mensaje.tiempo)
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
            let mio = evento.lado == "yo"
            switch evento.efecto {
            case "ataque":
                if mio { ataquesMios += 1 } else { ataquesRival += 1 }
            case "golpe":
                let ps = evento.ps ?? 0
                withAnimation(.easeInOut(duration: 0.6)) {
                    if mio { miPokemon?.ps = ps } else { pokemonRival?.ps = ps }
                }
                mostrar(impactoDeGolpe(evento), mio: mio)
            case "fallo", "inmune":
                mostrar(nuevoImpacto(evento.efecto == "fallo" ? "Falló" : "Sin efecto", .sinEfecto, tipo: nil), mio: mio)
            case "debilitado":
                withAnimation {
                    if mio { miPokemon?.debilitado = true } else { pokemonRival?.debilitado = true }
                }
            default:
                if let pokemon = evento.pokemon {
                    if mio { miPokemon = pokemon } else { pokemonRival = pokemon }
                }
            }
            try? await Task.sleep(for: evento.efecto == "ataque" ? pausaAtaque : pausaEvento)
        }
        narrando = false
    }

    private func impactoDeGolpe(_ evento: Evento) -> Impacto {
        let danio = evento.danio ?? 0
        let efectividad = evento.efectividad ?? 1
        if evento.critico == true {
            return nuevoImpacto("¡−\(danio)!", .critico, tipo: evento.tipo)
        }
        let estilo: Impacto.Estilo = efectividad > 1 ? .eficaz : efectividad < 1 ? .pocoEficaz : .normal
        return nuevoImpacto("−\(danio)", estilo, tipo: evento.tipo)
    }

    private func nuevoImpacto(_ texto: String, _ estilo: Impacto.Estilo, tipo: String?) -> Impacto {
        impactos += 1
        return Impacto(id: impactos, texto: texto, estilo: estilo, tipo: tipo)
    }

    // El golpe también sacude y hace parpadear al Pokémon (ver SpriteEnCampo).
    private func mostrar(_ impacto: Impacto, mio: Bool) {
        if mio {
            impactoMio = impacto
            if impacto.tipo != nil { golpesMios += 1 }
        } else {
            impactoRival = impacto
            if impacto.tipo != nil { golpesRival += 1 }
        }
    }

    // El reloj empieza cuando termina la narración, igual que en el servidor.
    private func iniciarReloj(segundos: Int?) {
        detenerReloj()
        guard let segundos else { return }
        turnoHasta = .now.addingTimeInterval(TimeInterval(segundos))
        relojTurno = Task { [weak self] in
            try? await Task.sleep(for: .seconds(segundos))
            guard !Task.isCancelled else { return }
            self?.turnoAgotado = true
        }
    }

    private func detenerReloj() {
        relojTurno?.cancel()
        relojTurno = nil
        turnoHasta = nil
        turnoAgotado = false
    }

    private func aplicar(_ estado: EstadoCombate) {
        self.estado = estado
        miPokemon = estado.yo.equipo[estado.yo.activo]
        pokemonRival = estado.rival.activo
        accionEnviada = false
    }
}
