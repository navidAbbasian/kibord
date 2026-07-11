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
import com.navidabbasian.kibord.core.ui.theme.ForeheadAccent
import com.navidabbasian.kibord.core.ui.theme.MafiaAccent
import com.navidabbasian.kibord.core.ui.theme.NofooziAccent
import com.navidabbasian.kibord.core.ui.theme.ProverbAccent
import com.navidabbasian.kibord.core.ui.theme.ShahDozdAccent
import com.navidabbasian.kibord.core.ui.theme.SpyAccent
import com.navidabbasian.kibord.core.ui.theme.TabooAccent
import com.navidabbasian.kibord.games.dor.DorGame
import com.navidabbasian.kibord.games.esmfamil.EsmFamilGame
import com.navidabbasian.kibord.games.gandegoo.GandeGooGame
import com.navidabbasian.kibord.games.kalamz.KalamzGame
import com.navidabbasian.kibord.games.pantomime.classic.ClassicPantomimeGame
import com.navidabbasian.kibord.games.pantomime.rival.RivalPantomimeGame
import com.navidabbasian.kibord.games.forehead.ForeheadGame
import com.navidabbasian.kibord.games.mafia.MafiaGame
import com.navidabbasian.kibord.games.nofoozi.NofooziGame
import com.navidabbasian.kibord.games.proverb.ProverbGame
import com.navidabbasian.kibord.games.shahdozd.ShahDozdGame
import com.navidabbasian.kibord.games.spy.SpyGame
import com.navidabbasian.kibord.games.taboo.TabooGame
import com.navidabbasian.kibord.hub.HubShell
import com.navidabbasian.kibord.hub.MoreGamesScreen
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
        composable(Routes.MORE_GAMES) {
            LaunchedEffect(Unit) { sound?.switchMusic(MusicTrack.HUB) }
            MoreGamesScreen(onOpenGame = { route -> navController.navigate(route) })
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
        composable(Routes.TABOO) {
            CompositionLocalProvider(LocalGameAccent provides TabooAccent) {
                TabooGame(onExitToHub = { navController.popBackStack(Routes.HUB, inclusive = false) })
            }
        }
        composable(Routes.SPY) {
            CompositionLocalProvider(LocalGameAccent provides SpyAccent) {
                SpyGame(onExitToHub = { navController.popBackStack(Routes.HUB, inclusive = false) })
            }
        }
        composable(Routes.FOREHEAD) {
            CompositionLocalProvider(LocalGameAccent provides ForeheadAccent) {
                ForeheadGame(onExitToHub = { navController.popBackStack(Routes.HUB, inclusive = false) })
            }
        }
        composable(Routes.SHAH_DOZD) {
            CompositionLocalProvider(LocalGameAccent provides ShahDozdAccent) {
                ShahDozdGame(onExitToHub = { navController.popBackStack(Routes.HUB, inclusive = false) })
            }
        }
        composable(Routes.PROVERB) {
            CompositionLocalProvider(LocalGameAccent provides ProverbAccent) {
                ProverbGame(onExitToHub = { navController.popBackStack(Routes.HUB, inclusive = false) })
            }
        }
        composable(Routes.NOFOOZI) {
            CompositionLocalProvider(LocalGameAccent provides NofooziAccent) {
                NofooziGame(onExitToHub = { navController.popBackStack(Routes.HUB, inclusive = false) })
            }
        }
        composable(Routes.MAFIA) {
            CompositionLocalProvider(LocalGameAccent provides MafiaAccent) {
                MafiaGame(onExitToHub = { navController.popBackStack(Routes.HUB, inclusive = false) })
            }
        }
        composable(Routes.ESM_FAMIL) {
            CompositionLocalProvider(LocalGameAccent provides EsmFamilAccent) {
                EsmFamilGame(onExitToHub = { navController.popBackStack(Routes.HUB, inclusive = false) })
            }
        }
    }
}
