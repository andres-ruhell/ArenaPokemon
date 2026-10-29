import SwiftUI

struct FinView: View {
    let cliente: ClienteArena

    var body: some View {
        VStack(spacing: 24) {
            Spacer()
            Image(systemName: cliente.gane ? "trophy.fill" : "flag.checkered")
                .font(.system(size: 64, weight: .bold))
                .foregroundStyle(cliente.gane ? Color.yellow : Color.secondary)
                .symbolEffect(.bounce, value: cliente.gane)
            VStack(spacing: 10) {
                Text(cliente.gane ? "¡Ganaste!" : "Perdiste")
                    .font(.system(size: 40, weight: .heavy, design: .rounded))
                    .accessibilityIdentifier("resultado")
                Text(cliente.motivoFin)
                    .font(.system(size: 17))
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
            }
            if let equipo = cliente.estado?.yo.equipo {
                HStack(spacing: 16) {
                    ForEach(Array(equipo.enumerated()), id: \.offset) { _, pokemon in
                        VStack(spacing: 4) {
                            SpriteRemoto(url: pokemon.spriteFrente, pixelado: true)
                                .frame(width: 72, height: 72)
                                .grayscale(pokemon.debilitado ? 1 : 0)
                            Text(pokemon.nombre)
                                .font(.system(size: 12, weight: .semibold))
                                .lineLimit(1)
                        }
                        .opacity(pokemon.debilitado ? 0.5 : 1)
                    }
                }
            }
            Spacer()
            VStack(spacing: 14) {
                BotonPrincipal(titulo: "Jugar otra vez", icono: "arrow.clockwise") {
                    cliente.jugarOtraVez()
                }
                .accessibilityIdentifier("jugarOtraVez")
                Button("Volver al inicio") { cliente.cancelar() }
                    .font(.system(size: 17, weight: .semibold))
            }
        }
        .padding(.horizontal, 28)
        .padding(.bottom, 24)
        .frame(maxWidth: 480)
        .frame(maxWidth: .infinity)
        .background(Color(.systemGroupedBackground))
    }
}
