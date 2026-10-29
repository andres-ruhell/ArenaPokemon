package com.ibero.arenapokemon

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.concurrent.TimeUnit

enum class Fase { INICIO, BUSCANDO, ELIGIENDO, ESPERANDO_RIVAL, COMBATE, FIN }

data class EstadoUi(
    val fase: Fase = Fase.INICIO,
    val contraCPU: Boolean = false,
    // Cuándo (System.currentTimeMillis) el servidor pone a la CPU de rival si no llega otro jugador.
    val esperaHasta: Long? = null,
    val miNombre: String = "",
    val rival: String = "",
    val opciones: List<PokemonResumen> = emptyList(),
    val estado: EstadoCombate? = null,
    val preparandoEquipo: Boolean = false,
    // Lo que se ve en el campo mientras se narran los eventos de un turno.
    val miPokemon: PokemonCombate? = null,
    val pokemonRival: PokemonCombate? = null,
    val narracion: String = "",
    val narrando: Boolean = false,
    val golpesMios: Int = 0,
    val golpesRival: Int = 0,
    val ataquesMios: Int = 0,
    val ataquesRival: Int = 0,
    val impactoMio: Impacto? = null,
    val impactoRival: Impacto? = null,
    val accionEnviada: Boolean = false,
    // Hasta cuándo (System.currentTimeMillis) hay para elegir el movimiento; después el servidor ataca al azar.
    val turnoHasta: Long? = null,
    val turnoAgotado: Boolean = false,
    val ganador: String? = null,
    val motivoFin: String = "",
    val mensajeError: String? = null
) {
    val gane get() = ganador == miNombre
    val puedeActuar get() = fase == Fase.COMBATE && !narrando && !accionEnviada && !turnoAgotado
}

// Un solo cliente HTTP para el WebSocket y las imágenes.
object Red {
    val cliente: OkHttpClient = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .build()
}

private sealed interface Entrada {
    data class Mensaje(val texto: String) : Entrada
    data object Perdida : Entrada
}

// Conexión WebSocket con el servidor y estado de la partida que muestran las pantallas.
class ArenaViewModel : ViewModel() {
    private val _ui = MutableStateFlow(EstadoUi())
    val ui: StateFlow<EstadoUi> = _ui

    private var socket: WebSocket? = null
    private var lector: Job? = null
    private var relojTurno: Job? = null
    private var ultimoNombre = ""
    private var ultimoServidor = ""
    private var impactos = 0

