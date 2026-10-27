import SwiftUI

struct SeleccionView: View {
    let cliente: ClienteArena
    @State private var seleccion: [Int] = []
    // Entrada: la Pokébola se sacude, baja al borde inferior y de ella salen los Pokémon.
    @State private var revelado = false
    @State private var mostrarPokebola = true
    @State private var barraVisible = false
    @State private var origen = CGPoint.zero
    @Environment(\.accessibilityReduceMotion) private var reducirMovimiento

    private let tamanoEquipo = 3
    private let columnas = [GridItem(.flexible(), spacing: 14), GridItem(.flexible(), spacing: 14)]
    private var esperando: Bool { cliente.fase == .esperandoRival || cliente.preparandoEquipo }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                VStack(alignment: .leading, spacing: 6) {
                    Text("Elige a tu equipo")
                        .font(.system(size: 30, weight: .heavy, design: .rounded))
                    Text("Elige 3 Pokémon.")
                        .font(.system(size: 16))
                        .foregroundStyle(.secondary)
                }

                LazyVGrid(columns: columnas, spacing: 14) {
                    ForEach(Array(cliente.opciones.enumerated()), id: \.element.id) { indice, pokemon in
                        Button { alternar(pokemon.id) } label: {
                            TarjetaPokemon(pokemon: pokemon, orden: seleccion.firstIndex(of: pokemon.id).map { $0 + 1 })
                        }
                        .buttonStyle(.plain)
                        .disabled(esperando)
                        .modifier(SalidaDePokebola(progreso: revelado ? 1 : 0, indice: indice, origen: origen))
                        .animation(.linear(duration: Entrada.duracionCarta / 1000)
                                    .delay(Double(indice) * Entrada.retrasoCarta / 1000), value: revelado)
                        .accessibilityIdentifier("pokemon-\(pokemon.id)")
                    }
                }
            }
            .padding(.horizontal, 20)
            .padding(.top, 20)
            .padding(.bottom, 120)
        }
        .background(Color(.systemGroupedBackground))
        .safeAreaInset(edge: .bottom) {
            barraInferior
                .offset(y: barraVisible ? 0 : 160)
                .opacity(barraVisible ? 1 : 0)
        }
        .overlay {
            if mostrarPokebola {
                PokebolaDeEntrada()
                    .onGeometryChange(for: CGPoint.self) { geometria in
                        // Las cartas salen del botón de la Pokébola ya asomada en el borde.
                        let marco = geometria.frame(in: .global)
                        return CGPoint(x: marco.midX, y: marco.maxY - PokebolaDeEntrada.diametro * 0.1)
                    } action: { origen = $0 }
                    .ignoresSafeArea(edges: .bottom)
                    .allowsHitTesting(false)
            }
        }
        .task { await abrirPokebola() }
    }

    // Sigue los tiempos de `Entrada`: las cartas salen cuando el botón ya está verde.
    private func abrirPokebola() async {
        guard !reducirMovimiento else {
            var sinAnimacion = Transaction()
            sinAnimacion.disablesAnimations = true
            withTransaction(sinAnimacion) {
                revelado = true
                barraVisible = true
                mostrarPokebola = false
            }
            return
        }
        try? await Task.sleep(for: .milliseconds(Entrada.inicioCartas))
        revelado = true
        try? await Task.sleep(for: .milliseconds(Entrada.inicioBarra - Entrada.inicioCartas))
        withAnimation(.easeInOut(duration: 0.35)) { barraVisible = true }
        try? await Task.sleep(for: .milliseconds(Entrada.total - Entrada.inicioBarra))
        mostrarPokebola = false
    }

    private var barraInferior: some View {
        VStack(spacing: 10) {
            if let error = cliente.mensajeError {
                Text(error).font(.footnote).foregroundStyle(.red)
            }
            if cliente.fase == .esperandoRival {
                HStack(spacing: 10) {
                    ProgressView()
                    Text("Esperando a que \(cliente.rival) elija…")
                        .font(.system(size: 16, weight: .semibold))
                }
                .frame(height: 56)
            } else {
                BotonPrincipal(
                    titulo: cliente.preparandoEquipo ? "Preparando ataques…" : "¡Al combate! \(seleccion.count)/\(tamanoEquipo)",
                    icono: "flame.fill",
                    cargando: cliente.preparandoEquipo
                ) {
                    cliente.mensajeError = nil
                    cliente.elegirEquipo(seleccion)
                }
                .disabled(seleccion.count != tamanoEquipo || cliente.preparandoEquipo)
                .opacity(seleccion.count == tamanoEquipo ? 1 : 0.5)
                .accessibilityIdentifier("confirmar")
            }
        }
        .padding(.horizontal, 20)
        .padding(.top, 12)
        .padding(.bottom, 8)
        .background(.bar)
    }

    private func alternar(_ id: Int) {
        if let posicion = seleccion.firstIndex(of: id) {
            seleccion.remove(at: posicion)
        } else if seleccion.count < tamanoEquipo {
            seleccion.append(id)
        }
    }
}

