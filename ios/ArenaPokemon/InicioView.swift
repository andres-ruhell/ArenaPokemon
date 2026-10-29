import Network
import SwiftUI

struct InicioView: View {
    let cliente: ClienteArena
    @AppStorage("nombre") private var nombre = ""
    @State private var buscador = BuscadorServidor()
    @State private var sinRespuesta = false
    @FocusState private var escribiendo: Bool

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

                VStack(alignment: .leading, spacing: 10) {
                    Text("Tu nombre de entrenador")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(.secondary)
                    TextField("Ash", text: $nombre)
                        .font(.system(size: 20, weight: .medium, design: .rounded))
                        .textInputAutocapitalization(.words)
                        .autocorrectionDisabled()
                        .focused($escribiendo)
                        .submitLabel(.done)
                        .padding(.horizontal, 18)
                        .frame(height: 58)
                        .background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 16))
                        .overlay {
                            RoundedRectangle(cornerRadius: 16)
                                .stroke(escribiendo ? Color.rojoPokebola : .clear, lineWidth: 2)
                        }
                        .accessibilityIdentifier("nombre")
                    estadoServidor
                }

                if let error = cliente.mensajeError {
                    Label(error, systemImage: "exclamationmark.circle")
                        .font(.subheadline)
                        .foregroundStyle(.red)
                        .accessibilityIdentifier("error")
                }

                VStack(spacing: 12) {
                    BotonPrincipal(titulo: "Buscar rival", icono: "bolt.fill") {
                        buscar(contraCPU: false)
                    }
                    .accessibilityIdentifier("buscar")

                    Button {
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
                .disabled(servidor == nil)
                .opacity(servidor == nil ? 0.45 : 1)
            }
            .frame(maxWidth: 480)
            .padding(.horizontal, 28)
            .padding(.vertical, 32)
            .frame(maxWidth: .infinity)
        }
        .background(Color(.systemGroupedBackground))
        .scrollDismissesKeyboard(.interactively)
        .onAppear { buscador.iniciar() }
        .onDisappear { buscador.detener() }
        .task {
            try? await Task.sleep(for: .seconds(5))
            sinRespuesta = true
        }
    }

    // El servidor que se anuncia en la red. Sin él, el simulador usa el de la Mac en la que corre.
    private var servidor: String? {
        if let encontrado = buscador.encontrado { return encontrado }
        #if targetEnvironment(simulator)
        return "127.0.0.1:3000"
        #else
        return nil
        #endif
    }

    @ViewBuilder private var estadoServidor: some View {
        Group {
            if buscador.encontrado != nil {
                Label("Servidor encontrado en tu red", systemImage: "checkmark.circle.fill")
                    .foregroundStyle(.green)
            } else if !sinRespuesta {
                Label("Buscando el servidor en tu red…", systemImage: "antenna.radiowaves.left.and.right")
                    .foregroundStyle(.secondary)
            } else if servidor != nil {
                Label("Usando el servidor de esta Mac", systemImage: "desktopcomputer")
                    .foregroundStyle(.secondary)
            } else {
                Label("No encontré el servidor. Enciéndelo y conéctate a la misma Wi‑Fi.", systemImage: "wifi.exclamationmark")
                    .foregroundStyle(.orange)
            }
        }
        .font(.system(size: 13, weight: .medium))
        .padding(.top, 4)
        .accessibilityIdentifier("estadoServidor")
    }

    private func buscar(contraCPU: Bool) {
        escribiendo = false
        let limpio = nombre.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !limpio.isEmpty else {
            cliente.mensajeError = "Escribe tu nombre de entrenador."
            return
        }
        guard let servidor else { return }
        cliente.conectar(nombre: limpio, servidor: servidor, contraCPU: contraCPU)
    }
}

// Busca en la red local el servidor que se anuncia por Bonjour como _arenapokemon._tcp,
// para no tener que escribir su IP.
@Observable
@MainActor
final class BuscadorServidor {
    private(set) var encontrado: String?
    @ObservationIgnored private var navegador: NWBrowser?
    @ObservationIgnored private var prueba: NWConnection?

    func iniciar() {
        guard navegador == nil else { return }
        let navegador = NWBrowser(for: .bonjour(type: "_arenapokemon._tcp", domain: nil), using: .tcp)
        navegador.browseResultsChangedHandler = { [weak self] resultados, _ in
            guard let servicio = resultados.first?.endpoint else { return }
            MainActor.assumeIsolated { self?.resolver(servicio) }
        }
        navegador.start(queue: .main)
        self.navegador = navegador
    }

    func detener() {
        navegador?.cancel()
        navegador = nil
        prueba?.cancel()
        prueba = nil
    }

    // Bonjour solo da el nombre del servicio; una conexión de prueba revela su IPv4 y su puerto.
    private func resolver(_ servicio: NWEndpoint) {
        guard prueba == nil else { return }
        let parametros = NWParameters.tcp
        (parametros.defaultProtocolStack.internetProtocol as? NWProtocolIP.Options)?.version = .v4
        let conexion = NWConnection(to: servicio, using: parametros)
        conexion.stateUpdateHandler = { [weak self] estado in
            MainActor.assumeIsolated {
                switch estado {
                case .ready:
                    if case .hostPort(let host, let puerto) = conexion.currentPath?.remoteEndpoint {
                        let ip = "\(host)".split(separator: "%").first.map(String.init) ?? "\(host)"
                        self?.encontrado = "\(ip):\(puerto)"
                    }
                    conexion.cancel()
                case .failed, .cancelled:
                    self?.prueba = nil
                default:
                    break
                }
            }
        }
        conexion.start(queue: .main)
        prueba = conexion
    }
}
