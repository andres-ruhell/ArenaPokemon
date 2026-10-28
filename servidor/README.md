# Servidor de la Arena Pokémon

Servidor en Node.js con Express y WebSockets (`ws`). Le da a cada jugador 10 Pokémon al azar de la PokeAPI, empareja a dos jugadores y resuelve cada turno del combate. Usa el puerto **3000** por defecto.

## Ejecutar

Con Node.js 22 o superior, desde esta carpeta:

```bash
npm install
npm start
```

`npm install` solo hace falta la primera vez. En Mac también puedes encenderlo con `./iniciar.command`.

Al arrancar, la terminal muestra la dirección que debe escribir cada app:

```
Arena Pokémon en http://localhost:3000
  Simulador de iOS:     127.0.0.1:3000
  Emulador de Android:  10.0.2.2:3000
  Teléfono en tu red:   192.168.1.20:3000
```

Para revisarlo en el navegador:

- http://localhost:3000: cuántos jugadores esperan y cuántas partidas hay activas.
- http://localhost:3000/pokemon/aleatorios: 10 Pokémon al azar en JSON, con sprites, tipos en español y stats.

Para detener el servidor, presiona `Control + C` en su terminal.

## Rival de prueba

`bot.js` se une a la arena, elige 3 Pokémon al azar y juega solo. Sirve para probar una app sin tener otro teléfono:

```bash
node bot.js Misty
```

Para ver una partida completa sin apps, abre dos terminales y ejecuta `node bot.js Ash` en una y `node bot.js Misty` en la otra.

## Contra la CPU

Las apps tienen el botón **Jugar contra la CPU**, que envía `jugarCPU` en vez de `unirse`. El servidor crea la partida al instante con un rival de `cpu.js`. Ese rival recibe los mismos mensajes que una app y contesta con `elegirEquipo` y `accion`, así que el resto del servidor no lo distingue de un jugador.

Para probarla sin apps:

```bash
node bot.js Ash --cpu
```

## Pruebas

```bash
node --test
```

Prueban las reglas de `combate.js` y las decisiones de `cpu.js` sin conectarse a internet.

## Archivos

- `servidor.js`: Express, el WebSocket en `/arena` y un ping cada 30 s para detectar apps desconectadas.
- `arena.js`: fila de espera, partidas y validación de cada mensaje (ids de Pokémon, ataques, cambios y una sola acción por turno).
- `cpu.js`: la CPU. Elige un equipo fuerte y casi siempre usa su ataque más efectivo.
- `combate.js`: stats al nivel 50, fórmula de daño, efectividad de tipos y orden del turno.
- `pokeapi.js`: consultas a `pokeapi.co` con caché en memoria, para que cada Pokémon, ataque y tipo se pida una sola vez.

## Ajustes

- **Puerto**: `PORT=3001 npm start`.
- **Solo la primera generación**: en `pokeapi.js` cambia `MAX_ID = 1025` por `151`.
