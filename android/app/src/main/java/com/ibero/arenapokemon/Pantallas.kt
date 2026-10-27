package com.ibero.arenapokemon

import android.content.Context
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
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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

    fun buscar() {
        foco.clearFocus()
        val limpio = nombre.trim()
        if (limpio.isEmpty()) return
        preferencias.edit().putString("nombre", limpio).putString("servidor", servidor.trim()).apply()
        vm.conectar(limpio, servidor)
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

        BotonPrincipal(titulo = "⚡ Buscar rival", onClick = { buscar() }, habilitado = nombre.isNotBlank())
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
fun BuscandoPantalla(vm: ArenaViewModel) {
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
        Text("Buscando rival…", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "La partida empieza cuando otro entrenador se conecte.",
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

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
    ) {
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
                    Text(
                        buildAnnotatedString {
                            append("Tu rival es ")
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(ui.rival) }
                            append(". Toca 3 Pokémon.")
                        },
                        fontSize = 16.sp,
                        color = TextoSecundario
                    )
                }
            }
            items(ui.opciones, key = { it.id }) { pokemon ->
                val orden = seleccion.indexOf(pokemon.id).takeIf { it >= 0 }?.plus(1)
                TarjetaPokemon(pokemon = pokemon, orden = orden, habilitada = !esperando) {
                    if (orden != null) {
                        seleccion.remove(pokemon.id)
                    } else if (seleccion.size < TAMANO_EQUIPO) {
                        seleccion.add(pokemon.id)
                    }
                }
            }
        }

        Column(
            Modifier
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
}

@Composable
private fun TarjetaPokemon(pokemon: PokemonResumen, orden: Int?, habilitada: Boolean, onClick: () -> Unit) {
    val forma = RoundedCornerShape(20.dp)
    val tono = colorTipo(pokemon.tipos.firstOrNull()?.clave ?: "")
    Column(
        Modifier
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
