import SwiftUI

// Muestra la pantalla que corresponde a la fase de la partida.
struct ContentView: View {
    @State private var cliente = ClienteArena()

    var body: some View {
        Group {
            switch cliente.fase {
            case .inicio:
                InicioView(cliente: cliente)
            case .buscando:
                BuscandoView(cliente: cliente)
            case .eligiendo, .esperandoRival:
                SeleccionView(cliente: cliente)
            case .combate:
                CombateView(cliente: cliente)
            case .fin:
                FinView(cliente: cliente)
            }
        }
        .tint(Color.rojoPokebola)
        .animation(.easeInOut(duration: 0.25), value: cliente.fase)
    }
}

#Preview {
    ContentView()
}
