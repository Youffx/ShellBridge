package com.shellbridge.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.shellbridge.app.screens.SSHInputScreen
import com.shellbridge.app.screens.TerminalScreen
import com.shellbridge.app.ui.theme.ShellBridgeTheme
import java.net.URLEncoder

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ShellBridgeTheme {
                ShellBridgeNavHost()
            }
        }
    }
}

@Composable
fun ShellBridgeNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "ssh_input",
        modifier = Modifier.fillMaxSize()
    ) {
        composable("ssh_input") {
            SSHInputScreen(
                onConnect = { sshInput ->
                    val encoded = URLEncoder.encode(sshInput, "UTF-8")
                    navController.navigate("terminal/$encoded")
                }
            )
        }

        composable(
            route = "terminal/{sshInput}",
            arguments = listOf(navArgument("sshInput") { type = NavType.StringType })
        ) { backStackEntry ->
            val sshInput = backStackEntry.arguments?.getString("sshInput") ?: ""
            TerminalScreen(
                sshInput = sshInput,
                onDisconnect = {
                    navController.popBackStack()
                }
            )
        }
    }
}
