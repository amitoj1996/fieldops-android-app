package com.fieldops.app.network

import android.webkit.CookieManager
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val cookieManager = CookieManager.getInstance()
        val cookies = cookieManager.getCookie(original.url.toString())

        val requestBuilder = original.newBuilder()
        if (cookies != null) {
            android.util.Log.d("AuthInterceptor", "Attaching cookies to ${original.url} (masked)")
            requestBuilder.header("Cookie", cookies)
        } else {
            android.util.Log.d("AuthInterceptor", "No cookies found for ${original.url}")
        }

        return chain.proceed(requestBuilder.build())
    }
}
