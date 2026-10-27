package com.ibero.arenapokemon

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.IOException

val RojoPokebola = Color(0xFFE3350D)
val FondoAgrupado = Color(0xFFF2F2F7)
val FondoTarjeta = Color.White
val TextoSecundario = Color(0xFF8A8A8E)

// Colores clásicos de los 18 tipos.
fun colorTipo(clave: String): Color = Color(
    when (clave) {
        "normal" -> 0xFFA8A77A
        "fire" -> 0xFFEE8130
        "water" -> 0xFF6390F0
        "electric" -> 0xFFF7D02C
        "grass" -> 0xFF7AC74C
        "ice" -> 0xFF96D9D6
        "fighting" -> 0xFFC22E28
        "poison" -> 0xFFA33EA1
        "ground" -> 0xFFE2BF65
        "flying" -> 0xFFA98FF3
        "psychic" -> 0xFFF95587
        "bug" -> 0xFFA6B91A
        "rock" -> 0xFFB6A136
        "ghost" -> 0xFF735797
        "dragon" -> 0xFF6F35FC
        "dark" -> 0xFF705746
        "steel" -> 0xFFB7B7CE
        "fairy" -> 0xFFD685AD
        else -> 0xFF8E8E93
    }
)

// Texto oscuro sobre los tipos de color claro para que se lea bien.
fun textoSobreTipo(clave: String): Color =
    if (clave in setOf("electric", "ice", "ground", "steel", "normal", "bug", "rock")) Color(0xCC000000) else Color.White

@Composable
fun Pokebola(modifier: Modifier = Modifier) {
    Canvas(modifier.aspectRatio(1f)) {
        val radio = size.minDimension / 2
        val linea = size.minDimension * 0.07f
        val esquina = Offset(center.x - radio, center.y - radio)
        drawCircle(Color.White, radio)
        drawArc(RojoPokebola, 180f, 180f, useCenter = true, topLeft = esquina, size = Size(radio * 2, radio * 2))
        drawRect(Color.Black, topLeft = Offset(center.x - radio, center.y - linea / 2), size = Size(radio * 2, linea))
        drawCircle(Color.Black, radio - linea / 2, style = Stroke(linea))
        drawCircle(Color.White, radio * 0.34f)
        drawCircle(Color.Black, radio * 0.34f, style = Stroke(linea))
    }
}

private val cacheImagenes = LruCache<String, ImageBitmap>(64)

// Descarga un sprite con OkHttp y lo guarda en memoria (Compose no trae cargador de imágenes por URL).
@Composable
fun ImagenRemota(url: String, descripcion: String?, modifier: Modifier = Modifier, pixelado: Boolean = false) {
    var imagen by remember(url) { mutableStateOf(cacheImagenes.get(url)) }
    var fallo by remember(url) { mutableStateOf(false) }

    LaunchedEffect(url) {
        if (imagen != null) return@LaunchedEffect
        imagen = withContext(Dispatchers.IO) {
            try {
                Red.cliente.newCall(Request.Builder().url(url).build()).execute().use { respuesta ->
                    if (!respuesta.isSuccessful) return@use null
                    val bytes = respuesta.body.bytes()
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                }
            } catch (error: IOException) {
                null
            } catch (error: IllegalArgumentException) {
                null
            }
        }?.also { cacheImagenes.put(url, it) }
        fallo = imagen == null
    }

    Box(modifier, contentAlignment = Alignment.Center) {
        val actual = imagen
        when {
            actual != null -> Image(
                bitmap = actual,
                contentDescription = descripcion,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                filterQuality = if (pixelado) FilterQuality.None else FilterQuality.Medium
            )
            fallo -> Text("?", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextoSecundario)
            else -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = TextoSecundario)
        }
    }
}

@Composable
fun ChipTipo(tipo: Tipo, compacto: Boolean = false) {
    Text(
        text = tipo.nombre.uppercase(),
        color = textoSobreTipo(tipo.clave),
        fontSize = if (compacto) 9.sp else 10.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = 0.6.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(colorTipo(tipo.clave))
            .padding(horizontal = if (compacto) 6.dp else 8.dp, vertical = 3.dp)
    )
}

@Composable
fun BarraPs(ps: Int, psMax: Int, modifier: Modifier = Modifier) {
    val fraccion = if (psMax > 0) ps.toFloat() / psMax else 0f
    val animada by animateFloatAsState(fraccion, tween(600), label = "ps")
    val color = when {
        fraccion > 0.5f -> Color(0xFF34C759)
        fraccion > 0.2f -> Color(0xFFFFCC00)
        else -> Color(0xFFFF3B30)
    }
    Row(
        modifier.semantics { contentDescription = "Puntos de salud: $ps de $psMax" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("PS", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color(0xFFFF9500))
        Box(
            Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(50))
                .background(Color(0x1F000000))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(animada.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(50))
                    .background(color)
            )
        }
    }
}

@Composable
fun BotonPrincipal(
    titulo: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true,
    cargando: Boolean = false
) {
    Button(
        onClick = onClick,
        enabled = habilitado,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = RojoPokebola,
            disabledContainerColor = RojoPokebola.copy(alpha = 0.45f),
            disabledContentColor = Color.White
        )
    ) {
        if (cargando) {
            CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
            Box(Modifier.size(10.dp))
        }
        Text(titulo, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}
