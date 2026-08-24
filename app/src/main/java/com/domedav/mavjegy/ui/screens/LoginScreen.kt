package com.domedav.mavjegy.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.domedav.mavjegy.data.MavApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val CookieShape = RoundedCornerShape(16.dp)
private val FieldShape = RoundedCornerShape(20.dp)
private val PillShape = RoundedCornerShape(24.dp)

@Composable
fun LoginScreen(api: MavApi, onLoggedIn: () -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var appeared by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) { appeared = true }

    val rootAlpha by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "rootAlpha"
    )
    val rootSlide by animateDpAsState(
        targetValue = if (appeared) 0.dp else 24.dp,
        animationSpec = tween(durationMillis = 180),
        label = "rootSlide"
    )

    val goForward: () -> Unit = {
        if (email.isNotBlank()) {
            focusManager.clearFocus()
            error = null
            step = 1
        }
    }

    val submitLogin: () -> Unit = submitLogin@{
        if (loading || email.isBlank() || password.isBlank()) return@submitLogin
        focusManager.clearFocus()
        loading = true
        error = null
        scope.launch {
            try {
                val result = api.login(email.trim(), password)
                if (result.isSuccess) {
                    onLoggedIn()
                } else {
                    error = "Sikertelen bejelentkezés: ${
                        result.exceptionOrNull()?.message ?: "ismeretlen hiba"
                    }"
                }
            } finally {
                loading = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        if (step == 1) {
            StaggeredAppear(index = 0) {
                Surface(
                    shape = CookieShape,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp)
                ) {
                    IconButton(onClick = {
                        focusManager.clearFocus()
                        error = null
                        step = 0
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Vissza"
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = rootAlpha
                    translationY = rootSlide.toPx()
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(1f))

            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    if (targetState > initialState) {
                        slideInHorizontally(
                            initialOffsetX = { it },
                            animationSpec = tween(durationMillis = 200)
                        ) + fadeIn(animationSpec = tween(durationMillis = 200)) togetherWith
                            slideOutHorizontally(
                                targetOffsetX = { -it / 3 },
                                animationSpec = tween(durationMillis = 200)
                            ) + fadeOut(animationSpec = tween(durationMillis = 200))
                    } else {
                        slideInHorizontally(
                            initialOffsetX = { -it },
                            animationSpec = tween(durationMillis = 200)
                        ) + fadeIn(animationSpec = tween(durationMillis = 200)) togetherWith
                            slideOutHorizontally(
                                targetOffsetX = { it / 3 },
                                animationSpec = tween(durationMillis = 200)
                            ) + fadeOut(animationSpec = tween(durationMillis = 200))
                    }
                },
                label = "stepContent"
            ) { currentStep ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when (currentStep) {
                        0 -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = CookieShape,
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Mail,
                                        contentDescription = null,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.size(12.dp))
                                Text(
                                    text = "Email",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            StaggeredAppear(index = 1) {
                                Text(
                                    text = "A jegy.mav.hu fiókod email címe",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(20.dp))
                            StaggeredAppear(index = 2) {
                                OutlinedTextField(
                                    value = email,
                                    onValueChange = { email = it },
                                    singleLine = true,
                                    enabled = !loading,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Email,
                                        imeAction = ImeAction.Next
                                    ),
                                    keyboardActions = KeyboardActions(onNext = { goForward() }),
                                    shape = FieldShape,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            StaggeredAppear(index = 3) {
                                Button(
                                    onClick = { goForward() },
                                    enabled = !loading && email.isNotBlank(),
                                    shape = FieldShape,
                                    colors = ButtonDefaults.buttonColors(),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp)
                                ) {
                                    Text(
                                        text = "Tovább",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        else -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = CookieShape,
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Lock,
                                        contentDescription = null,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.size(12.dp))
                                Text(
                                    text = "Jelszó",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            StaggeredAppear(index = 1) {
                                Text(
                                    text = "A fiókod jelszava",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(20.dp))
                            StaggeredAppear(index = 2) {
                                OutlinedTextField(
                                    value = password,
                                    onValueChange = { password = it },
                                    singleLine = true,
                                    enabled = !loading,
                                    visualTransformation = if (passwordVisible) {
                                        VisualTransformation.None
                                    } else {
                                        PasswordVisualTransformation()
                                    },
                                    trailingIcon = {
                                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                            Icon(
                                                imageVector = if (passwordVisible) {
                                                    Icons.Rounded.VisibilityOff
                                                } else {
                                                    Icons.Rounded.Visibility
                                                },
                                                contentDescription = if (passwordVisible) {
                                                    "Jelszó elrejtése"
                                                } else {
                                                    "Jelszó megjelenítése"
                                                }
                                            )
                                        }
                                    },
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Password,
                                        imeAction = ImeAction.Done
                                    ),
                                    keyboardActions = KeyboardActions(onDone = { submitLogin() }),
                                    shape = FieldShape,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            StaggeredAppear(index = 3) {
                                Button(
                                    onClick = { submitLogin() },
                                    enabled = !loading && email.isNotBlank() && password.isNotBlank(),
                                    shape = FieldShape,
                                    colors = ButtonDefaults.buttonColors(),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp)
                                ) {
                                    if (loading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            strokeWidth = 2.5.dp
                                        )
                                    } else {
                                        Text(
                                            text = "Bejelentkezés",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    AnimatedVisibility(
                        visible = error != null,
                        enter = slideInVertically(
                            initialOffsetY = { it / 2 },
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            )
                        ) + fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)),
                        exit = slideOutVertically(targetOffsetY = { it / 2 }) +
                            fadeOut(animationSpec = spring(stiffness = Spring.StiffnessHigh))
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            shape = PillShape,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ErrorOutline,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.size(10.dp))
                                Text(
                                    text = error.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    BackHandler(enabled = step == 1) {
        focusManager.clearFocus()
        error = null
        step = 0
    }
}

@Composable
private fun StaggeredAppear(index: Int, content: @Composable () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(index * 35L)
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(
            animationSpec = tween(durationMillis = 180)
        ) + slideInVertically(
            initialOffsetY = { it / 6 },
            animationSpec = tween(durationMillis = 180)
        )
    ) {
        content()
    }
}
