// Rival de prueba: se une a la arena, elige 3 Pokémon al azar y ataca al azar.
// Uso: node bot.js [nombre] [servidor]   →   node bot.js Misty 127.0.0.1:3000
const nombre = process.argv[2] || "Bot";
const servidor = process.argv[3] || `127.0.0.1:${process.env.PORT || 3000}`;
const PAUSA = 1500;

const socket = new WebSocket(`ws://${servidor}/arena`);
const enviar = (mensaje) => socket.send(JSON.stringify(mensaje));
const azar = (n) => Math.floor(Math.random() * n);

function imprimir(eventos) {
  for (const evento of eventos) {
    console.log(`  ${evento.texto}`);
  }
}

function jugar(estado) {
  const disponibles = estado.yo.equipo
    .map((p, i) => i)
    .filter((i) => i !== estado.yo.activo && !estado.yo.equipo[i].debilitado);

  setTimeout(() => {
    if (disponibles.length > 0 && Math.random() < 0.15) {
      enviar({ tipo: "accion", accion: "cambiar", indice: disponibles[azar(disponibles.length)] });
    } else {
      const activo = estado.yo.equipo[estado.yo.activo];
      enviar({ tipo: "accion", accion: "atacar", movimiento: azar(activo.movimientos.length) });
    }
  }, PAUSA);
}

socket.addEventListener("open", () => {
  console.log(`${nombre} conectado a ${servidor}`);
  enviar({ tipo: "unirse", nombre });
});

socket.addEventListener("message", (evento) => {
  const mensaje = JSON.parse(evento.data);
  switch (mensaje.tipo) {
    case "esperando":
      console.log("Esperando rival…");
      break;
    case "partida": {
      const equipo = [...mensaje.opciones].sort(() => Math.random() - 0.5).slice(0, 3);
      console.log(`Rival: ${mensaje.rival}. Equipo: ${equipo.map((p) => p.nombre).join(", ")}`);
      enviar({ tipo: "elegirEquipo", ids: equipo.map((p) => p.id) });
      break;
    }
    case "inicioCombate":
    case "turno":
      console.log(`Turno ${mensaje.estado.turno}`);
      imprimir(mensaje.eventos);
      jugar(mensaje.estado);
      break;
    case "fin":
      imprimir(mensaje.eventos);
      console.log(mensaje.ganador === nombre ? "¡Gané!" : `Perdí. Ganó ${mensaje.ganador}.`);
      socket.close();
      break;
    case "error":
      console.error(`Error: ${mensaje.mensaje}`);
      break;
  }
});

socket.addEventListener("error", () => {
  console.error(`No se pudo conectar a ws://${servidor}/arena. ¿Está encendido el servidor?`);
});
