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
    val accionEnviada: Boolean = false,
    val ganador: String? = null,
    val motivoFin: String = "",
    val mensajeError: String? = null
) {
    val gane get() = ganador == miNombre
    val puedeActuar get() = fase == Fase.COMBATE && !narrando && !accionEnviada
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
    private var ultimoNombre = ""
    private var ultimoServidor = ""

    fun conectar(nombre: String, servidor: String) {
        desconectar()
        val direccion = servidor.trim()
        val peticion = try {
            Request.Builder().url("ws://$direccion/arena").build()
        } catch (error: IllegalArgumentException) {
            _ui.update { it.copy(mensajeError = "Escribe la dirección del servidor, por ejemplo 10.0.2.2:3000.") }
            return
        }
        ultimoNombre = nombre
        ultimoServidor = direccion
        _ui.update { it.copy(fase = Fase.BUSCANDO, miNombre = nombre, mensajeError = null) }

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
        enviar(JSONObject().put("tipo", "unirse").put("nombre", nombre))

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

    fun jugarOtraVez() = conectar(ultimoNombre, ultimoServidor)

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
            "esperando" -> _ui.update { it.copy(fase = Fase.BUSCANDO) }
            "partida" -> _ui.update {
                it.copy(
                    fase = Fase.ELIGIENDO,
                    miNombre = json.optString("nombre", it.miNombre),
                    rival = json.optString("rival"),
                    opciones = opcionesDe(json),
                    estado = null,
                    miPokemon = null,
                    pokemonRival = null,
                    ganador = null,
                    preparandoEquipo = false
                )
            }
            "esperandoRival" -> _ui.update { it.copy(fase = Fase.ESPERANDO_RIVAL, preparandoEquipo = false) }
            "inicioCombate", "turno" -> {
                _ui.update { it.copy(fase = Fase.COMBATE, preparandoEquipo = false) }
                narrar(eventosDe(json))
                json.optJSONObject("estado")?.let { aplicar(estadoCombateDe(it)) }
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
            _ui.update { ui ->
                val conTexto = ui.copy(narracion = evento.texto)
                val ps = evento.ps
                when {
                    evento.pokemon != null && evento.lado == "yo" -> conTexto.copy(miPokemon = evento.pokemon)
                    evento.pokemon != null -> conTexto.copy(pokemonRival = evento.pokemon)
                    ps != null && evento.lado == "yo" -> conTexto.copy(
                        miPokemon = ui.miPokemon?.copy(ps = ps, debilitado = ps == 0),
                        golpesMios = ui.golpesMios + 1
                    )
                    ps != null -> conTexto.copy(
                        pokemonRival = ui.pokemonRival?.copy(ps = ps, debilitado = ps == 0),
                        golpesRival = ui.golpesRival + 1
                    )
                    else -> conTexto
                }
            }
            delay(PAUSA_EVENTO)
        }
        _ui.update { it.copy(narrando = false) }
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
    }
}
