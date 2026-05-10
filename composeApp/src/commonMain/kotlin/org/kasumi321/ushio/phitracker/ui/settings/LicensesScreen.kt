package org.kasumi321.ushio.phitracker.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import phitracker.composeapp.generated.resources.Res

private sealed interface LicenseLoadState {
    data object Loading : LicenseLoadState
    data class Loaded(val libraries: Libs) : LicenseLoadState
    data class Failed(val message: String?) : LicenseLoadState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensesScreen(
    onNavigateBack: () -> Unit
) {
    val loadState = produceState<LicenseLoadState>(LicenseLoadState.Loading) {
        value = withContext(Dispatchers.Default) {
            runCatching {
                val json = Res.readBytes("files/aboutlibraries.json").decodeToString()
                Libs.Builder().withJson(json).build()
            }.fold(
                onSuccess = { LicenseLoadState.Loaded(it) },
                onFailure = { LicenseLoadState.Failed(it.message) }
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("第三方组件许可") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        when (val state = loadState.value) {
            LicenseLoadState.Loading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }

            is LicenseLoadState.Loaded -> LibrariesContainer(
                libraries = state.libraries,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )

            is LicenseLoadState.Failed -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = state.message
                        ?.takeIf { it.isNotBlank() }
                        ?.let { "第三方组件信息加载失败：$it" }
                        ?: "第三方组件信息加载失败"
                )
            }
        }
    }
}
