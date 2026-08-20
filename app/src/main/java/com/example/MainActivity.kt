package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.MainChatScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SetupScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.ChatViewModel

enum class AppDestination {
    CHAT,
    SETTINGS,
    SETUP
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: ChatViewModel = viewModel()
            val config by viewModel.config.collectAsStateWithLifecycle()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val activeConversations by viewModel.activeConversations.collectAsStateWithLifecycle()
            val archivedConversations by viewModel.archivedConversations.collectAsStateWithLifecycle()
            val messages by viewModel.messages.collectAsStateWithLifecycle()

            // Dynamic Dark/Light Theme selection
            val isDarkTheme = when (config.themeMode) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }

            var currentDestination by remember(config.isConfigured, config.apiKey) {
                mutableStateOf(
                    if (!config.isConfigured && config.apiKey.isEmpty()) AppDestination.SETUP
                    else AppDestination.CHAT
                )
            }

            MyApplicationTheme(darkTheme = isDarkTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    when (currentDestination) {
                        AppDestination.SETUP -> {
                            SetupScreen(
                                currentConfig = config,
                                onCompleteSetup = { newConfig ->
                                    viewModel.saveConfig(newConfig)
                                    currentDestination = AppDestination.CHAT
                                },
                                onTestConnection = { tempConfig, callback ->
                                    viewModel.testConnection(tempConfig, callback)
                                }
                            )
                        }
                        AppDestination.SETTINGS -> {
                            SettingsScreen(
                                config = config,
                                models = uiState.models,
                                isLoadingModels = uiState.isLoadingModels,
                                onSaveConfig = { newConfig ->
                                    viewModel.saveConfig(newConfig)
                                },
                                onTestConnection = { tempConfig, callback ->
                                    viewModel.testConnection(tempConfig, callback)
                                },
                                onRefreshModels = { viewModel.refreshModels() },
                                onOpenDebugLogs = { /* handled in settings */ },
                                onClearAllConversations = { viewModel.deleteAllConversations() },
                                onBack = { currentDestination = AppDestination.CHAT }
                            )
                        }
                        AppDestination.CHAT -> {
                            MainChatScreen(
                                viewModel = viewModel,
                                uiState = uiState,
                                config = config,
                                activeConversations = activeConversations,
                                archivedConversations = archivedConversations,
                                messages = messages,
                                onOpenSettings = { currentDestination = AppDestination.SETTINGS }
                            )
                        }
                    }
                }
            }
        }
    }
}
