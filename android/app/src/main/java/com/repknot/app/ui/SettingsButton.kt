package com.repknot.app.ui

import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.repknot.app.R

/** Settings gear; a small dot marks a signed-out account, since uploads then cannot happen. */
@Composable
fun SettingsButton(signedOut: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        BadgedBox(badge = { if (signedOut) Badge(containerColor = MaterialTheme.colorScheme.error) }) {
            Icon(painterResource(R.drawable.ic_settings),
                contentDescription = stringResource(if (signedOut) R.string.settings_signed_out else R.string.settings))
        }
    }
}
