package com.ibero.arenapokemon

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

// El emulador de Android llega al servidor de la Mac por 10.0.2.2.
private const val SERVIDOR_EMULADOR = "10.0.2.2:3000"
private const val TAMANO_EQUIPO = 3

@Composable
fun InicioPantalla(ui: EstadoUi, vm: ArenaViewModel) {
    val contexto = LocalContext.current
    val preferencias = remember { contexto.getSharedPreferences("arena", Context.MODE_PRIVATE) }
    var nombre by rememberSaveable { mutableStateOf(preferencias.getString("nombre", "") ?: "") }
    var servidor by rememberSaveable {
        mutableStateOf(preferencias.getString("servidor", SERVIDOR_EMULADOR) ?: SERVIDOR_EMULADOR)
    }
    val foco = LocalFocusManager.current

    fun buscar(contraCPU: Boolean = false) {
        foco.clearFocus()
        val limpio = nombre.trim()
        if (limpio.isEmpty()) return
        preferencias.edit().putString("nombre", limpio).putString("servidor", servidor.trim()).apply()
        vm.conectar(limpio, servidor, contraCPU)
    }

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Pokebola(Modifier.size(64.dp))
            Text("Arena Pokémon", fontSize = 34.sp, fontWeight = FontWeight.Black)
            Text(
                "Recibe 10 Pokémon al azar, elige a 3 y reta a otro entrenador.",
                fontSize = 16.sp,
                color = TextoSecundario
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            CampoTexto(
                titulo = "Tu nombre de entrenador",
                valor = nombre,
                ejemplo = "Ash",
                onCambio = { nombre = it },
                opciones = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Next
                )
            )
            CampoTexto(
                titulo = "Servidor",
                valor = servidor,
                ejemplo = SERVIDOR_EMULADOR,
                onCambio = { servidor = it },
                opciones = KeyboardOptions(
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go
                ),
                acciones = KeyboardActions(onGo = { buscar() })
            )
        }

        ui.mensajeError?.let { error ->
            Text("⚠ $error", color = Color(0xFFFF3B30), fontSize = 14.sp)
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BotonPrincipal(titulo = "⚡ Buscar rival", onClick = { buscar() }, habilitado = nombre.isNotBlank())
            BotonSecundario(
                titulo = "🤖 Jugar contra la CPU",
                onClick = { buscar(contraCPU = true) },
                habilitado = nombre.isNotBlank()
            )
        }
    }
}

