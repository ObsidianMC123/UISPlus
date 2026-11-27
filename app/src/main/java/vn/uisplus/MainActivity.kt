package vn.uisplus

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import vn.uisplus.ui.HomeScreen
import vn.uisplus.ui.LoginScreen
import vn.uisplus.ui.MainViewModel
import vn.uisplus.ui.theme.Ui
import vn.uisplus.ui.theme.UisTheme

class MainActivity : ComponentActivity() {
    companion object {
        /** Tab can mo khi bam vao thong bao (vd. 2 = Tap trung). */
        val pendingTab = MutableStateFlow<Int?>(null)
        /** Thu tu tab tren thanh duoi (1.3). */
        val TABS = listOf("today", "week", "bus", "focus", "me", "music", "notes")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent {
            val vm: MainViewModel = viewModel()
            val prefs by vm.prefs.collectAsStateWithLifecycle()
            UisTheme(prefs) {
                val loggedIn by vm.loggedIn.collectAsStateWithLifecycle()
                val data by vm.data.collectAsStateWithLifecycle()
                val showHome = loggedIn && data != null
                Box(Modifier.fillMaxSize().background(Ui.Bg)) {
                    AnimatedContent(
                        targetState = showHome,
                        transitionSpec = {
                            (fadeIn(tween(500)) + scaleIn(tween(500), initialScale = 1.06f))
                                .togetherWith(fadeOut(tween(300)) + scaleOut(tween(300), targetScale = 0.94f))
                        },
                        label = "root",
                    ) { home ->
                        if (home) HomeScreen(vm) else LoginScreen(vm)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(i: Intent?) {
        // 1.3: tab goi theo TEN ("today","week","bus","focus","me"); so cu (0..3 cua ban 1.2) van doi dung.
        val extras = i?.extras ?: return
        val t = when (val v = extras.get("tab")) {
            is String -> TABS.indexOf(v)
            is Int -> when (v) { 0 -> 0; 1 -> 1; 2 -> 3; 3 -> 4; else -> -1 }
            else -> -1
        }
        if (t >= 0) pendingTab.value = t
    }
}
