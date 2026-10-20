package app.awavenue.c001apk.ui.login

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.awavenue.c001apk.network.Api
import app.awavenue.c001apk.network.ApiFailure
import app.awavenue.c001apk.ui.LocalApp
import app.awavenue.c001apk.ui.common.toast
import app.awavenue.c001apk.ui.nav.LocalNavigator
import app.awavenue.c001apk.ui.nav.Route
import app.awavenue.c001apk.ui.nav.rememberPageScrollState
import io.github.suqi8.coui.kmp.basic.Icon
import app.awavenue.c001apk.ui.common.TopBarIconButton
import io.github.suqi8.coui.kmp.basic.IconButton
import io.github.suqi8.coui.kmp.basic.LoadingButton
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.SmallTopAppBar
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.basic.TextField
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.ChevronBackward
import io.github.suqi8.coui.kmp.icon.extended.Hide
import io.github.suqi8.coui.kmp.icon.extended.Show
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** The messages the server sends when the picture captcha is wrong or missing, alone or joined. */
private val CAPTCHA_MESSAGES = setOf("图形验证码不能为空", "图形验证码错误", "图形验证码不能为空\", \"图形验证码错误")

@Composable
fun LoginScreen(onDone: () -> Unit) {
    val app = LocalApp.current
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()

    var account by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var captcha by rememberSaveable { mutableStateOf("") }
    var requestHash by remember { mutableStateOf<String?>(null) }
    var captchaImage by remember { mutableStateOf<ByteArray?>(null) }
    var showCaptcha by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    // A login made elsewhere — the web login on top of this page — switches the session. Comparing
    // against the session this page opened on keeps 添加账号 from closing as soon as it opens.
    val openedOn = rememberSaveable { app.session.generation }
    var done by remember { mutableStateOf(false) }

    val accountFocus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }
    val captchaFocus = remember { FocusRequester() }
    val bitmap = remember(captchaImage) {
        captchaImage?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
    }

    suspend fun fetchCaptcha() {
        try {
            captchaImage = app.repository.loginCaptchaImage()
            showCaptcha = true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            context.toast("无法获取验证码：${ApiFailure.from(e).message}")
        }
    }

    fun submit() {
        val hash = requestHash ?: return
        if (account.isEmpty() || password.isEmpty()) {
            context.toast("账号或密码为空")
            return
        }
        scope.launch {
            submitting = true
            try {
                val outcome = app.repository.login(hash, account, password, captcha)
                if (outcome.success) {
                    done = true
                    context.toast("登录成功")
                    onDone()
                } else {
                    if (outcome.message.isNotEmpty()) context.toast(outcome.message)
                    if (outcome.message in CAPTCHA_MESSAGES || (showCaptcha && outcome.message == "密码错误")) {
                        fetchCaptcha()
                    } else if ("人机" in outcome.message) {
                        navigator.push(Route.Web(Api.LOGIN_PAGE, isLogin = true))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                context.toast("登录失败：${ApiFailure.from(e).message}")
            } finally {
                submitting = false
            }
        }
    }

    LaunchedEffect(Unit) {
        accountFocus.requestFocus()
    }

    LaunchedEffect(app.session.generation) {
        // The page stays composed while it animates out, so a login it finished itself is skipped.
        if (app.session.generation != openedOn && !done) {
            done = true
            context.toast("登录成功")
            onDone()
        }
    }

    LaunchedEffect(Unit) {
        try {
            requestHash = app.repository.loginParams().requestHash
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            context.toast("无法获取登录参数：${ApiFailure.from(e).message}")
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            SmallTopAppBar(
                title = "登录",
                gradientGlass = true,
                navigationIcon = {
                    TopBarIconButton(onClick = { navigator.pop() }) {
                        Icon(COUIIcons.ChevronBackward, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(
                        text = "网页登录",
                        onClick = { navigator.push(Route.Web(Api.LOGIN_PAGE, isLogin = true)) },
                    )
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberPageScrollState("login"))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "遇到人机验证或无法直接登录时，请使用右上角的“网页登录”，在酷安网页中完成验证与登录。",
                modifier = Modifier.padding(bottom = 6.dp),
                style = COUITheme.textStyles.footnote1,
                color = COUITheme.colorScheme.onSurfaceVariantSummary,
            )
            TextField(
                value = account,
                onValueChange = { account = it },
                modifier = Modifier.fillMaxWidth().focusRequester(accountFocus),
                label = "账号",
                singleLine = true,
                showClearButton = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { runCatching { passwordFocus.requestFocus() } }),
            )
            TextField(
                value = password,
                onValueChange = { password = it },
                modifier = Modifier.fillMaxWidth().focusRequester(passwordFocus),
                label = "密码",
                singleLine = true,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            imageVector = if (showPassword) COUIIcons.Hide else COUIIcons.Show,
                            contentDescription = if (showPassword) "隐藏密码" else "显示密码",
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = if (showCaptcha) ImeAction.Next else ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onNext = { runCatching { captchaFocus.requestFocus() } },
                    onDone = { submit() },
                ),
            )
            if (bitmap != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = "验证码",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { scope.launch { fetchCaptcha() } },
                    )
                    TextField(
                        value = captcha,
                        onValueChange = { input -> captcha = input.filter { it.isCaptchaChar() } },
                        modifier = Modifier.weight(1f).focusRequester(captchaFocus),
                        label = "验证码",
                        singleLine = true,
                        maxCount = 4,
                        showClearButton = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                    )
                }
            }
            LoadingButton(
                text = "登录",
                onClick = { submit() },
                modifier = Modifier.fillMaxWidth(),
                isLoading = submitting,
                enabled = requestHash != null,
            )
        }
    }
}

private fun Char.isCaptchaChar(): Boolean = this in '0'..'9' || this in 'a'..'z' || this in 'A'..'Z'
