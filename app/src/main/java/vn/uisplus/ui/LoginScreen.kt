package vn.uisplus.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import vn.uisplus.ui.theme.Ui
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun LoginScreen(vm: MainViewModel) {
    val busy by vm.loginBusy.collectAsStateWithLifecycle()
    val error by vm.loginError.collectAsStateWithLifecycle()
    var user by rememberSaveable { mutableStateOf("") }
    var pass by rememberSaveable { mutableStateOf("") }
    var savePass by rememberSaveable { mutableStateOf(true) }
    var show by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current

    // Rung the khi loi
    val shake = remember { Animatable(0f) }
    LaunchedEffect(error) {
        if (error != null) {
            for (x in listOf(18f, -14f, 10f, -6f, 3f, 0f)) shake.animateTo(x, tween(55))
        }
    }
    // Intro
    val intro = remember { Animatable(0f) }
    LaunchedEffect(Unit) { intro.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 120f)) }

    Box(Modifier.fillMaxSize().background(Ui.Bg)) {
        AuroraBackground()
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(56.dp))
            Logo(Modifier.graphicsLayer {
                val v = intro.value
                scaleX = 0.5f + 0.5f * v; scaleY = 0.5f + 0.5f * v
                rotationZ = (1f - v) * -40f
                alpha = v.coerceIn(0f, 1f)
            })
            Spacer(Modifier.height(22.dp))
            Text(
                "UIS+",
                style = MaterialTheme.typography.headlineLarge.copy(fontSize = 40.sp, letterSpacing = (-1).sp),
                color = Ui.Ink,
                modifier = Modifier.graphicsLayer { alpha = intro.value.coerceIn(0f, 1f); translationY = (1 - intro.value) * 40f },
            )
            Text(
                "Thời khóa biểu PTIT HCM — xịn hơn, nhanh hơn",
                style = MaterialTheme.typography.bodyMedium,
                color = Ui.Sub,
                modifier = Modifier.graphicsLayer { alpha = intro.value.coerceIn(0f, 1f) },
            )
            Spacer(Modifier.height(32.dp))

            Column(
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        translationX = shake.value * density
                        val v = intro.value
                        translationY = (1 - v) * 120f
                        alpha = v.coerceIn(0f, 1f)
                    }
                    .elev(24.dp, RoundedCornerShape(24.dp), ambientColor = Ui.Brand.copy(alpha = 0.25f), spotColor = Ui.Brand.copy(alpha = 0.25f))
                    .clip(RoundedCornerShape(24.dp))
                    .background(Ui.Card.copy(alpha = 0.92f))
                    .border(1.dp, Ui.Card, RoundedCornerShape(24.dp))
                    .padding(22.dp),
            ) {
                Text("Đăng nhập", style = MaterialTheme.typography.titleLarge, color = Ui.Ink)
                Text("Dùng tài khoản UIS của trường", style = MaterialTheme.typography.bodySmall, color = Ui.Sub)
                Spacer(Modifier.height(18.dp))
                val fieldColors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Ui.Brand,
                    unfocusedBorderColor = Ui.Line,
                    focusedLabelColor = Ui.Brand,
                    cursorColor = Ui.Brand,
                    focusedContainerColor = Ui.Card,
                    unfocusedContainerColor = Ui.Soft,
                )
                OutlinedTextField(
                    value = user,
                    onValueChange = { user = it.trim() },
                    label = { Text("Mã sinh viên") },
                    placeholder = { Text("VD: N26DCCN001") },
                    singleLine = true,
                    enabled = !busy,
                    shape = RoundedCornerShape(16.dp),
                    colors = fieldColors,
                    leadingIcon = { Icon(Ic.Person, null, tint = Ui.Faint) },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Next,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = pass,
                    onValueChange = { pass = it },
                    label = { Text("Mật khẩu") },
                    singleLine = true,
                    enabled = !busy,
                    shape = RoundedCornerShape(16.dp),
                    colors = fieldColors,
                    leadingIcon = { Icon(Ic.Sparkle, null, tint = Ui.Faint) },
                    trailingIcon = {
                        IconButton(onClick = { show = !show }) {
                            AnimatedContent(show, label = "eye", transitionSpec = { fadeIn() togetherWith fadeOut() }) {
                                Icon(if (it) Ic.EyeOff else Ic.Eye, "Hiện mật khẩu", tint = Ui.Sub)
                            }
                        }
                    },
                    visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focus.clearFocus(); vm.login(user, pass, savePass) }),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Lưu mật khẩu", style = MaterialTheme.typography.titleSmall, color = Ui.Ink)
                        Text(
                            "Mã hóa bằng Android Keystore, chỉ nằm trên máy này. Giúp tự đăng nhập lại khi phiên hết hạn.",
                            style = MaterialTheme.typography.bodySmall, color = Ui.Sub,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = savePass, onCheckedChange = { savePass = it }, enabled = !busy,
                        colors = SwitchDefaults.colors(checkedTrackColor = Ui.Brand),
                    )
                }
                AnimatedVisibility(
                    visible = error != null,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Text(
                        error ?: "",
                        color = Ui.Brand,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Ui.Brand.copy(alpha = 0.08f))
                            .padding(10.dp),
                    )
                }
                Spacer(Modifier.height(18.dp))
                GradientButton(
                    text = if (busy) "Đang tải lịch của bạn…" else "Đăng nhập",
                    busy = busy,
                    onClick = { focus.clearFocus(); vm.login(user, pass, savePass) },
                )
            }
            Spacer(Modifier.height(24.dp))
            Text(
                "App không chính thức · chỉ đọc dữ liệu, không đăng ký môn hay đóng tiền",
                style = MaterialTheme.typography.bodySmall.copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center),
                color = Ui.Faint,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Logo(modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "logo")
    val ang by t.animateFloat(0f, 360f, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "ang")
    val float by t.animateFloat(0f, (2 * Math.PI).toFloat(), infiniteRepeatable(tween(3200, easing = LinearEasing)), label = "f")
    Box(modifier.size(112.dp).graphicsLayer { translationY = sin(float) * 10f }, contentAlignment = Alignment.Center) {
        // Vong orbit
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2
            val rad = Math.toRadians(ang.toDouble())
            for (i in 0 until 3) {
                val a = rad + i * 2.094
                val c = listOf(Ui.Brand, Ui.Brand2, Ui.Violet)[i]
                drawCircle(c, radius = 5.dp.toPx(), center = Offset(center.x + (r - 6.dp.toPx()) * cos(a).toFloat(), center.y + (r - 6.dp.toPx()) * sin(a).toFloat()))
            }
        }
        Box(
            Modifier
                .size(78.dp)
                .elev(20.dp, RoundedCornerShape(16.dp), spotColor = Ui.Brand)
                .clip(RoundedCornerShape(16.dp))
                .background(Ui.BrandGradient)
                .shimmer(alpha = 0.35f),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Ic.Calendar, null, tint = Color.White, modifier = Modifier.size(40.dp))
        }
    }
}

@Composable
fun GradientButton(text: String, busy: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .bounceClick(enabled = !busy, onClick = onClick)
            .elev(14.dp, RoundedCornerShape(16.dp), spotColor = Ui.Brand)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.horizontalGradient(listOf(Ui.Brand, Ui.Brand2)))
            .then(if (busy) Modifier.shimmer(alpha = 0.4f) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            AnimatedVisibility(busy) {
                Row {
                    CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.5.dp)
                    Spacer(Modifier.width(10.dp))
                }
            }
            AnimatedContent(text, label = "btn", transitionSpec = {
                (fadeIn(tween(220)) + androidx.compose.animation.slideInVertically { it / 2 })
                    .togetherWith(fadeOut(tween(150)) + androidx.compose.animation.slideOutVertically { -it / 2 })
            }) {
                Text(it, color = Color.White, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
