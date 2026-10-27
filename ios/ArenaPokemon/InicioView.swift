import SwiftUI

struct InicioView: View {
    let cliente: ClienteArena
    @AppStorage("nombre") private var nombre = ""
    // El simulador de iOS llega al servidor de la Mac por 127.0.0.1.
    @AppStorage("servidor") private var servidor = "127.0.0.1:3000"
    @FocusState private var campoActivo: Campo?

    private enum Campo { case nombre, servidor }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 32) {
                VStack(alignment: .leading, spacing: 18) {
                    Pokebola().frame(width: 64)
                    VStack(alignment: .leading, spacing: 10) {
                        Text("Arena Pokémon")
                            .font(.system(size: 34, weight: .heavy, design: .rounded))
                        Text("Recibe 10 Pokémon al azar, elige a 3 y reta a otro entrenador.")
                            .font(.system(size: 16))
                            .foregroundStyle(.secondary)
                    }
                }

                VStack(spacing: 20) {
                    campo("Tu nombre de entrenador", texto: $nombre, ejemplo: "Ash", campo: .nombre)
                    campo("Servidor", texto: $servidor, ejemplo: "127.0.0.1:3000", campo: .servidor)
                }

                if let error = cliente.mensajeError {
                    Label(error, systemImage: "exclamationmark.circle")
                        .font(.subheadline)
                        .foregroundStyle(.red)
                        .accessibilityIdentifier("error")
                }

                VStack(spacing: 12) {
                    BotonPrincipal(titulo: "Buscar rival", icono: "bolt.fill") {
                        campoActivo = nil
                        buscar(contraCPU: false)
                    }
                    .accessibilityIdentifier("buscar")

                    Button {
                        campoActivo = nil
                        buscar(contraCPU: true)
                    } label: {
                        Label("Jugar contra la CPU", systemImage: "cpu")
                            .font(.system(size: 17, weight: .semibold, design: .rounded))
                            .foregroundStyle(Color.rojoPokebola)
                            .frame(maxWidth: .infinity)
                            .frame(height: 52)
                            .background(Color.rojoPokebola.opacity(0.1), in: RoundedRectangle(cornerRadius: 18))
                    }
                    .buttonStyle(.plain)
                    .accessibilityIdentifier("contraCPU")
                }
            }
            .frame(maxWidth: 480)
            .padding(.horizontal, 28)
            .padding(.vertical, 32)
            .frame(maxWidth: .infinity)
        }
        .background(Color(.systemGroupedBackground))
        .scrollDismissesKeyboard(.interactively)
    }

    private func buscar(contraCPU: Bool) {
        let limpio = nombre.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !limpio.isEmpty else {
            cliente.mensajeError = "Escribe tu nombre de entrenador."
            return
        }
        cliente.conectar(nombre: limpio, servidor: servidor, contraCPU: contraCPU)
    }

    private func campo(_ titulo: String, texto: Binding<String>, ejemplo: String, campo: Campo) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(titulo)
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(.secondary)
            TextField(ejemplo, text: texto)
                .font(.system(size: 20, weight: .medium, design: .rounded))
                .textInputAutocapitalization(campo == .nombre ? .words : .never)
                .autocorrectionDisabled()
                .keyboardType(campo == .servidor ? .URL : .default)
                .focused($campoActivo, equals: campo)
                .submitLabel(campo == .nombre ? .next : .go)
                .onSubmit {
                    if campo == .nombre { campoActivo = .servidor } else { campoActivo = nil; buscar(contraCPU: false) }
                }
                .padding(.horizontal, 18)
                .frame(height: 58)
                .background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 16))
                .overlay {
                    RoundedRectangle(cornerRadius: 16)
                        .stroke(campoActivo == campo ? Color.rojoPokebola : .clear, lineWidth: 2)
                }
                .accessibilityIdentifier(campo == .nombre ? "nombre" : "servidor")
        }
    }
}
