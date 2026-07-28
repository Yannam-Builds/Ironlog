package com.ironlog.app.ui.screens.onboarding.steps

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironlog.app.R
import com.ironlog.app.domain.intelligence.CloudAiEngine
import com.ironlog.app.ui.screens.onboarding.GlowButton
import com.ironlog.app.ui.screens.onboarding.OnboardingConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Step6AiAbilities(
    cloudApiKey: String,
    cloudModelName: String,
    cloudProvider: String,
    onApiKeyChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onProviderChange: (provider: String, defaultModel: String) -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var keyVisible by remember { mutableStateOf(false) }
    var showProviderSheet by remember { mutableStateOf(false) }
    var modelExpanded by remember { mutableStateOf(false) }
    var showModelSheet by remember { mutableStateOf(false) }
    var loadingModels by remember { mutableStateOf(false) }
    var verifying by remember { mutableStateOf(false) }
    var remoteModels by remember { mutableStateOf<List<String>>(emptyList()) }
    var status by remember { mutableStateOf<String?>(null) }

    val provider = OnboardingConfig.providerFor(cloudProvider)
    val presetModels = OnboardingConfig.modelsFor(provider)
    val modelOptions = remember(provider, remoteModels) {
        (remoteModels + presetModels.map { it.modelId }).distinct()
    }
    val selectedModel = cloudModelName.ifBlank { OnboardingConfig.defaultModelFor(provider) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OnboardingConfig.bgDark)
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, top = 32.dp, end = 24.dp, bottom = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.onb_ai_header),
            color = OnboardingConfig.accentBlue,
            fontSize = 28.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Local coaching stays on by default. Add your own key only if you want cloud plan generation.",
            color = OnboardingConfig.textMuted,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(22.dp))
        Surface(
            color = OnboardingConfig.surfaceDark,
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, OnboardingConfig.cardBorder),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(18.dp)) {
                Text("Local coaching is active", color = OnboardingConfig.accentGold, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(8.dp))
                Text("Recovery scoring, workout suggestions, and effort tracking stay on-device.", color = OnboardingConfig.textMuted, fontSize = 13.sp, lineHeight = 19.sp)
            }
        }

        Spacer(Modifier.height(16.dp))
        Surface(
            color = OnboardingConfig.surfaceDark,
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.5.dp, OnboardingConfig.accentBlue.copy(alpha = 0.7f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(18.dp)) {
                Text("Cloud AI", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(4.dp))
                Text("Choose a provider and model you already have access to. Your key is stored securely on this device.", color = OnboardingConfig.textMuted, fontSize = 13.sp, lineHeight = 18.sp)

                Spacer(Modifier.height(18.dp))
                Text("Provider", color = OnboardingConfig.textMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showProviderSheet = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, OnboardingConfig.cardBorder),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(provider.displayName, color = Color.White, fontWeight = FontWeight.Bold)
                        Text("Change", color = OnboardingConfig.accentBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.height(14.dp))
                LabeledField(label = "Base URL", value = provider.baseUrl.ifBlank { "Set in Settings after onboarding" })

                if (provider == OnboardingConfig.AiProvider.GEMINI) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Get Gemini API key ->",
                        color = OnboardingConfig.accentBlue,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.clickable {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(OnboardingConfig.AI_STUDIO_URL)))
                        },
                    )
                }

                Spacer(Modifier.height(14.dp))
                Text("API key", color = OnboardingConfig.textMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = cloudApiKey,
                    onValueChange = onApiKeyChange,
                    placeholder = { Text("Paste your key here") },
                    visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { keyVisible = !keyVisible }) {
                            Icon(
                                imageVector = if (keyVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = null,
                                tint = OnboardingConfig.textMuted,
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    colors = onboardingTextFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                        onClick = {
                            remoteModels = presetModels.map { it.modelId }
                            showModelSheet = true
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(999.dp),
                        border = BorderStroke(1.dp, OnboardingConfig.cardBorder),
                    ) {
                        Text("Browse ${presetModels.size.coerceAtLeast(modelOptions.size)} Models")
                    }
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                loadingModels = true
                                status = null
                                val result = withContext(Dispatchers.IO) {
                                    CloudAiEngine.fetchModels(provider.baseUrl, cloudApiKey.trim(), provider.apiFormat)
                                }
                                loadingModels = false
                                result.onSuccess {
                                    remoteModels = it
                                    showModelSheet = true
                                    status = "Loaded ${it.size} models."
                                }.onFailure {
                                    status = it.message ?: "Could not load models."
                                }
                            }
                        },
                        enabled = cloudApiKey.isNotBlank() && provider.baseUrl.isNotBlank() && !loadingModels,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(999.dp),
                        border = BorderStroke(1.dp, OnboardingConfig.cardBorder),
                    ) {
                        if (loadingModels) CircularProgressIndicator(Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp)
                        else Text("Load Models")
                    }
                }

                Spacer(Modifier.height(14.dp))
                Text("Model", color = OnboardingConfig.textMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                ExposedDropdownMenuBox(expanded = modelExpanded, onExpandedChange = { modelExpanded = it }) {
                    OutlinedTextField(
                        value = selectedModel,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(modelExpanded) },
                        colors = onboardingTextFieldColors(),
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth(),
                    )
                    ExposedDropdownMenu(expanded = modelExpanded, onDismissRequest = { modelExpanded = false }) {
                        modelOptions.forEach { model ->
                            DropdownMenuItem(
                                text = { Text(model, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                onClick = {
                                    onModelChange(model)
                                    modelExpanded = false
                                },
                            )
                        }
                    }
                }

                status?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, color = if (it.startsWith("Loaded") || it.startsWith("Verified")) OnboardingConfig.grantedColor else OnboardingConfig.deniedColor, fontSize = 13.sp)
                }

                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                verifying = true
                                status = null
                                val result = withContext(Dispatchers.IO) {
                                    CloudAiEngine.verify(provider.baseUrl, cloudApiKey.trim(), selectedModel, provider.apiFormat)
                                }
                                verifying = false
                                status = result.fold(
                                    onSuccess = { "Verified. Cloud AI will be ready after onboarding." },
                                    onFailure = { it.message ?: "Verification failed." },
                                )
                            }
                        },
                        enabled = cloudApiKey.isNotBlank() && selectedModel.isNotBlank() && provider.baseUrl.isNotBlank() && !verifying,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(999.dp),
                    ) {
                        Text(if (verifying) "Checking..." else "Verify")
                    }
            }
        }

        Spacer(Modifier.height(24.dp))
        GlowButton(text = if (cloudApiKey.isBlank()) "Continue with local coaching" else "Save and continue", onClick = onNext)
        TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
            Text("Skip cloud setup", color = OnboardingConfig.textMuted, fontSize = 13.sp)
        }
        Spacer(Modifier.height(24.dp))
    }

    if (showModelSheet) {
        ModalBottomSheet(
            onDismissRequest = { showModelSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = OnboardingConfig.surfaceDark,
        ) {
            Text("Choose model", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(modelOptions) { model ->
                    Surface(
                        color = if (model == selectedModel) OnboardingConfig.accentBlue.copy(alpha = 0.18f) else OnboardingConfig.bgDark.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, if (model == selectedModel) OnboardingConfig.accentBlue else OnboardingConfig.cardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onModelChange(model)
                                showModelSheet = false
                            },
                    ) {
                        Text(model, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(16.dp))
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showProviderSheet) {
        ModalBottomSheet(
            onDismissRequest = { showProviderSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = OnboardingConfig.surfaceDark,
        ) {
            Text(
                "Choose provider",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            )
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OnboardingConfig.AiProvider.entries.forEach { item ->
                    Surface(
                        color = if (item == provider) OnboardingConfig.accentBlue.copy(alpha = 0.18f) else OnboardingConfig.bgDark.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, if (item == provider) OnboardingConfig.accentBlue else OnboardingConfig.cardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onProviderChange(item.key, OnboardingConfig.defaultModelFor(item))
                                remoteModels = emptyList()
                                status = null
                                showProviderSheet = false
                            },
                    ) {
                        Text(item.displayName, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(16.dp))
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun LabeledField(label: String, value: String) {
    Text(label, color = OnboardingConfig.textMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(6.dp))
    Surface(
        color = OnboardingConfig.bgDark.copy(alpha = 0.55f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, OnboardingConfig.cardBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(value, color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(14.dp))
    }
}

@Composable
private fun onboardingTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = OnboardingConfig.accentBlue,
    unfocusedBorderColor = OnboardingConfig.cardBorder,
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedContainerColor = OnboardingConfig.bgDark.copy(alpha = 0.45f),
    unfocusedContainerColor = OnboardingConfig.bgDark.copy(alpha = 0.45f),
    cursorColor = OnboardingConfig.accentBlue,
    focusedPlaceholderColor = OnboardingConfig.textMuted,
    unfocusedPlaceholderColor = OnboardingConfig.textMuted,
    errorBorderColor = MaterialTheme.colorScheme.error,
)
