package com.fieldops.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.fieldops.app.network.ApiService
import com.fieldops.app.network.AuthInterceptor
import com.fieldops.app.ui.employee.EmployeeDashboard
import com.fieldops.app.ui.employee.TaskDetailScreen
import com.fieldops.app.ui.login.LoginScreen
import com.fieldops.app.ui.theme.FieldOpsTheme
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Body-level logging prints request + response bodies in plaintext —
        // including session cookies, SAS upload/read URLs, and OCR/report
        // payloads. Gate it so release builds don't leak these to logcat.
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor())
            .addInterceptor(logging)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(Constants.BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()

        val apiService = retrofit.create(ApiService::class.java)

        setContent {
            FieldOpsTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    NavHost(navController = navController, startDestination = "login") {
                        composable("login") {
                            LoginScreen(navController, apiService)
                        }
                        composable("employee") {
                            EmployeeDashboard(navController, apiService)
                        }
                        composable("task/{taskId}") { backStackEntry ->
                            val taskId = backStackEntry.arguments?.getString("taskId")
                            if (taskId != null) {
                                TaskDetailScreen(navController, apiService, taskId)
                            }
                        }
                    }
                }
            }
        }
    }
}