@Composable
private fun CampoTexto(
    titulo: String,
    valor: String,
    ejemplo: String,
    onCambio: (String) -> Unit,
    opciones: KeyboardOptions,
    acciones: KeyboardActions = KeyboardActions.Default
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(titulo, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextoSecundario)
        OutlinedTextField(
            value = valor,
            onValueChange = onCambio,
            placeholder = { Text(ejemplo, fontSize = 20.sp) },
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Medium),
            singleLine = true,
            keyboardOptions = opciones,
            keyboardActions = acciones,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = FondoTarjeta,
                unfocusedContainerColor = FondoTarjeta,
                focusedBorderColor = RojoPokebola,
                unfocusedBorderColor = Color.Transparent
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun BuscandoPantalla(ui: EstadoUi, vm: ArenaViewModel) {
    val giro = rememberInfiniteTransition(label = "giro")
    val angulo by giro.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
        label = "angulo"
    )
    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(1f))
        Pokebola(
            Modifier
                .size(96.dp)
                .rotate(angulo)
        )
        Spacer(Modifier.height(28.dp))
        Text(
            if (ui.contraCPU) "Preparando a la CPU…" else "Buscando rival…",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (ui.contraCPU) "El servidor está eligiendo los Pokémon de tu rival."
            else "La partida empieza cuando otro entrenador se conecte.",
            fontSize = 15.sp,
            color = TextoSecundario,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.weight(1f))
        TextButton(onClick = { vm.cancelar() }) {
            Text("Cancelar", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun SeleccionPantalla(ui: EstadoUi, vm: ArenaViewModel) {
    val seleccion = remember(ui.opciones) { mutableStateListOf<Int>() }
    val esperando = ui.fase == Fase.ESPERANDO_RIVAL || ui.preparandoEquipo

    // Entrada: la Pokébola se sacude, baja al borde inferior y de ella salen los Pokémon.
    val reloj = remember(ui.opciones) { Animatable(0f) }
    LaunchedEffect(reloj) {
        reloj.animateTo(DURACION_ENTRADA, tween(DURACION_ENTRADA.toInt(), easing = LinearEasing))
    }
    var origen by remember { mutableStateOf(Offset.Unspecified) }
    val diametro = with(LocalDensity.current) { DIAMETRO_BOLA.toPx() }

    Box(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
    ) {
        Column(Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(20.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(Modifier.padding(bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Elige a tu equipo", fontSize = 30.sp, fontWeight = FontWeight.Black)
                        Text("Elige 3 Pokémon.", fontSize = 16.sp, color = TextoSecundario)
                    }
                }
                itemsIndexed(ui.opciones, key = { _, pokemon -> pokemon.id }) { indice, pokemon ->
                    val orden = seleccion.indexOf(pokemon.id).takeIf { it >= 0 }?.plus(1)
                    var centro by remember { mutableStateOf(Offset.Unspecified) }
                    // La posición se mide en un contenedor fijo para que la animación no la altere.
                    Box(Modifier.onGloballyPositioned { centro = it.centroEnRaiz() }) {
                        TarjetaPokemon(
                            pokemon = pokemon,
                            orden = orden,
                            habilitada = !esperando,
                            modifier = Modifier.graphicsLayer { salidaDePokebola(reloj.value, indice, origen, centro) }
                        ) {
                            if (orden != null) {
                                seleccion.remove(pokemon.id)
                            } else if (seleccion.size < TAMANO_EQUIPO) {
                                seleccion.add(pokemon.id)
                            }
                        }
                    }
                }
            }

            Column(
                Modifier
                    .graphicsLayer {
                        // El botón sube cuando la Pokébola ya se escondió.
                        val subida = suave(avance(reloj.value, INICIO_BARRA, 350f))
                        translationY = (1f - subida) * size.height
                        alpha = subida
                    }
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.94f))
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ui.mensajeError?.let { Text(it, color = Color(0xFFFF3B30), fontSize = 13.sp) }
                if (ui.fase == Fase.ESPERANDO_RIVAL) {
                    Row(
                        Modifier.height(56.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        Text("Esperando a que ${ui.rival} elija…", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    BotonPrincipal(
                        titulo = if (ui.preparandoEquipo) "Preparando ataques…" else "¡Al combate! ${seleccion.size}/$TAMANO_EQUIPO",
                        onClick = { vm.elegirEquipo(seleccion.toList()) },
                        habilitado = seleccion.size == TAMANO_EQUIPO && !ui.preparandoEquipo,
                        cargando = ui.preparandoEquipo
                    )
                }
            }
        }
        PokebolaDeEntrada(
            tiempo = { reloj.value },
            modifier = Modifier.onGloballyPositioned { area ->
                // Las cartas salen del botón de la Pokébola ya asomada en el borde.
                val posicion = area.positionInRoot()
                origen = Offset(posicion.x + area.size.width / 2f, posicion.y + area.size.height - diametro * 0.1f)
            }
        )
    }
}

private val DIAMETRO_BOLA = 72.dp

// Tiempos (en ms) y curvas de la entrada a la selección. iOS usa los mismos valores.
private const val DURACION_APARICION = 280f
private const val INICIO_SACUDIDAS = 280f
private const val DURACION_SACUDIDAS = 660f
private const val INICIO_BAJADA = 940f
private const val DURACION_BAJADA = 400f
private const val INICIO_VERDE = 1280f
private const val DURACION_VERDE = 240f
private const val INICIO_CARTAS = 1450f
private const val RETRASO_CARTA = 60f
private const val DURACION_CARTA = 650f
private const val INICIO_HUNDIDA = 2600f
private const val DURACION_HUNDIDA = 300f
private const val INICIO_BARRA = 2700f
private const val DURACION_ENTRADA = 3050f

// Verde blancuzco del botón cuando la Pokébola ya está en el borde.
private val BotonVerde = Color(0xFFCCF7D6)

private fun avance(tiempo: Float, inicio: Float, duracion: Float) = ((tiempo - inicio) / duracion).coerceIn(0f, 1f)

// Acelera y frena sin saltos.
private fun suave(x: Float) = if (x < 0.5f) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2

// Como un resorte: llega rápido, se pasa un poco y se acomoda.
private fun resorte(x: Float) = if (x >= 1f) 1f else 1 - exp(-6 * x) * cos(2.2f * PI.toFloat() * x)

private fun verde(tiempo: Float) = suave(avance(tiempo, INICIO_VERDE, DURACION_VERDE))

private fun LayoutCoordinates.centroEnRaiz(): Offset {
    val posicion = positionInRoot()
    return Offset(posicion.x + size.width / 2f, posicion.y + size.height / 2f)
}

// Cada carta sale de detrás de la Pokébola: sube rápido, se abre en curva hacia su lugar,
// crece y se acomoda con un pequeño rebote.
private fun GraphicsLayerScope.salidaDePokebola(tiempo: Float, indice: Int, origen: Offset, centro: Offset) {
    if (origen.isUnspecified || centro.isUnspecified) {
        alpha = if (tiempo >= DURACION_ENTRADA) 1f else 0f
        return
    }
    val progreso = avance(tiempo, INICIO_CARTAS + indice * RETRASO_CARTA, DURACION_CARTA)
    val asentado = resorte(progreso)
    val lateral = 1f - (1f - progreso) * (1f - progreso)
    val escala = 0.15f + 0.85f * asentado
    scaleX = escala
    scaleY = escala
    rotationZ = (if (indice % 2 == 0) -10f else 10f) * (1f - asentado)
    translationX = (origen.x - centro.x) * (1f - lateral)
    translationY = (origen.y - centro.y) * (1f - asentado)
    alpha = (progreso * 6f).coerceAtMost(1f)
}

// Aparece al centro, se sacude tres veces, baja hasta asomarse en el borde inferior
// (se ven sus 3/5), el botón pasa de blanco a verde, suelta las cartas y se esconde.
@Composable
private fun PokebolaDeEntrada(tiempo: () -> Float, modifier: Modifier = Modifier) {
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .clipToBounds()
    ) {
        val alto = maxHeight
        Pokebola(
            colorBoton = { lerp(Color.White, BotonVerde, verde(tiempo())) },
            brillo = { verde(tiempo()) },
            modifier = Modifier
                .align(Alignment.Center)
                .size(DIAMETRO_BOLA)
                .graphicsLayer {
                    val t = tiempo()
                    val diametro = DIAMETRO_BOLA.toPx()
                    val aparicion = avance(t, 0f, DURACION_APARICION)
                    val sacudidas = avance(t, INICIO_SACUDIDAS, DURACION_SACUDIDAS)
                    val emision = avance(t, INICIO_CARTAS, RETRASO_CARTA * 10)
                    // Late un poco cada vez que suelta una carta.
                    val latido = if (emision > 0f && emision < 1f) 0.05f * abs(sin(emision * PI.toFloat() * 10)) else 0f
                    val escala = (0.4f + 0.6f * resorte(aparicion)) * (1f + latido)
                    scaleX = escala
                    scaleY = escala
                    alpha = (aparicion * 3f).coerceAtMost(1f)
                    // Tres sacudidas sobre su base que empiezan y terminan suaves.
                    transformOrigin = TransformOrigin(0.5f, 1f)
                    rotationZ = 14f * sin(2 * PI.toFloat() * 3 * sacudidas) * sin(PI.toFloat() * sacudidas)
                    val bajada = suave(avance(t, INICIO_BAJADA, DURACION_BAJADA))
                    val hundida = avance(t, INICIO_HUNDIDA, DURACION_HUNDIDA).pow(3)
                    translationY = (alto.toPx() / 2f - diametro * 0.1f) * bajada + diametro * hundida
                }
        )
    }
}

@Composable
private fun TarjetaPokemon(
    pokemon: PokemonResumen,
    orden: Int?,
    habilitada: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val forma = RoundedCornerShape(20.dp)
    val tono = colorTipo(pokemon.tipos.firstOrNull()?.clave ?: "")
    Column(
        modifier
            .scale(if (orden == null) 1f else 0.97f)
            .clip(forma)
            .background(Brush.verticalGradient(listOf(tono.copy(alpha = 0.22f), FondoTarjeta)))
            .border(BorderStroke(3.dp, if (orden == null) Color.Transparent else RojoPokebola), forma)
            .clickable(enabled = habilitada, onClick = onClick)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(Modifier.fillMaxWidth()) {
            ImagenRemota(
                url = pokemon.sprite,
                descripcion = pokemon.nombre,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(92.dp)
                    .padding(top = 6.dp)
            )
            if (orden != null) {
                Text(
                    "$orden",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(RojoPokebola)
                        .padding(top = 4.dp)
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(pokemon.nombre, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("#${pokemon.id}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextoSecundario)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            pokemon.tipos.forEach { ChipTipo(it, compacto = true) }
        }
        Row(Modifier.fillMaxWidth()) {
            Dato("PS", pokemon.stats.ps, Modifier.weight(1f))
            Dato("ATQ", maxOf(pokemon.stats.ataque, pokemon.stats.ataqueEspecial), Modifier.weight(1f))
            Dato("VEL", pokemon.stats.velocidad, Modifier.weight(1f))
        }
    }
}

@Composable
private fun Dato(titulo: String, valor: Int, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$valor", fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(titulo, fontSize = 9.sp, fontWeight = FontWeight.Black, color = TextoSecundario)
    }
}

@Composable
fun FinPantalla(ui: EstadoUi, vm: ArenaViewModel) {
    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(1f))
        Text(if (ui.gane) "🏆" else "🏁", fontSize = 64.sp)
        Spacer(Modifier.height(24.dp))
        Text(if (ui.gane) "¡Ganaste!" else "Perdiste", fontSize = 40.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(10.dp))
        Text(ui.motivoFin, fontSize = 17.sp, color = TextoSecundario, textAlign = TextAlign.Center)
        ui.estado?.let { estado ->
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                estado.yo.equipo.forEach { pokemon ->
                    Column(
                        Modifier
                            .width(80.dp)
                            .alpha(if (pokemon.debilitado) 0.45f else 1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        ImagenRemota(pokemon.spriteFrente, pokemon.nombre, Modifier.size(72.dp), pixelado = true)
                        Text(pokemon.nombre, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        BotonPrincipal(titulo = "Jugar otra vez", onClick = { vm.jugarOtraVez() })
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { vm.cancelar() }) {
            Text("Volver al inicio", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