// Tiempos (en ms) y curvas de la entrada a la selección. Android usa los mismos valores.
enum Entrada {
    static let duracionAparicion = 280.0
    static let inicioSacudidas = 280.0
    static let duracionSacudidas = 660.0
    static let inicioBajada = 940.0
    static let duracionBajada = 400.0
    static let inicioVerde = 1280.0
    static let duracionVerde = 240.0
    static let inicioCartas = 1450.0
    static let retrasoCarta = 60.0
    static let duracionCarta = 650.0
    static let inicioHundida = 2600.0
    static let duracionHundida = 300.0
    static let inicioBarra = 2700.0
    static let total = 3050.0

    static func avance(_ tiempo: Double, desde inicio: Double, dura duracion: Double) -> Double {
        min(max((tiempo - inicio) / duracion, 0), 1)
    }

    // Acelera y frena sin saltos.
    static func suave(_ x: Double) -> Double {
        x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2
    }

    // Como un resorte: llega rápido, se pasa un poco y se acomoda.
    static func resorte(_ x: Double) -> Double {
        x >= 1 ? 1 : 1 - exp(-6 * x) * cos(2.2 * .pi * x)
    }

    struct EstadoPokebola {
        var escala = 1.0
        var opacidad = 1.0
        var giro = 0.0
        // 0 = centro de la pantalla; 1 = asomada en el borde inferior.
        var bajada = 0.0
        // 1 = escondida debajo del borde.
        var hundida = 0.0
        var verde = 0.0
    }

    static func pokebola(en tiempo: Double) -> EstadoPokebola {
        let aparicion = avance(tiempo, desde: 0, dura: duracionAparicion)
        let sacudidas = avance(tiempo, desde: inicioSacudidas, dura: duracionSacudidas)
        let emision = avance(tiempo, desde: inicioCartas, dura: retrasoCarta * 10)
        // Late un poco cada vez que suelta una carta.
        let latido = emision > 0 && emision < 1 ? 0.05 * abs(sin(emision * .pi * 10)) : 0
        let hundida = avance(tiempo, desde: inicioHundida, dura: duracionHundida)
        return EstadoPokebola(
            escala: (0.4 + 0.6 * resorte(aparicion)) * (1 + latido),
            opacidad: min(1, aparicion * 3),
            // Tres sacudidas que empiezan y terminan suaves.
            giro: 14 * sin(2 * .pi * 3 * sacudidas) * sin(.pi * sacudidas),
            bajada: suave(avance(tiempo, desde: inicioBajada, dura: duracionBajada)),
            hundida: hundida * hundida * hundida,
            verde: suave(avance(tiempo, desde: inicioVerde, dura: duracionVerde))
        )
    }
}

// Cada carta sale de detrás de la Pokébola: sube rápido, se abre en curva hacia su lugar,
// crece y se acomoda con un pequeño rebote. `progreso` va de 0 a 1 de forma lineal.
private struct SalidaDePokebola: ViewModifier, Animatable {
    var progreso: Double
    let indice: Int
    let origen: CGPoint

    var animatableData: Double {
        get { progreso }
        set { progreso = newValue }
    }

