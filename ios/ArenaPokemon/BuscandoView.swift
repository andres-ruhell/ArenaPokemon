import SwiftUI

struct BuscandoView: View {
    let cliente: ClienteArena
    @State private var girando = false

    var body: some View {
        VStack(spacing: 28) {
            Spacer()
            Pokebola()
                .frame(width: 96)
                .rotationEffect(.degrees(girando ? 360 : 0))
                .animation(.linear(duration: 1.4).repeatForever(autoreverses: false), value: girando)
                .onAppear { girando = true }
            VStack(spacing: 8) {
                Text(cliente.contraCPU ? "Preparando a la CPU…" : "Buscando rival…")
                    .font(.system(size: 26, weight: .bold, design: .rounded))
                Group {
                    if cliente.contraCPU {
                        Text("El servidor está eligiendo los Pokémon de tu rival.")
                    } else if let limite = cliente.esperaHasta {
                        TimelineView(.periodic(from: .now, by: 0.25)) { contexto in
                            let restantes = max(0, Int(limite.timeIntervalSince(contexto.date).rounded(.up)))
                            Text(restantes > 0
                                 ? "Si nadie llega en \(restantes) s, juegas contra la CPU."
                                 : "Nadie llegó: preparando a la CPU…")
                                .monospacedDigit()
                        }
                    } else {
                        Text("La partida empieza cuando otro entrenador se conecte.")
                    }
                }
                .font(.system(size: 15))
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
            }
            Spacer()
            Button("Cancelar", role: .cancel) { cliente.cancelar() }
                .font(.system(size: 17, weight: .semibold))
                .padding(.bottom, 24)
        }
        .padding(.horizontal, 32)
        .frame(maxWidth: .infinity)
        .background(Color(.systemGroupedBackground))
    }
}
