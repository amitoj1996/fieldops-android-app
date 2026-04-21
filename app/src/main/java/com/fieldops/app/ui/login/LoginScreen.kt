package com.fieldops.app.ui.login

import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.fieldops.app.Constants
import com.fieldops.app.network.ApiService
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(navController: NavController, apiService: ApiService) {
    val scope = rememberCoroutineScope()

    val context = androidx.compose.ui.platform.LocalContext.current

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                        return false // Allow WebView to handle redirects
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        // Never log full URLs with query strings (they carry
                        // auth tokens) or the cookie value (AAD session
                        // token). Log only presence/absence in debug builds.
                        val cookies = CookieManager.getInstance().getCookie(url)
                        if (com.fieldops.app.BuildConfig.DEBUG) {
                            android.util.Log.d("LoginScreen", "onPageFinished host=${android.net.Uri.parse(url ?: "").host}")
                            android.util.Log.d("LoginScreen", "cookie present=${cookies != null}")
                        }

                        if (url != null && cookies != null && (cookies.contains("AppServiceAuthSession") || cookies.contains("StaticWebAppsAuthCookie"))) {
                            if (url.startsWith(Constants.BASE_URL)) {
                                CookieManager.getInstance().flush()
                                scope.launch {
                                    try {
                                        android.util.Log.d("LoginScreen", "Starting verification (getMe)...")
                                        val response = apiService.getMe()
                                        android.util.Log.d("LoginScreen", "getMe response code: ${response.code()}")
                                        
                                        if (response.isSuccessful && response.body() != null) {
                                            val authResponse = response.body()!!
                                            val clientPrincipal = authResponse.clientPrincipal
                                            
                                            if (clientPrincipal != null) {
                                                val email = clientPrincipal.userDetails
                                                if (com.fieldops.app.BuildConfig.DEBUG) {
                                                    val roles = clientPrincipal.userRoles ?: emptyList()
                                                    android.util.Log.d("LoginScreen", "User roles=$roles")
                                                }
                                                android.widget.Toast.makeText(context, "Login successful: $email", android.widget.Toast.LENGTH_SHORT).show()

                                                // Mobile app is employee-only; admin users manage
                                                // tasks/expenses from the web console. Every
                                                // authenticated role lands on /employee and sees
                                                // their own assigned tasks.
                                                navController.navigate("employee") {
                                                    popUpTo("login") { inclusive = true }
                                                }
                                            } else {
                                                android.util.Log.e("LoginScreen", "ClientPrincipal is null")
                                                android.widget.Toast.makeText(context, "Login failed: No user details found", android.widget.Toast.LENGTH_LONG).show()
                                            }
                                        } else {
                                            android.util.Log.e("LoginScreen", "Failed to get user: ${response.errorBody()?.string()}")
                                            android.widget.Toast.makeText(context, "Login verification failed: ${response.code()}", android.widget.Toast.LENGTH_LONG).show()
                                        }
                                    } catch (e: Exception) {
                                        android.util.Log.e("LoginScreen", "Exception getting user", e)
                                        android.widget.Toast.makeText(context, "Login error: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                                    }
                                }
                            } else if (com.fieldops.app.BuildConfig.DEBUG) {
                                android.util.Log.d("LoginScreen", "Cookie present but URL not on app domain yet")
                            }
                        } else if (com.fieldops.app.BuildConfig.DEBUG) {
                            android.util.Log.d("LoginScreen", "No session cookie yet on ${android.net.Uri.parse(url ?: "").host}")
                        }
                    }
                }
                loadUrl("${Constants.BASE_URL}/.auth/login/aad?post_login_redirect_uri=/after-login")
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}
