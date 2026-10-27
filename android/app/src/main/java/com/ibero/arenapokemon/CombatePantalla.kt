package com.ibero.arenapokemon

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Cielo = Color(0xFF9ED6FA)
private val Pasto = Color(0xFFC7EDAB)
private val Plataforma = Color(0x8C73AB59)
private val CuadroTexto = Color(0xFF293347)

@Composable
fun CombatePantalla(ui: EstadoUi, vm: ArenaViewModel) {
    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Turno ${ui.estado?.turno ?: 1}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextoSecundario)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { vm.cancelar() }) {
                Text("Abandonar", color = Color(0xFFFF3B30), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Campo(ui)
        Narracion(ui)
        Ataques(ui, vm)
        Equipo(ui, vm)
    }
}

// Campo de batalla: el rival arriba a la derecha y tu Pokémon de espaldas abajo a la izquierda.
@Composable
private fun Campo(ui: EstadoUi) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(330.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.verticalGradient(listOf(Cielo, Pasto)))
            .padding(14.dp)
    ) {
        ui.pokemonRival?.let { rival ->
            TarjetaEstado(
                pokemon = rival,
                pie = { Vivos(ui.estado?.rival?.vivos, ui.estado?.rival?.total) },
                modifier = Modifier.align(Alignment.TopStart)
            )
            SpriteEnCampo(rival.spriteFrente, rival.debilitado, ui.golpesRival, Modifier.align(Alignment.TopEnd))
        }
        ui.miPokemon?.let { mio ->
            SpriteEnCampo(mio.spriteEspalda, mio.debilitado, ui.golpesMios, Modifier.align(Alignment.BottomStart))
            TarjetaEstado(
                pokemon = mio,
                pie = {
                    Text(ui.miNombre, fontSize = 11.sp, color = TextoSecundario, maxLines = 1)
                    Spacer(Modifier.weight(1f))
                    Text("${mio.ps}/${mio.psMax}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                },
                modifier = Modifier.align(Alignment.BottomEnd)
            )
        }
    }
}

@Composable
private fun Vivos(vivos: Int?, total: Int?) {
    if (vivos == null || total == null) return
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(total) { i ->
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (i < vivos) RojoPokebola else Color(0x59999999))
            )
        }
    }
}

@Composable
private fun TarjetaEstado(
    pokemon: PokemonCombate,
    pie: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .width(168.dp)
            .shadow(6.dp, RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.94f), RoundedCornerShape(14.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                pokemon.nombre,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text("Nv50", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextoSecundario)
        }
        BarraPs(pokemon.ps, pokemon.psMax)
        Row(verticalAlignment = Alignment.CenterVertically, content = pie)
    }
}

@Composable
private fun SpriteEnCampo(url: String, debilitado: Boolean, golpes: Int, modifier: Modifier) {
    val sacudida = remember { Animatable(0f) }
    LaunchedEffect(golpes) {
        if (golpes == 0) return@LaunchedEffect
        for (destino in listOf(-10f, 10f, -6f, 0f)) {
            sacudida.animateTo(destino, tween(70))
        }
    }
    val opacidad by animateFloatAsState(if (debilitado) 0f else 1f, tween(500), label = "opacidad")
    val caida by animateFloatAsState(if (debilitado) 30f else 0f, tween(500), label = "caida")

    Box(modifier.width(130.dp), contentAlignment = Alignment.BottomCenter) {
        Box(
            Modifier
                .size(width = 130.dp, height = 30.dp)
                .clip(RoundedCornerShape(50))
                .background(Plataforma)
        )
        ImagenRemota(
            url = url,
            descripcion = null,
            pixelado = true,
            modifier = Modifier
                .padding(bottom = 8.dp)
                .size(128.dp)
                .offset { IntOffset(sacudida.value.dp.roundToPx(), caida.dp.roundToPx()) }
                .alpha(opacidad)
        )
    }
}

@Composable
private fun Narracion(ui: EstadoUi) {
    val texto = when {
        ui.narrando -> ui.narracion
        ui.accionEnviada -> "Esperando a ${ui.rival}…"
        else -> "¿Qué hará ${ui.miPokemon?.nombre ?: "tu Pokémon"}?"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .background(CuadroTexto, RoundedCornerShape(18.dp))
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AnimatedContent(
            targetState = texto,
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
            label = "narracion",
            modifier = Modifier.weight(1f)
        ) { actual ->
            Text(actual, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
        if (ui.accionEnviada && !ui.narrando) {
            CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
        }
    }
}

@Composable
private fun Ataques(ui: EstadoUi, vm: ArenaViewModel) {
    val movimientos = ui.miPokemon?.movimientos.orEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        movimientos.withIndex().chunked(2).forEach { fila ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                fila.forEach { (indice, movimiento) ->
                    BotonAtaque(
                        movimiento = movimiento,
                        habilitado = ui.puedeActuar,
                        onClick = { vm.atacar(indice) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (fila.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun BotonAtaque(movimiento: Movimiento, habilitado: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val texto = textoSobreTipo(movimiento.tipo.clave)
    Column(
        modifier
            .alpha(if (habilitado) 1f else 0.45f)
            .clip(RoundedCornerShape(16.dp))
            .background(colorTipo(movimiento.tipo.clave))
            .clickable(enabled = habilitado, onClick = onClick)
            .heightIn(min = 60.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)
    ) {
        Text(movimiento.nombre, color = texto, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            "${movimiento.tipo.nombre} · Poder ${movimiento.poder}",
            color = texto.copy(alpha = 0.85f),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun Equipo(ui: EstadoUi, vm: ArenaViewModel) {
    val estado = ui.estado ?: return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Cambiar Pokémon", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextoSecundario)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            estado.yo.equipo.forEachIndexed { indice, pokemon ->
                val activo = indice == estado.yo.activo
                val habilitado = ui.puedeActuar && !activo && !pokemon.debilitado
                Column(
                    Modifier
                        .weight(1f)
                        .alpha(if (pokemon.debilitado) 0.45f else 1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(FondoTarjeta)
                        .border(2.dp, if (activo) RojoPokebola else Color.Transparent, RoundedCornerShape(14.dp))
                        .clickable(enabled = habilitado) { vm.cambiar(indice) }
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ImagenRemota(
                        url = pokemon.spriteFrente,
                        descripcion = pokemon.nombre,
                        pixelado = true,
                        modifier = Modifier
                            .size(52.dp)
                            .then(if (pokemon.debilitado) Modifier.alpha(0.6f) else Modifier)
                    )
                    Text(pokemon.nombre, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    BarraPs(pokemon.ps, pokemon.psMax)
                }
            }
        }
    }
}
