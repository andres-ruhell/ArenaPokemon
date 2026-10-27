import SwiftUI

extension Color {
    static let rojoPokebola = Color(red: 0.89, green: 0.21, blue: 0.05)

    // Colores clásicos de los 18 tipos.
    static func tipo(_ clave: String) -> Color {
        let hex: UInt32 = switch clave {
        case "normal": 0xA8A77A
        case "fire": 0xEE8130
        case "water": 0x6390F0
        case "electric": 0xF7D02C
        case "grass": 0x7AC74C
        case "ice": 0x96D9D6
        case "fighting": 0xC22E28
        case "poison": 0xA33EA1
        case "ground": 0xE2BF65
        case "flying": 0xA98FF3
        case "psychic": 0xF95587
        case "bug": 0xA6B91A
        case "rock": 0xB6A136
        case "ghost": 0x735797
        case "dragon": 0x6F35FC
        case "dark": 0x705746
        case "steel": 0xB7B7CE
        case "fairy": 0xD685AD
        default: 0x8E8E93
        }
        return Color(red: Double((hex >> 16) & 0xFF) / 255,
                     green: Double((hex >> 8) & 0xFF) / 255,
                     blue: Double(hex & 0xFF) / 255)
    }

    // Texto oscuro sobre los tipos de color claro para que se lea bien.
    static func textoSobreTipo(_ clave: String) -> Color {
        ["electric", "ice", "ground", "steel", "normal", "bug", "rock"].contains(clave)
            ? .black.opacity(0.8) : .white
    }
}

struct Pokebola: View {
    var body: some View {
        GeometryReader { geometria in
            let lado = min(geometria.size.width, geometria.size.height)
            let linea = lado * 0.07
            ZStack {
                Circle().fill(.white)
                Circle()
                    .fill(Color.rojoPokebola)
                    .mask(VStack(spacing: 0) { Rectangle(); Color.clear })
                Rectangle().fill(.black).frame(height: linea)
                Circle()
                    .fill(.white)
                    .frame(width: lado * 0.34)
                    .overlay(Circle().stroke(.black, lineWidth: linea))
                Circle().stroke(.black, lineWidth: linea)
            }
            .frame(width: lado, height: lado)
        }
        .aspectRatio(1, contentMode: .fit)
        .accessibilityHidden(true)
    }
}

struct SpriteRemoto: View {
    let url: String
    var pixelado = false

    var body: some View {
        AsyncImage(url: URL(string: url)) { fase in
            if let imagen = fase.image {
                imagen
                    .resizable()
                    .interpolation(pixelado ? .none : .medium)
                    .scaledToFit()
            } else if fase.error != nil {
                Image(systemName: "questionmark")
                    .font(.title2.weight(.bold))
                    .foregroundStyle(.secondary)
            } else {
                ProgressView()
            }
        }
        .id(url)
    }
}

struct ChipTipo: View {
    let tipo: Tipo
    var compacto = false

    var body: some View {
        Text(tipo.nombre.uppercased())
            .font(.system(size: compacto ? 9 : 10, weight: .heavy))
            .tracking(0.6)
            .foregroundStyle(Color.textoSobreTipo(tipo.clave))
            .padding(.horizontal, compacto ? 6 : 8)
            .padding(.vertical, compacto ? 3 : 4)
            .background(Color.tipo(tipo.clave), in: Capsule())
    }
}

struct BarraPS: View {
    let ps: Int
    let psMax: Int

    private var fraccion: Double { psMax > 0 ? Double(ps) / Double(psMax) : 0 }
    private var color: Color {
        fraccion > 0.5 ? .green : fraccion > 0.2 ? .yellow : .red
    }

    var body: some View {
        HStack(spacing: 6) {
            Text("PS")
                .font(.system(size: 10, weight: .heavy))
                .foregroundStyle(.orange)
            GeometryReader { geometria in
                ZStack(alignment: .leading) {
                    Capsule().fill(Color.black.opacity(0.12))
                    Capsule()
                        .fill(color)
                        .frame(width: geometria.size.width * fraccion)
                }
            }
            .frame(height: 8)
        }
        .accessibilityElement()
        .accessibilityLabel("Puntos de salud")
        .accessibilityValue("\(ps) de \(psMax)")
    }
}

struct BotonPrincipal: View {
    let titulo: String
    var icono: String?
    var cargando = false
    let accion: () -> Void

    var body: some View {
        Button(action: accion) {
            HStack(spacing: 10) {
                if cargando {
                    ProgressView().tint(.white)
                } else if let icono {
                    Image(systemName: icono)
                }
                Text(titulo).font(.system(size: 18, weight: .bold, design: .rounded))
            }
            .foregroundStyle(.white)
            .frame(maxWidth: .infinity)
            .frame(height: 56)
            .background(Color.rojoPokebola, in: RoundedRectangle(cornerRadius: 18))
        }
        .buttonStyle(.plain)
    }
}