    func body(content: Content) -> some View {
        let asentado = Entrada.resorte(progreso)
        let lateral = 1 - (1 - progreso) * (1 - progreso)
        let giro = (indice.isMultiple(of: 2) ? -10.0 : 10.0) * (1 - asentado)
        content
            .visualEffect { efecto, geometria in
                let marco = geometria.frame(in: .global)
                return efecto
                    .scaleEffect(0.15 + 0.85 * asentado)
                    .rotationEffect(.degrees(giro))
                    .offset(x: (origen.x - marco.midX) * (1 - lateral),
                            y: (origen.y - marco.midY) * (1 - asentado))
            }
            .opacity(min(1, progreso * 6))
    }
}

// Aparece al centro, se sacude tres veces, baja hasta asomarse en el borde inferior
// (se ven sus 3/5), el botón pasa de blanco a verde, suelta las cartas y se esconde.
private struct PokebolaDeEntrada: View {
    static let diametro: CGFloat = 72

    @State private var inicio = Date()

    var body: some View {
        GeometryReader { geometria in
            TimelineView(.animation) { contexto in
                let estado = Entrada.pokebola(en: contexto.date.timeIntervalSince(inicio) * 1000)
                let diametro = Self.diametro
                let centro = geometria.size.height / 2
                let asomada = geometria.size.height - diametro * 0.1
                Pokebola(verde: estado.verde)
                    .frame(width: diametro, height: diametro)
                    .rotationEffect(.degrees(estado.giro), anchor: .bottom)
                    .scaleEffect(estado.escala)
                    .opacity(estado.opacidad)
                    .shadow(color: .black.opacity(0.18), radius: 8, y: 4)
                    .position(x: geometria.size.width / 2,
                              y: centro + (asomada - centro) * estado.bajada + diametro * estado.hundida)
            }
        }
        .accessibilityHidden(true)
    }
}

struct TarjetaPokemon: View {
    let pokemon: PokemonResumen
    let orden: Int?

    private var colorTipo: Color { Color.tipo(pokemon.tipos.first?.clave ?? "") }

    var body: some View {
        VStack(spacing: 8) {
            ZStack(alignment: .topTrailing) {
                SpriteRemoto(url: pokemon.sprite)
                    .frame(height: 92)
                    .frame(maxWidth: .infinity)
                    .padding(.top, 6)
                if let orden {
                    Text("\(orden)")
                        .font(.system(size: 14, weight: .heavy, design: .rounded))
                        .foregroundStyle(.white)
                        .frame(width: 28, height: 28)
                        .background(Color.rojoPokebola, in: Circle())
                }
            }

            VStack(spacing: 2) {
                Text(pokemon.nombre)
                    .font(.system(size: 16, weight: .bold, design: .rounded))
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
                Text("#\(pokemon.id)")
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundStyle(.secondary)
            }

            HStack(spacing: 4) {
                ForEach(pokemon.tipos, id: \.clave) { ChipTipo(tipo: $0, compacto: true) }
            }

            HStack(spacing: 0) {
                dato("PS", pokemon.stats.ps)
                dato("ATQ", max(pokemon.stats.ataque, pokemon.stats.ataqueEspecial))
                dato("VEL", pokemon.stats.velocidad)
            }
        }
        .padding(12)
        .background(
            LinearGradient(colors: [colorTipo.opacity(0.22), Color(.secondarySystemGroupedBackground)],
                           startPoint: .top, endPoint: .bottom),
            in: RoundedRectangle(cornerRadius: 20)
        )
        .overlay {
            RoundedRectangle(cornerRadius: 20)
                .stroke(orden == nil ? Color.clear : Color.rojoPokebola, lineWidth: 3)
        }
        .scaleEffect(orden == nil ? 1 : 0.97)
        .animation(.spring(response: 0.3, dampingFraction: 0.7), value: orden)
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(orden == nil ? [] : .isSelected)
    }

    private func dato(_ titulo: String, _ valor: Int) -> some View {
        VStack(spacing: 1) {
            Text("\(valor)")
                .font(.system(size: 14, weight: .bold, design: .rounded))
                .monospacedDigit()
            Text(titulo)
                .font(.system(size: 9, weight: .heavy))
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity)
    }
}
