package com.repknot.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.lifecycle.viewmodel.compose.viewModel
import com.repknot.app.ui.theme.RepknotTheme
import com.repknot.data.CloudState

class MainActivity : ComponentActivity() {
    override fun onStart() {
        super.onStart()
        (application as RepknotApplication).requestRefresh()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        val app = application as RepknotApplication
        val cloudFactory = CloudViewModel.Factory(app.cloud)
        setContent {
            RepknotTheme {
                val cloud: CloudViewModel = viewModel(factory = cloudFactory)
                val cloudState by cloud.state.collectAsState()
                val workout: WorkoutViewModel = viewModel(factory = WorkoutViewModel.Factory(
                    app.database.sessions(), app.database.reference(), app.sync, app.current,
                    app.settings.restOverrides, app.settings::setRestOverride,
                ))
                val progress: ProgressViewModel = viewModel(factory = ProgressViewModel.Factory(
                    app.database.sessions(), app.database.reference(), app.database.checkIns(),
                    app.settings.waistGoalCm, app::requestRefresh,
                ))
                val history: HistoryViewModel = viewModel(factory = HistoryViewModel.Factory(app.database.sessions()))
                val coachNotes: CoachNotesViewModel = viewModel(factory = CoachNotesViewModel.Factory(
                    app.database.coachNotes(), app.database.reference(), app.settings.seenCoachNoteId,
                    app.settings::markCoachNoteSeen,
                ))
                AppNavigation(workout, progress, history, coachNotes) {
                    val waistGoal by app.settings.waistGoalCm.collectAsState()
                    SettingsScreen(waistGoal, app.settings::setWaistGoalCm) {
                        CloudSection(cloudState, cloud::signIn, cloud::signOut)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(waistGoal: Double, setWaistGoal: (Double) -> Unit, cloud: @Composable () -> Unit) {
    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.settings), style = MaterialTheme.typography.titleLarge)
            var goalText by rememberSaveable { mutableStateOf(waistGoal.toString()) }
            val parsedGoal = goalText.replace(',', '.').toDoubleOrNull()
            val invalidGoal = parsedGoal == null || parsedGoal !in 40.0..200.0
            OutlinedTextField(goalText, { value ->
                goalText = value
                value.replace(',', '.').toDoubleOrNull()?.takeIf { it in 40.0..200.0 }?.let(setWaistGoal)
            }, label = { Text(stringResource(R.string.waist_goal)) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = invalidGoal,
                supportingText = if (invalidGoal) ({ Text(stringResource(R.string.waist_goal_range)) }) else null,
                modifier = Modifier.fillMaxWidth())
            cloud()
        }
    }
}

@Composable
private fun CloudSection(
    state: CloudUiState,
    signIn: (String, String) -> Unit,
    signOut: () -> Unit,
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
            OutlinedButton(onClick = signOut, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_sign_out))
            }
        }
    }
    state.error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
}
