import SwiftUI

struct SeleccionView: View {
    let cliente: ClienteArena
    @State private var seleccion: [Int] = []

    private let tamanoEquipo = 3
    private let columnas = [GridItem(.flexible(), spacing: 14), GridItem(.flexible(), spacing: 14)]
    private var esperando: Bool { cliente.fase == .esperandoRival || cliente.preparandoEquipo }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                VStack(alignment: .leading, spacing: 6) {
                    Text("Elige a tu equipo")
                        .font(.system(size: 30, weight: .heavy, design: .rounded))
                    Text("Tu rival es **\(cliente.rival)**. Toca 3 Pokémon.")
                        .font(.system(size: 16))
                        .foregroundStyle(.secondary)
                }

                LazyVGrid(columns: columnas, spacing: 14) {
                    ForEach(cliente.opciones) { pokemon in
                        Button { alternar(pokemon.id) } label: {
                            TarjetaPokemon(pokemon: pokemon, orden: seleccion.firstIndex(of: pokemon.id).map { $0 + 1 })
                        }
                        .buttonStyle(.plain)
                        .disabled(esperando)
                        .accessibilityIdentifier("pokemon-\(pokemon.id)")
                    }
                }
            }
            .padding(.horizontal, 20)
            .padding(.top, 20)
            .padding(.bottom, 120)
        }
        .background(Color(.systemGroupedBackground))
        .safeAreaInset(edge: .bottom) { barraInferior }
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
