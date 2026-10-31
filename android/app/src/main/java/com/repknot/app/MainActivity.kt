package com.repknot.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.repknot.app.ui.theme.RepknotTheme
import com.repknot.data.CloudState
import com.repknot.domain.LoadUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        val app = application as RepknotApplication
        val loggingFactory = LoggingViewModel.Factory(app.repository)
        val cloudFactory = CloudViewModel.Factory(app.cloud, app.repository)
        setContent {
            RepknotTheme {
                val model: LoggingViewModel = viewModel(factory = loggingFactory)
                val cloud: CloudViewModel = viewModel(factory = cloudFactory)
                val state by model.state.collectAsState()
                val cloudState by cloud.state.collectAsState()
                LoggingScreen(state, model::save) {
                    CloudSection(cloudState, cloud::signIn, cloud::signOut, cloud::uploadTestSession)
                }
            }
        }
    }
}

@Composable
private fun LoggingScreen(
    state: LoggingState,
    save: (String, LoadUnit, String, String) -> Unit,
    footer: @Composable () -> Unit,
) {
    Scaffold { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding()
                .verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.app_name), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.demo_workout_title), style = MaterialTheme.typography.headlineLarge)
            Text(stringResource(R.string.demo_exercise_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.demo_disclaimer), color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (state.loading) {
                CircularProgressIndicator()
            } else {
                SetEditor(state, save)
            }
            footer()
        }
    }
}

@Composable
private fun SetEditor(state: LoggingState, save: (String, LoadUnit, String, String) -> Unit) {
    // Initial values come from an explicit saved set, never from the previous-value hint.
    var load by rememberSaveable { mutableStateOf(state.saved?.values?.load?.toString() ?: "") }
    var reps by rememberSaveable { mutableStateOf(state.saved?.values?.reps?.toString() ?: "") }
    var rir by rememberSaveable { mutableStateOf(state.saved?.values?.rir?.toString() ?: "") }
    var unitName by rememberSaveable { mutableStateOf(state.saved?.values?.unit?.name ?: "KG") }
    Text(stringResource(R.string.demo_previous_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
    OutlinedTextField(load, { load = it }, label = { Text(stringResource(R.string.label_load)) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        LoadUnit.entries.forEach { unit ->
            FilterChip(selected = unitName == unit.name, onClick = { unitName = unit.name }, label = { Text(unit.name.lowercase()) })
        }
    }
    OutlinedTextField(reps, { reps = it }, label = { Text(stringResource(R.string.label_reps)) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
    OutlinedTextField(rir, { rir = it }, label = { Text(stringResource(R.string.label_rir)) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
    state.error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
    Button(onClick = { save(load, LoadUnit.valueOf(unitName), reps, rir) }, enabled = !state.saving,
        modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(when {
            state.saving -> R.string.action_saving
            state.saved == null -> R.string.action_log_set
            else -> R.string.action_update_set
        }))
    }
    val saved = state.saved
    if (saved == null) {
        Text(stringResource(R.string.no_set_logged), color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.saved_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(
                    R.string.saved_summary,
                    saved.values.load.toString(),
                    saved.values.unit.name.lowercase(),
                    pluralStringResource(R.plurals.reps_count, saved.values.reps, saved.values.reps),
                    saved.values.rir?.toString() ?: stringResource(R.string.saved_rir_blank),
                ))
                if (saved.pendingSync) {
                    Text(stringResource(R.string.pending_sync), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun CloudSection(
    state: CloudUiState,
    signIn: (String, String) -> Unit,
    signOut: () -> Unit,
    uploadTestSession: () -> Unit,
) {
    HorizontalDivider()
    Text(stringResource(R.string.cloud_title), style = MaterialTheme.typography.titleLarge)
    when (val cloud = state.cloud) {
        CloudState.NotConfigured ->
            Text(stringResource(R.string.cloud_not_configured), color = MaterialTheme.colorScheme.onSurfaceVariant)
        CloudState.Loading -> CircularProgressIndicator()
        CloudState.SignedOut -> {
            var email by rememberSaveable { mutableStateOf("") }
            var password by rememberSaveable { mutableStateOf("") }
            OutlinedTextField(email, { email = it }, label = { Text(stringResource(R.string.label_email)) },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth())
            OutlinedTextField(password, { password = it }, label = { Text(stringResource(R.string.label_password)) },
                singleLine = true, visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth())
            Button(onClick = { signIn(email, password) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_sign_in))
            }
        }
        is CloudState.SignedIn -> {
            Text(stringResource(R.string.cloud_signed_in_as, cloud.email))
            state.sessionCount?.let {
                Text(pluralStringResource(R.plurals.cloud_session_count, it, it),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            state.lastCreatedNumber?.let { Text(stringResource(R.string.cloud_created, it)) }
            Button(onClick = uploadTestSession, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_upload_test))
            }
            OutlinedButton(onClick = signOut, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_sign_out))
            }
        }
    }
    state.error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
}
