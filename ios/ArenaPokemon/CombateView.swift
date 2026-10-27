import SwiftUI

struct CombateView: View {
    let cliente: ClienteArena

    private let columnas = [GridItem(.flexible(), spacing: 10), GridItem(.flexible(), spacing: 10)]

    var body: some View {
        ScrollView {
            VStack(spacing: 14) {
                encabezado
                campo
                cuadroTexto
                ataques
                equipo
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
        }
        .background(Color(.systemGroupedBackground))
    }

    private var encabezado: some View {
        HStack {
            Text("Turno \(cliente.estado?.turno ?? 1)")
                .font(.system(size: 15, weight: .bold, design: .rounded))
                .foregroundStyle(.secondary)
            Spacer()
            Button("Abandonar", role: .destructive) { cliente.cancelar() }
                .font(.system(size: 15, weight: .semibold))
                .accessibilityIdentifier("abandonar")
        }
    }

    // Campo de batalla: el rival arriba a la derecha y tu Pokémon de espaldas abajo a la izquierda.
    private var campo: some View {
        ZStack {
            LinearGradient(colors: [Color(red: 0.62, green: 0.84, blue: 0.98), Color(red: 0.78, green: 0.93, blue: 0.67)],
                           startPoint: .top, endPoint: .bottom)
            VStack {
                HStack(alignment: .top) {
                    if let rival = cliente.pokemonRival {
                        TarjetaEstado(pokemon: rival, entrenador: cliente.rival, mostrarNumeros: false,
                                      vivos: cliente.estado?.rival.vivos, total: cliente.estado?.rival.total)
                    }
                    Spacer()
                    if let rival = cliente.pokemonRival {
                        SpriteEnCampo(url: rival.spriteFrente, debilitado: rival.debilitado, golpes: cliente.golpesRival)
                    }
                }
                Spacer()
                HStack(alignment: .bottom) {
                    if let mio = cliente.miPokemon {
                        SpriteEnCampo(url: mio.spriteEspalda, debilitado: mio.debilitado, golpes: cliente.golpesMios)
                    }
                    Spacer()
                    if let mio = cliente.miPokemon {
                        TarjetaEstado(pokemon: mio, entrenador: cliente.miNombre, mostrarNumeros: true, vivos: nil, total: nil)
                    }
                }
            }
            .padding(14)
        }
        .frame(height: 330)
        .clipShape(RoundedRectangle(cornerRadius: 24))
    }

    private var textoActual: String {
        if cliente.narrando { return cliente.narracion }
        if cliente.accionEnviada { return "Esperando a \(cliente.rival)…" }
        return "¿Qué hará \(cliente.miPokemon?.nombre ?? "tu Pokémon")?"
    }

    private var cuadroTexto: some View {
        HStack {
            Text(textoActual)
                .font(.system(size: 17, weight: .semibold, design: .rounded))
                .foregroundStyle(.white)
                .contentTransition(.opacity)
                .animation(.easeInOut(duration: 0.2), value: textoActual)
                .accessibilityIdentifier("narracion")
            Spacer(minLength: 0)
            if cliente.accionEnviada && !cliente.narrando {
                ProgressView().tint(.white)
            }
        }
        .padding(.horizontal, 18)
        .frame(maxWidth: .infinity, minHeight: 64, alignment: .leading)
        .background(Color(red: 0.16, green: 0.2, blue: 0.28), in: RoundedRectangle(cornerRadius: 18))
    }

    private var ataques: some View {
        LazyVGrid(columns: columnas, spacing: 10) {
            ForEach(Array((cliente.miPokemon?.movimientos ?? []).enumerated()), id: \.offset) { indice, movimiento in
                Button { cliente.atacar(indice) } label: {
                    BotonAtaque(movimiento: movimiento)
                }
                .buttonStyle(.plain)
                .disabled(!cliente.puedeActuar)
                .opacity(cliente.puedeActuar ? 1 : 0.45)
                .accessibilityIdentifier("ataque-\(indice)")
            }
        }
    }

    private var equipo: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Cambiar Pokémon")
                .font(.system(size: 13, weight: .bold))
                .foregroundStyle(.secondary)
            HStack(spacing: 10) {
                ForEach(Array((cliente.estado?.yo.equipo ?? []).enumerated()), id: \.offset) { indice, pokemon in
                    let esActivo = indice == cliente.estado?.yo.activo
                    Button { cliente.cambiar(a: indice) } label: {
                        MiembroEquipo(pokemon: pokemon, activo: esActivo)
                    }
                    .buttonStyle(.plain)
                    .disabled(!cliente.puedeActuar || esActivo || pokemon.debilitado)
                    .accessibilityIdentifier("cambiar-\(indice)")
                }
            }
        }
    }
}

