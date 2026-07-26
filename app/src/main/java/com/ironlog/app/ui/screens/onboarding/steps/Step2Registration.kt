package com.ironlog.app.ui.screens.onboarding.steps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironlog.app.R
import com.ironlog.app.ui.screens.onboarding.GlowButton
import com.ironlog.app.ui.screens.onboarding.OnboardingConfig

@Composable
fun Step2Registration(
    userName: String,
    onUserNameChange: (String) -> Unit,
    onNext: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OnboardingConfig.bgDark)
            .padding(horizontal = 32.dp),
    ) {
        Column(
            modifier            = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text          = stringResource(R.string.onb_reg_header),
                color         = OnboardingConfig.accentBlue,
                fontSize      = 22.sp,
                fontWeight    = FontWeight.Black,
                letterSpacing = 4.sp,
                textAlign     = TextAlign.Center,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text      = stringResource(R.string.onb_reg_subtext),
                color     = OnboardingConfig.textMuted,
                fontSize  = 14.sp,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(40.dp))

            OutlinedTextField(
                value         = userName,
                onValueChange = { if (it.length <= 30) onUserNameChange(it) },
                placeholder   = { Text(stringResource(R.string.onb_reg_hint), color = OnboardingConfig.textMuted) },
                singleLine    = true,
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = OnboardingConfig.accentBlue,
                    unfocusedBorderColor = OnboardingConfig.accentBlue.copy(alpha = 0.3f),
                    cursorColor          = OnboardingConfig.accentBlue,
                    focusedTextColor     = Color.White,
                    unfocusedTextColor   = Color.White,
                ),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction      = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { if (userName.isNotBlank()) onNext() }),
                modifier        = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )

            Spacer(Modifier.height(40.dp))

            GlowButton(
                text    = stringResource(R.string.onb_reg_cta),
                onClick = onNext,
                enabled = userName.isNotBlank(),
            )
        }
    }
}
