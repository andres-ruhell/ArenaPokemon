# Arena Pokémon

Juego en línea para dos jugadores. El servidor en Node.js consulta la [PokeAPI](https://pokeapi.co), le da a cada jugador **10 Pokémon al azar**, cada uno elige **3** y pelean por turnos con **ataques reales** de la PokeAPI. Hay dos apps nativas, sin frameworks multiplataforma, y pueden jugar entre sí:

| Carpeta | Tecnología |
|---|---|
| `servidor/` | Node.js 24, Express 5 y WebSockets (`ws`) |
| `ios/` | Swift y SwiftUI, iOS 17 o superior, sin dependencias |
| `android/` | Kotlin y Jetpack Compose; OkHttp para el WebSocket |

Una partida iPhone contra Android pasa siempre por el mismo servidor, que es quien decide el resultado de cada turno.

## Cómo jugar en esta Mac

1. **Enciende el servidor**:

   ```bash
   cd /Users/andres/ap_moviles/programas/ArenaPokemon/servidor
   ./iniciar.command
   ```

2. **iOS**: abre `ios/ArenaPokemon.xcodeproj` en Xcode, elige un simulador de iPhone y pulsa **Run ▶**. El servidor por defecto es `127.0.0.1:3000`.
3. **Android**: abre la carpeta `android/` en Android Studio, elige un emulador y pulsa **Run ▶**. El servidor por defecto es `10.0.2.2:3000`, que es la dirección de la Mac vista desde el emulador.
4. Escribe tu nombre en cada app y pulsa **Buscar rival**. El servidor empareja a los dos primeros jugadores que estén esperando.

Si solo tienes una app abierta, usa el bot como rival:

```bash
cd /Users/andres/ap_moviles/programas/ArenaPokemon/servidor
node bot.js Misty
```

**Teléfonos físicos**: conéctalos a la misma red Wi‑Fi que la Mac y escribe en el campo **Servidor** la IP que muestra la terminal al encender el servidor (por ejemplo `192.168.1.20:3000`).

## Cómo se juega

1. **Elegir equipo**: cada jugador recibe sus propios 10 Pokémon al azar (de los 1025 que hay) y toca 3.
2. **Combate**: cada turno, los dos eligen a la vez entre atacar con uno de los 4 ataques del Pokémon activo o cambiar de Pokémon. Cuando ambos eligieron, el servidor resuelve el turno y las apps lo narran.
3. **Fin**: pierde quien se queda sin Pokémon. Si un jugador abandona o se desconecta, gana el otro.

Reglas del combate, en `servidor/combate.js`:

- Todos los Pokémon están al **nivel 50**: `PS = base + 60` y las demás stats son `base + 5`.
- **Daño**: `floor(floor(22 × poder × A / D) / 50) + 2`. A y D son físicos o especiales según el ataque. Luego se multiplica por:
  - **STAB** ×1.5 si el ataque es del mismo tipo que el Pokémon.
  - **efectividad** de tipos (×2, ×0.5 o ×0), tomada de la PokeAPI.
  - **golpe crítico** ×1.5, con probabilidad de 1/24.
  - un factor aleatorio entre 0.85 y 1.
- Los ataques pueden **fallar** según su precisión.
- **Orden del turno**: primero los cambios; después ataca el Pokémon más veloz. Un Pokémon debilitado ya no ataca, y al final del turno entra automáticamente el siguiente que siga vivo.
- **Ataques**: el servidor elige 4 ataques de daño al azar de la lista real de cada Pokémon, da prioridad a los de su tipo y los muestra con su nombre en español. No usa ataques de más de 120 de poder. Si un Pokémon no tiene ataques de daño (Ditto, por ejemplo), usa *Forcejeo*.

## Protocolo WebSocket

La conexión es `ws://<servidor>:3000/arena` y todos los mensajes son JSON con un campo `tipo`.

| Dirección | `tipo` | Contenido |
|---|---|---|
| app → servidor | `unirse` | `{nombre}` |
| servidor → app | `esperando` | todavía no hay rival |
| servidor → app | `partida` | `{nombre, rival, opciones: [10 Pokémon]}` |
| app → servidor | `elegirEquipo` | `{ids: [3 ids]}` |
| servidor → app | `esperandoRival` | tu equipo está listo y falta el del rival |
| servidor → app | `inicioCombate` | `{eventos, estado}` |
| app → servidor | `accion` | `{accion: "atacar", movimiento: 0-3}` o `{accion: "cambiar", indice: 0-2}` |
| servidor → app | `accionRecibida` | se guardó tu acción y se espera la del rival |
| servidor → app | `turno` | `{eventos, estado}` |
| servidor → app | `fin` | `{ganador, eventos, estado}` |
| servidor → app | `error` | `{mensaje}` |

- `estado` llega desde la perspectiva de quien lo recibe: `yo` trae tu equipo completo con sus ataques, y `rival` solo trae su Pokémon activo y cuántos le quedan vivos.
- Cada evento trae un `texto`, por ejemplo "¡Es muy eficaz!". Los eventos que cambian algo en el campo también traen `lado` (`yo` o `rival`) y los `ps` nuevos, o el `pokemon` que entra. Así las apps narran el turno paso a paso y animan las barras de PS.

## Código

- **Servidor**:
  - `servidor.js`: Express y WebSocket.
  - `arena.js`: emparejamiento, partidas y validación de mensajes.
  - `combate.js`: reglas del combate.
  - `pokeapi.js`: consultas a la PokeAPI con caché.
  - `bot.js`: rival de prueba.
- **iOS** (`ios/ArenaPokemon/`):
  - `ClienteArena.swift`: `URLSessionWebSocketTask`, el estado de la partida y la narración.
  - `Modelos.swift`: los datos del protocolo.
  - Las vistas: `InicioView`, `BuscandoView`, `SeleccionView`, `CombateView` y `FinView`.
- **Android** (`android/app/src/main/java/com/ibero/arenapokemon/`):
  - `ArenaViewModel.kt`: WebSocket con OkHttp y el estado de la partida.
  - `Modelos.kt`: los datos del protocolo, leídos con `org.json`.
  - `Pantallas.kt` y `CombatePantalla.kt`: las pantallas en Compose.
  - `Componentes.kt`: barra de PS, colores de tipo y carga de sprites con OkHttp.

## Pruebas realizadas

- `cd servidor && node --test`: 8 pruebas del combate (fórmula de daño, efectividad, inmunidad, orden por velocidad, cambio antes de ataque, fallo por precisión, cambio automático y ganador).
- **Dos bots** (`node bot.js Ash` y `node bot.js Misty`): partida completa de 18 turnos contra la PokeAPI real.
- **iOS** en el simulador de iPhone 18 Pro (iOS 27): partida completa contra el bot, con cambio manual, cambio automático e inmunidad de tipo, y victoria.
- **Android** en el emulador Pixel 4 (API 29): partida completa contra el bot.
- **iOS contra Android** en la misma partida. Los dos narran el mismo turno. Al pulsar **Abandonar** en iOS, Android muestra "¡Ganaste! Andres abandonó la partida."

Las capturas están en `capturas/`.

## Compilar desde la terminal

```bash
# iOS (después de modificar project.yml: xcodegen generate)
cd ios && xcodebuild -project ArenaPokemon.xcodeproj -scheme ArenaPokemon -destination 'platform=iOS Simulator,name=iPhone 18 Pro' build

# Android (con el JDK de Android Studio)
cd android && JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew assembleDebug
```

La APK queda en `android/app/build/outputs/apk/debug/app-debug.apk`.
