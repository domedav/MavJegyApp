@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.domedav.mavjegy.ui.screens

import com.domedav.mavjegy.R

import com.domedav.mavjegy.ui.components.ExpressiveLoader

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
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.domedav.mavjegy.data.MavApi
import com.domedav.mavjegy.util.friendlyError
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
    // Regisztráció mezők
    var regLastName by rememberSaveable { mutableStateOf("") }
    var regFirstName by rememberSaveable { mutableStateOf("") }
    var regEmail by rememberSaveable { mutableStateOf("") }
    var regBirthDate by rememberSaveable { mutableStateOf("") }
    var regPassword by rememberSaveable { mutableStateOf("") }
    var regPasswordAgain by rememberSaveable { mutableStateOf("") }
    var showForgotDialog by remember { mutableStateOf(false) }
    var forgotEmail by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Int?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var appeared by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current

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
                    error = friendlyError(result.exceptionOrNull()?.message)
                }
            } finally {
                loading = false
            }
        }
    }

    val submitRegistration: () -> Unit = submitRegistration@{
        if (loading) return@submitRegistration
        if (regLastName.isBlank() || regFirstName.isBlank() || regEmail.isBlank() ||
            regBirthDate.isBlank() || regPassword.isBlank() || regPasswordAgain.isBlank()
        ) {
            error = R.string.err_fill_all
            return@submitRegistration
        }
        if (regPassword != regPasswordAgain) {
            error = R.string.err_pw_mismatch
            return@submitRegistration
        }
        // yyyy.MM.dd. -> yyyy-MM-dd
        val birthIso = try {
            val p = regBirthDate.trim().split(".")
            String.format("%04d-%02d-%02d", p[0].trim().toInt(), p[1].trim().toInt(), p[2].trim().toInt())
        } catch (_: Exception) {
            ""
        }
        if (birthIso.isBlank()) {
            error = R.string.err_invalid_birth
            return@submitRegistration
        }
        focusManager.clearFocus()
        loading = true
        error = null
        scope.launch {
            try {
                val result = api.register(regEmail.trim(), regLastName.trim(), regFirstName.trim(), birthIso, regPassword)
                result.fold(
                    onSuccess = { _ ->
                        info = context.getString(R.string.info_register_ok)
                        step = 0
                    },
                    onFailure = { e -> error = friendlyError(e.message) }
                )
            } finally {
                loading = false
            }
        }
    }

    val sendForgotPassword: () -> Unit = sendForgot@{
        if (loading || forgotEmail.isBlank()) return@sendForgot
        loading = true
        error = null
        scope.launch {
            try {
                val result = api.forgotPassword(forgotEmail.trim())
                result.fold(
                    onSuccess = { msg ->
                        showForgotDialog = false
                        info = msg ?: context.getString(R.string.fmt_forgot_sent, forgotEmail.trim())
                    },
                    onFailure = { e -> error = friendlyError(e.message) }
                )
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
                    val spec = tween<androidx.compose.ui.unit.IntSize>(durationMillis = 240)
                    if (targetState > initialState) {
                        (slideInHorizontally(
                            initialOffsetX = { it },
                            animationSpec = tween(durationMillis = 220)
                        ) + fadeIn(animationSpec = tween(durationMillis = 220)) +
                            scaleIn(initialScale = 0.92f, animationSpec = tween(durationMillis = 240))) togetherWith
                            slideOutHorizontally(
                                targetOffsetX = { -it / 3 },
                                animationSpec = tween(durationMillis = 220)
                            ) + fadeOut(animationSpec = tween(durationMillis = 160)) +
                            scaleOut(targetScale = 1.06f, animationSpec = tween(durationMillis = 240))
                    } else {
                        (slideInHorizontally(
                            initialOffsetX = { -it },
                            animationSpec = tween(durationMillis = 220)
                        ) + fadeIn(animationSpec = tween(durationMillis = 220)) +
                            scaleIn(initialScale = 1.04f, animationSpec = tween(durationMillis = 240))) togetherWith
                            slideOutHorizontally(
                                targetOffsetX = { it / 3 },
                                animationSpec = tween(durationMillis = 220)
                            ) + fadeOut(animationSpec = tween(durationMillis = 160)) +
                            scaleOut(targetScale = 0.94f, animationSpec = tween(durationMillis = 240))
                    }.using(androidx.compose.animation.SizeTransform(clip = false) { _, _ -> spec })
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
                                      text = stringResource(R.string.title_email),
                                     style = MaterialTheme.typography.titleLarge,
                                     fontWeight = FontWeight.Bold,
                                     color = MaterialTheme.colorScheme.onSurface
                                 )
                             }
                             Spacer(modifier = Modifier.height(6.dp))
                             StaggeredAppear(index = 1) {
                                 Text(
                                      text = stringResource(R.string.label_email),
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
                                        text = stringResource(R.string.btn_continue),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            StaggeredAppear(index = 4) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    androidx.compose.material3.TextButton(onClick = { step = 2 }) {
                                        Text(stringResource(R.string.account_create))
                                    }
                                    androidx.compose.material3.TextButton(onClick = {
                                        forgotEmail = email
                                        showForgotDialog = true
                                    }) {
                                        Text(stringResource(R.string.btn_forgot_pw))
                                    }
                                }
                            }
                        }

                        1 -> {
                            // A megadott email cím megjelenik a jelszó-lépés tetején
                            Surface(
                                shape = PillShape,
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ) {
                                Text(
                                    text = email.trim(),
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            // Vissza gomb középen, könnyen elérhető helyen – mint a regisztrációnál
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = CookieShape,
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.clickable {
                                        focusManager.clearFocus()
                                        error = null
                                        step = 0
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                        contentDescription = stringResource(R.string.cd_back_to_login),
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                                 Spacer(modifier = Modifier.size(12.dp))
                                 Text(
                                      text = stringResource(R.string.title_password),
                                      style = MaterialTheme.typography.titleLarge,
                                     fontWeight = FontWeight.Bold,
                                     color = MaterialTheme.colorScheme.onSurface
                                 )
                             }
                             Spacer(modifier = Modifier.height(6.dp))
                             StaggeredAppear(index = 1) {
                                 Text(
                                      text = stringResource(R.string.label_password),
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
                                                    stringResource(R.string.cd_pw_hide)
                                                } else {
                                                    stringResource(R.string.cd_pw_show)
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
                                        ExpressiveLoader(
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            size = 20.dp,
                                            strokeWidth = 2.5.dp
                                        )
                                    } else {
                                        Text(
                                            text = stringResource(R.string.btn_login),
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        2 -> {
                            // Regisztráció
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = CookieShape,
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.clickable {
                                        focusManager.clearFocus()
                                        error = null
                                        step = 0
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                        contentDescription = stringResource(R.string.cd_back_to_login),
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.size(12.dp))
                                Text(
                                    text = stringResource(R.string.account_create),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(
                                    value = regLastName,
                                    onValueChange = { regLastName = it },
                                    label = { Text(stringResource(R.string.label_lastname)) },
                                    singleLine = true,
                                    enabled = !loading,
                                    shape = FieldShape,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = regFirstName,
                                    onValueChange = { regFirstName = it },
                                    label = { Text(stringResource(R.string.label_firstname)) },
                                    singleLine = true,
                                    enabled = !loading,
                                    shape = FieldShape,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = regEmail,
                                onValueChange = { regEmail = it },
                                label = { Text(stringResource(R.string.label_email_addr)) },
                                singleLine = true,
                                enabled = !loading,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Email,
                                    imeAction = ImeAction.Next
                                ),
                                shape = FieldShape,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            // Dátum: selector, nem input box
                            var showRegDatePicker by remember { mutableStateOf(false) }
                            if (showRegDatePicker) {
                                val regDateState = androidx.compose.material3.rememberDatePickerState()
                                androidx.compose.material3.DatePickerDialog(
                                    onDismissRequest = { showRegDatePicker = false },
                                    confirmButton = {
                                        androidx.compose.material3.TextButton(onClick = {
                                            regDateState.selectedDateMillis?.let { ms ->
                                                regBirthDate = java.time.Instant.ofEpochMilli(ms)
                                                    .atZone(java.time.ZoneOffset.UTC).toLocalDate()
                                                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy.MM.dd."))
                                            }
                                            showRegDatePicker = false
                                            }) { Text(stringResource(R.string.btn_ok)) }
                                    },
                                    dismissButton = {
                                        androidx.compose.material3.TextButton(onClick = { showRegDatePicker = false }) {
                                            Text(stringResource(R.string.btn_cancel))
                                        }
                                    }
                                ) {
                                    androidx.compose.material3.DatePicker(state = regDateState)
                                }
                            }
                            Box {
                                OutlinedTextField(
                                    value = regBirthDate,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text(stringResource(R.string.birth_date)) },
                                    placeholder = { Text(stringResource(R.string.hint_pick_date)) },
                                    singleLine = true,
                                    enabled = !loading,
                                    shape = FieldShape,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                // Átfedő kattintási réteg: ez nyitja a date pickert
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .clickable(enabled = !loading) { showRegDatePicker = true }
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = regPassword,
                                onValueChange = { regPassword = it },
                                label = { Text(stringResource(R.string.title_password)) },
                                singleLine = true,
                                enabled = !loading,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Next
                                ),
                                shape = FieldShape,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = regPasswordAgain,
                                onValueChange = { regPasswordAgain = it },
                                label = { Text(stringResource(R.string.label_password_repeat)) },
                                singleLine = true,
                                enabled = !loading,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(onDone = { submitRegistration() }),
                                shape = FieldShape,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { submitRegistration() },
                                enabled = !loading,
                                shape = FieldShape,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                            ) {
                                if (loading) {
                                    ExpressiveLoader(
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        size = 20.dp,
                                        strokeWidth = 2.5.dp
                                    )
                                } else {
                                    Text(
                                        text = stringResource(R.string.btn_register),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Sikeres művelet (regisztráció / jelszó-kérés) – nem error színű
                    val snackbar = com.domedav.mavjegy.ui.components.LocalSnackbar.current
                    LaunchedEffect(info) {
                        info?.let {
                            snackbar.show(it, isError = false)
                            info = null
                        }
                    }

                    // Hiba snackbar – error színű
                    LaunchedEffect(error) {
                        error?.let {
                            snackbar.show(context.getString(it), isError = true)
                            error = null
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    BackHandler(enabled = step > 0) {
        focusManager.clearFocus()
        error = null
        step = 0
    }

    // Elfelejtett jelszó dialógus
    if (showForgotDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showForgotDialog = false },
            title = { Text(stringResource(R.string.title_forgot_pw)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(R.string.forgot_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = forgotEmail,
                        onValueChange = { forgotEmail = it },
                        label = { Text(stringResource(R.string.label_email_addr)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { sendForgotPassword() }) {
                    Text(stringResource(R.string.btn_send))
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showForgotDialog = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
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
