package com.navidabbasian.kibord

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
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
import com.navidabbasian.kibord.core.ui.theme.EsmRamzAccent
import com.navidabbasian.kibord.core.ui.theme.SpyAccent
import com.navidabbasian.kibord.core.ui.theme.WhoAmIAccent
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
import com.navidabbasian.kibord.games.esmramz.EsmRamzGame
import com.navidabbasian.kibord.games.spy.SpyGame
import com.navidabbasian.kibord.games.whoami.WhoAmIGame
import com.navidabbasian.kibord.games.taboo.TabooGame
import com.navidabbasian.kibord.hub.HubShell
import com.navidabbasian.kibord.hub.MoreGamesScreen
import com.navidabbasian.kibord.hub.TeamPickerScreen
import com.navidabbasian.kibord.hub.Routes
import com.navidabbasian.kibord.hub.OnboardingScreen
import com.navidabbasian.kibord.core.settings.GamePrefs
import com.navidabbasian.kibord.core.crash.CrashReporter
import com.navidabbasian.kibord.core.ui.components.BobbingEmoji
import com.navidabbasian.kibord.core.ui.components.KButton
import com.navidabbasian.kibord.core.ui.components.KButtonStyle
import com.navidabbasian.kibord.core.ui.components.TicketCard

/** ناوبری سطح بالا: هاب و مقصد هر بازی */
@Composable
fun KiBordApp() {
    val navController = rememberNavController()
    val sound = LocalSoundManager.current

    // خوش‌آمدگوییِ بارِ اول — تا وقتی رد/تمام نشده، جای همه‌چیز را می‌گیرد
    val context = LocalContext.current
    var showOnboarding by remember { mutableStateOf(!GamePrefs.getBool(context, "onboarding_done", false)) }
    if (showOnboarding) {
        OnboardingScreen(onDone = {
            GamePrefs.setBool(context, "onboarding_done", true)
            showOnboarding = false
        })
        return
    }

    // اگر اجرای قبل با کرش تمام شده، یک بار مودبانه پیشنهاد ارسال گزارش می‌دهیم
    var crashReport by remember { mutableStateOf(CrashReporter.pendingReport(context)) }
    crashReport?.let { report ->
        Dialog(onDismissRequest = {
            CrashReporter.clear(context)
            crashReport = null
        }) {
            TicketCard(modifier = Modifier.fillMaxWidth(), tilt = -1.5f) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    BobbingEmoji(emoji = "🤕", fontSize = 44.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "دفعه‌ی قبل اپ غیرمنتظره بسته شد!",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "اگه گزارش فنی‌ش رو بفرستی، سریع‌تر درستش می‌کنیم — هیچ اطلاعات شخصی‌ای توش نیست",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    KButton(text = "بفرست 📧", onClick = {
                        CrashReporter.sendByEmail(context, report)
                        CrashReporter.clear(context)
                        crashReport = null
                    })
                    Spacer(modifier = Modifier.height(8.dp))
                    KButton(text = "بی‌خیال", style = KButtonStyle.Glass, onClick = {
                        CrashReporter.clear(context)
                        crashReport = null
                    })
                }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = Routes.HUB
    ) {
        composable(Routes.HUB) {
            LaunchedEffect(Unit) { sound?.switchMusic(MusicTrack.HUB) }
            HubShell(onOpenGame = { route -> navController.navigate(route) })
        }
        composable(Routes.TEAM_PICKER) {
            LaunchedEffect(Unit) { sound?.switchMusic(MusicTrack.HUB) }
            TeamPickerScreen()
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
        composable(Routes.ESM_RAMZ) {
            CompositionLocalProvider(LocalGameAccent provides EsmRamzAccent) {
                EsmRamzGame(onExitToHub = { navController.popBackStack(Routes.HUB, inclusive = false) })
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
        composable(Routes.WHO_AM_I) {
            CompositionLocalProvider(LocalGameAccent provides WhoAmIAccent) {
                WhoAmIGame(onExitToHub = { navController.popBackStack(Routes.HUB, inclusive = false) })
            }
        }
        composable(Routes.ESM_FAMIL) {
            CompositionLocalProvider(LocalGameAccent provides EsmFamilAccent) {
                EsmFamilGame(onExitToHub = { navController.popBackStack(Routes.HUB, inclusive = false) })
            }
        }
    }
}
