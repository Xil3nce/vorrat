package de.example.vorrat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Text(stringResource(R.string.app_name)) }
    }

    companion object {
        const val EXTRA_OPEN_FILTER = "open_filter"
        const val FILTER_SOON = "soon"
    }
}
