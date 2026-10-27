package com.ibero.arenapokemon

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = RojoPokebola)) {
                ArenaApp(viewModel())
            }
        }
    }
}

// Muestra la pantalla que corresponde a la fase de la partida.
@Composable
fun ArenaApp(vm: ArenaViewModel) {
    val ui by vm.ui.collectAsState()
    BackHandler(enabled = ui.fase != Fase.INICIO) { vm.cancelar() }

    // Elegir equipo y esperar al rival comparten pantalla para no perder la selección.
    val pantalla = if (ui.fase == Fase.ESPERANDO_RIVAL) Fase.ELIGIENDO else ui.fase
    Surface(modifier = Modifier.fillMaxSize(), color = FondoAgrupado) {
        Crossfade(targetState = pantalla, label = "pantalla") { fase ->
            when (fase) {
                Fase.INICIO -> InicioPantalla(ui, vm)
                Fase.BUSCANDO -> BuscandoPantalla(ui, vm)
                Fase.ELIGIENDO, Fase.ESPERANDO_RIVAL -> SeleccionPantalla(ui, vm)
                Fase.COMBATE -> CombatePantalla(ui, vm)
                Fase.FIN -> FinPantalla(ui, vm)
            }
        }
    }
}
