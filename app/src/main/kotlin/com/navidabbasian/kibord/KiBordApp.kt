package com.navidabbasian.kibord

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.navidabbasian.kibord.core.audio.LocalSoundManager
import com.navidabbasian.kibord.core.audio.MusicTrack
import com.navidabbasian.kibord.core.ui.theme.DorAccent
import com.navidabbasian.kibord.core.ui.theme.EsmFamilAccent
import com.navidabbasian.kibord.core.ui.theme.GandeGooAccent
import com.navidabbasian.kibord.core.ui.theme.KalamzAccent
import com.navidabbasian.kibord.core.ui.theme.LocalGameAccent
import com.navidabbasian.kibord.core.ui.theme.PantomimeClassicAccent
import com.navidabbasian.kibord.core.ui.theme.PantomimeRivalAccent
import com.navidabbasian.kibord.games.dor.DorGame
import com.navidabbasian.kibord.games.esmfamil.EsmFamilGame
import com.navidabbasian.kibord.games.gandegoo.GandeGooGame
import com.navidabbasian.kibord.games.kalamz.KalamzGame
import com.navidabbasian.kibord.games.pantomime.classic.ClassicPantomimeGame
import com.navidabbasian.kibord.games.pantomime.rival.RivalPantomimeGame
import com.navidabbasian.kibord.hub.HubShell
import com.navidabbasian.kibord.hub.Routes

/** ناوبری سطح بالا: هاب و مقصد هر بازی */
@Composable
fun KiBordApp() {
    val navController = rememberNavController()
    val sound = LocalSoundManager.current

    NavHost(
        navController = navController,
        startDestination = Routes.HUB
    ) {
        composable(Routes.HUB) {
            LaunchedEffect(Unit) { sound?.switchMusic(MusicTrack.HUB) }
            HubShell(onOpenGame = { route -> navController.navigate(route) })
        }
        composable(Routes.KALAMZ) {
            CompositionLocalProvider(LocalGameAccent provides KalamzAccent) {
                KalamzGame(onExitToHub = { navController.popBackStack(Routes.HUB, inclusive = false) })
            }
        }
        composable(Routes.DOR) {
            CompositionLocalProvider(LocalGameAccent provides DorAccent) {
                DorGame(onExitToHub = { navController.popBackStack(Routes.HUB, inclusive = false) })
            }
        }
        composable(Routes.GANDEGOO) {
            CompositionLocalProvider(LocalGameAccent provides GandeGooAccent) {
                GandeGooGame(onExitToHub = { navController.popBackStack(Routes.HUB, inclusive = false) })
            }
        }
        composable(Routes.PANTOMIME_CLASSIC) {
            CompositionLocalProvider(LocalGameAccent provides PantomimeClassicAccent) {
                ClassicPantomimeGame(onExitToHub = { navController.popBackStack(Routes.HUB, inclusive = false) })
            }
        }
        composable(Routes.PANTOMIME_RIVAL) {
            CompositionLocalProvider(LocalGameAccent provides PantomimeRivalAccent) {
                RivalPantomimeGame(onExitToHub = { navController.popBackStack(Routes.HUB, inclusive = false) })
            }
        }
        composable(Routes.ESM_FAMIL) {
            CompositionLocalProvider(LocalGameAccent provides EsmFamilAccent) {
                EsmFamilGame(onExitToHub = { navController.popBackStack(Routes.HUB, inclusive = false) })
            }
        }
    }
}
