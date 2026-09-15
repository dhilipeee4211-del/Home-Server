package com.example.network

import android.content.Context
import android.content.SharedPreferences
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor : Interceptor {

    private var sharedPreferences: SharedPreferences? = null
    @Volatile
    private var cachedToken: String? = null

    fun initialize(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        this.sharedPreferences = prefs
        this.cachedToken = prefs.getString(KEY_AUTH_TOKEN, null)
    }

    fun setToken(token: String?) {
        cachedToken = token
        sharedPreferences?.edit()?.apply {
            if (token != null) {
                putString(KEY_AUTH_TOKEN, token)
            } else {
                remove(KEY_AUTH_TOKEN)
            }
            apply()
        }
    }

    fun getToken(): String? = cachedToken

    fun clear() {
        setToken(null)
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val token = cachedToken

        val newRequest = if (!token.isNullOrBlank()) {
            originalRequest.newBuilder()
                .header("Authorization", "Bearer $token")
                .header("User-Agent", USER_AGENT)
                .build()
        } else {
            originalRequest.newBuilder()
                .header("User-Agent", USER_AGENT)
                .build()
        }

        return chain.proceed(newRequest)
    }

    companion object {
        private const val PREFS_NAME = "dhiliphome_auth_prefs"
        private const val KEY_AUTH_TOKEN = "auth_token"
        const val USER_AGENT = "DhilipHome-Android/0.2.1"
    }
}
