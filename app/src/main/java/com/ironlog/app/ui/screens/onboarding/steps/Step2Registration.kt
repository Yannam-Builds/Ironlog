package com.ironlog.app.ui.screens.onboarding.steps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.ironlog.app.ui.screens.onboarding.OnboardingPageHeader
import com.ironlog.app.ui.screens.onboarding.OnboardingConfig
import com.ironlog.app.ui.screens.onboarding.SetupReward

@Composable
fun Step2Registration(
    userName: String,
    onUserNameChange: (String) -> Unit,
    onNext: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OnboardingConfig.bgDark)
            .imePadding()
            .padding(horizontal = 24.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            OnboardingPageHeader(
                step = "Identity",
                title = "What should your ledger call you?",
                body = "This stays on your device and appears on your training summaries. A nickname works perfectly.",
            )

            Spacer(Modifier.height(36.dp))

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
                    .fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
            )
            Spacer(Modifier.height(14.dp))
            Text("You can change this later in Settings.", color = OnboardingConfig.textFaint, fontSize = 12.sp)
        }

        Column {
            SetupReward(
                text = "Profile setup unlocks your personal Iron Ledger",
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(14.dp))
            GlowButton(
                text    = if (userName.isBlank()) "Enter a name to continue" else "Continue as ${userName.trim()}",
                onClick = onNext,
                enabled = userName.isNotBlank(),
            )
        }
    }
}