private struct TarjetaEstado: View {
    let pokemon: PokemonCombate
    let entrenador: String
    let mostrarNumeros: Bool
    let vivos: Int?
    let total: Int?

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .firstTextBaseline) {
                Text(pokemon.nombre)
                    .font(.system(size: 16, weight: .heavy, design: .rounded))
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
                Spacer(minLength: 4)
                Text("Nv50")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundStyle(.secondary)
            }
            BarraPS(ps: pokemon.ps, psMax: pokemon.psMax)
            HStack {
                if let vivos, let total {
                    HStack(spacing: 3) {
                        ForEach(0..<total, id: \.self) { i in
                            Circle()
                                .fill(i < vivos ? Color.rojoPokebola : Color.gray.opacity(0.35))
                                .frame(width: 8, height: 8)
                        }
                    }
                    .accessibilityLabel("\(vivos) de \(total) Pokémon con vida")
                } else {
                    Text(entrenador)
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }
                Spacer(minLength: 4)
                if mostrarNumeros {
                    Text("\(pokemon.ps)/\(pokemon.psMax)")
                        .font(.system(size: 12, weight: .bold, design: .rounded))
                        .monospacedDigit()
                        .contentTransition(.numericText(value: Double(pokemon.ps)))
                }
            }
        }
        .padding(10)
        .frame(width: 168)
        .background(.white.opacity(0.92), in: RoundedRectangle(cornerRadius: 14))
        .foregroundStyle(.black)
        .shadow(color: .black.opacity(0.08), radius: 6, y: 3)
    }
}

private struct SpriteEnCampo: View {
    let url: String
    let debilitado: Bool
    let golpes: Int

    var body: some View {
        ZStack(alignment: .bottom) {
            Ellipse()
                .fill(Color(red: 0.45, green: 0.67, blue: 0.35).opacity(0.55))
                .frame(width: 130, height: 30)
            SpriteRemoto(url: url, pixelado: true)
                .frame(width: 128, height: 128)
                .padding(.bottom, 8)
                .opacity(debilitado ? 0 : 1)
                .offset(y: debilitado ? 30 : 0)
                .animation(.easeIn(duration: 0.5), value: debilitado)
                .keyframeAnimator(initialValue: 0.0, trigger: golpes) { contenido, desplazamiento in
                    contenido.offset(x: desplazamiento)
                } keyframes: { _ in
                    KeyframeTrack {
                        LinearKeyframe(-10, duration: 0.06)
                        LinearKeyframe(10, duration: 0.08)
                        LinearKeyframe(-6, duration: 0.08)
                        LinearKeyframe(0, duration: 0.06)
                    }
                }
        }
        .accessibilityHidden(true)
    }
}

private struct BotonAtaque: View {
    let movimiento: Movimiento

    var body: some View {
        let texto = Color.textoSobreTipo(movimiento.tipo.clave)
        VStack(alignment: .leading, spacing: 4) {
            Text(movimiento.nombre)
                .font(.system(size: 16, weight: .bold, design: .rounded))
                .lineLimit(1)
                .minimumScaleFactor(0.7)
            Text("\(movimiento.tipo.nombre) · Poder \(movimiento.poder)")
                .font(.system(size: 11, weight: .semibold))
                .opacity(0.85)
        }
        .foregroundStyle(texto)
        .padding(.horizontal, 14)
        .frame(maxWidth: .infinity, minHeight: 60, alignment: .leading)
        .background(Color.tipo(movimiento.tipo.clave), in: RoundedRectangle(cornerRadius: 16))
    }
}

private struct MiembroEquipo: View {
    let pokemon: PokemonCombate
    let activo: Bool

    var body: some View {
        VStack(spacing: 4) {
            SpriteRemoto(url: pokemon.spriteFrente, pixelado: true)
                .frame(width: 52, height: 52)
                .grayscale(pokemon.debilitado ? 1 : 0)
            Text(pokemon.nombre)
                .font(.system(size: 11, weight: .bold))
                .lineLimit(1)
                .minimumScaleFactor(0.7)
            BarraPS(ps: pokemon.ps, psMax: pokemon.psMax)
        }
        .padding(8)
        .frame(maxWidth: .infinity)
        .background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 14))
        .overlay {
            RoundedRectangle(cornerRadius: 14)
                .stroke(activo ? Color.rojoPokebola : .clear, lineWidth: 2)
        }
        .opacity(pokemon.debilitado ? 0.45 : 1)
    }
}