    // Con `contraCPU` el servidor pone de rival a su CPU en vez de esperar a otro jugador.
    fun conectar(nombre: String, servidor: String, contraCPU: Boolean = false) {
        desconectar()
        val direccion = servidor.trim()
        val peticion = try {
            Request.Builder().url("ws://$direccion/arena").build()
        } catch (error: IllegalArgumentException) {
            _ui.update { it.copy(mensajeError = "No se encontró el servidor.") }
            return
        }
        ultimoNombre = nombre
        ultimoServidor = direccion
        _ui.update {
            it.copy(fase = Fase.BUSCANDO, contraCPU = contraCPU, esperaHasta = null, miNombre = nombre, mensajeError = null)
        }

        // OkHttp avisa en su propio hilo; el canal entrega los mensajes en orden al hilo principal.
        val canal = Channel<Entrada>(Channel.UNLIMITED)
        socket = Red.cliente.newWebSocket(peticion, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                canal.trySend(Entrada.Mensaje(text))
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                canal.trySend(Entrada.Perdida)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                canal.trySend(Entrada.Perdida)
            }
        })
        enviar(JSONObject().put("tipo", if (contraCPU) "jugarCPU" else "unirse").put("nombre", nombre))

        lector = viewModelScope.launch {
            for (entrada in canal) {
                when (entrada) {
                    is Entrada.Mensaje -> procesar(entrada.texto)
                    Entrada.Perdida -> {
                        conexionPerdida()
                        break
                    }
                }
            }
        }
    }

    fun jugarOtraVez() = conectar(ultimoNombre, ultimoServidor, _ui.value.contraCPU)

    fun cancelar() {
        desconectar()
        _ui.update { it.copy(fase = Fase.INICIO) }
    }

    fun elegirEquipo(ids: List<Int>) {
        _ui.update { it.copy(preparandoEquipo = true, mensajeError = null) }
        enviar(JSONObject().put("tipo", "elegirEquipo").put("ids", JSONArray(ids)))
    }

    fun atacar(movimiento: Int) {
        _ui.update { it.copy(accionEnviada = true) }
        enviar(JSONObject().put("tipo", "accion").put("accion", "atacar").put("movimiento", movimiento))
    }

    fun cambiar(indice: Int) {
        _ui.update { it.copy(accionEnviada = true) }
        enviar(JSONObject().put("tipo", "accion").put("accion", "cambiar").put("indice", indice))
    }

    override fun onCleared() = desconectar()

    private fun enviar(mensaje: JSONObject) {
        socket?.send(mensaje.toString())
    }

    private fun desconectar() {
        detenerReloj()
        lector?.cancel()
        lector = null
        socket?.close(1000, null)
        socket = null
    }

    private fun conexionPerdida() {
        socket = null
        if (_ui.value.fase != Fase.FIN) {
            _ui.update { it.copy(fase = Fase.INICIO, mensajeError = "Se perdió la conexión con el servidor.") }
        }
    }

    private suspend fun procesar(texto: String) {
        val json = try {
            JSONObject(texto)
        } catch (error: JSONException) {
            return
        }

        when (json.optString("tipo")) {
            "esperando" -> {
                val segundos = json.optInt("segundos", 0)
                val hasta = if (segundos > 0) System.currentTimeMillis() + segundos * 1000L else null
                _ui.update { it.copy(fase = Fase.BUSCANDO, esperaHasta = hasta) }
            }
            "partida" -> _ui.update {
                it.copy(
                    fase = Fase.ELIGIENDO,
                    miNombre = json.optString("nombre", it.miNombre),
                    rival = json.optString("rival"),
                    opciones = opcionesDe(json),
                    estado = null,
                    miPokemon = null,
                    pokemonRival = null,
                    impactoMio = null,
                    impactoRival = null,
                    ganador = null,
                    preparandoEquipo = false
                )
            }
            "esperandoRival" -> _ui.update { it.copy(fase = Fase.ESPERANDO_RIVAL, preparandoEquipo = false) }
            "inicioCombate", "turno" -> {
                detenerReloj()
                _ui.update { it.copy(fase = Fase.COMBATE, preparandoEquipo = false) }
                narrar(eventosDe(json))
                json.optJSONObject("estado")?.let { aplicar(estadoCombateDe(it)) }
                iniciarReloj(json.optInt("tiempo", 0))
            }
            "fin" -> {
                val eventos = eventosDe(json)
                narrar(eventos)
                json.optJSONObject("estado")?.let { aplicar(estadoCombateDe(it)) }
                _ui.update {
                    it.copy(
                        fase = Fase.FIN,
                        ganador = json.optString("ganador"),
                        motivoFin = eventos.lastOrNull()?.texto ?: ""
                    )
                }
                desconectar()
            }
            "error" -> {
                _ui.update {
                    it.copy(mensajeError = json.optString("mensaje"), preparandoEquipo = false, accionEnviada = false)
                }
                if (_ui.value.fase == Fase.BUSCANDO) {
                    cancelar()
                }
            }
        }
    }

    private suspend fun narrar(eventos: List<Evento>) {
        _ui.update { it.copy(narrando = true) }
        for (evento in eventos) {
            val mio = evento.lado == "yo"
            val impacto = when (evento.efecto) {
                "golpe" -> impactoDeGolpe(evento)
                "fallo" -> nuevoImpacto("Falló", EstiloImpacto.SIN_EFECTO, null)
                "inmune" -> nuevoImpacto("Sin efecto", EstiloImpacto.SIN_EFECTO, null)
                else -> null
            }
            _ui.update { ui ->
                val base = ui.copy(narracion = evento.texto)
                when (evento.efecto) {
                    "ataque" ->
                        if (mio) base.copy(ataquesMios = ui.ataquesMios + 1)
                        else base.copy(ataquesRival = ui.ataquesRival + 1)
                    // El golpe baja los PS, muestra el número y hace parpadear al Pokémon.
                    "golpe" -> {
                        val ps = evento.ps ?: 0
                        if (mio) base.copy(miPokemon = ui.miPokemon?.copy(ps = ps), impactoMio = impacto, golpesMios = ui.golpesMios + 1)
                        else base.copy(pokemonRival = ui.pokemonRival?.copy(ps = ps), impactoRival = impacto, golpesRival = ui.golpesRival + 1)
                    }
                    "fallo", "inmune" ->
                        if (mio) base.copy(impactoMio = impacto) else base.copy(impactoRival = impacto)
                    "debilitado" ->
                        if (mio) base.copy(miPokemon = ui.miPokemon?.copy(debilitado = true))
                        else base.copy(pokemonRival = ui.pokemonRival?.copy(debilitado = true))
                    else -> when {
                        evento.pokemon != null && mio -> base.copy(miPokemon = evento.pokemon)
                        evento.pokemon != null -> base.copy(pokemonRival = evento.pokemon)
                        else -> base
                    }
                }
            }
            // Tras la embestida el golpe llega enseguida.
            delay(if (evento.efecto == "ataque") PAUSA_ATAQUE else PAUSA_EVENTO)
        }
        _ui.update { it.copy(narrando = false) }
    }

    private fun impactoDeGolpe(evento: Evento): Impacto {
        val danio = evento.danio ?: 0
        val efectividad = evento.efectividad ?: 1.0
        if (evento.critico) {
            return nuevoImpacto("¡−$danio!", EstiloImpacto.CRITICO, evento.tipo)
        }
        val estilo = when {
            efectividad > 1 -> EstiloImpacto.EFICAZ
            efectividad < 1 -> EstiloImpacto.POCO_EFICAZ
            else -> EstiloImpacto.NORMAL
        }
        return nuevoImpacto("−$danio", estilo, evento.tipo)
    }

    private fun nuevoImpacto(texto: String, estilo: EstiloImpacto, tipo: String?): Impacto {
        impactos += 1
        return Impacto(impactos, texto, estilo, tipo)
    }

    // El reloj empieza cuando termina la narración, igual que en el servidor.
    private fun iniciarReloj(segundos: Int) {
        detenerReloj()
        if (segundos <= 0) return
        _ui.update { it.copy(turnoHasta = System.currentTimeMillis() + segundos * 1000L) }
        relojTurno = viewModelScope.launch {
            delay(segundos * 1000L)
            _ui.update { it.copy(turnoAgotado = true) }
        }
    }

    private fun detenerReloj() {
        relojTurno?.cancel()
        relojTurno = null
        _ui.update { it.copy(turnoHasta = null, turnoAgotado = false) }
    }

    private fun aplicar(estado: EstadoCombate) = _ui.update {
        it.copy(
            estado = estado,
            miPokemon = estado.yo.equipo[estado.yo.activo],
            pokemonRival = estado.rival.activo,
            accionEnviada = false
        )
    }

    private companion object {
        const val PAUSA_EVENTO = 900L
        const val PAUSA_ATAQUE = 450L
    }
}
