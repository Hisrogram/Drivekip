package com.heeseung.drivekip.data

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AuthManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("drivekip_auth", Context.MODE_PRIVATE)

    companion object {
        const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_USER_NAME = "user_name"
    }

    val googleSignInClient: GoogleSignInClient by lazy {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DRIVE_FILE_SCOPE))
            .build()
        GoogleSignIn.getClient(context, gso)
    }

    var accessToken: String?
        get() = prefs.getString(KEY_ACCESS_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_ACCESS_TOKEN, value).apply()

    var userEmail: String?
        get() = prefs.getString(KEY_USER_EMAIL, null)
        set(value) = prefs.edit().putString(KEY_USER_EMAIL, value).apply()

    var userName: String?
        get() = prefs.getString(KEY_USER_NAME, null)
        set(value) = prefs.edit().putString(KEY_USER_NAME, value).apply()

    val isSignedIn: Boolean
        get() = !accessToken.isNullOrBlank()

    suspend fun handleSignInResult(account: GoogleSignInAccount): String? = withContext(Dispatchers.IO) {
        try {
            userEmail = account.email
            userName = account.displayName

            // Fetch OAuth access token with scope
            val accountObj = account.account ?: return@withContext null
            val token = GoogleAuthUtil.getToken(
                context,
                accountObj,
                "oauth2:$DRIVE_FILE_SCOPE"
            )
            accessToken = token
            token
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun refreshCurrentToken(): String? = withContext(Dispatchers.IO) {
        val lastAccount = GoogleSignIn.getLastSignedInAccount(context)
        if (lastAccount != null && lastAccount.account != null) {
            try {
                // Clear old token cache if any
                accessToken?.let { GoogleAuthUtil.clearToken(context, it) }
                val newToken = GoogleAuthUtil.getToken(
                    context,
                    lastAccount.account!!,
                    "oauth2:$DRIVE_FILE_SCOPE"
                )
                accessToken = newToken
                return@withContext newToken
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        accessToken
    }

    fun signOut(onComplete: () -> Unit) {
        accessToken = null
        userEmail = null
        userName = null
        googleSignInClient.signOut().addOnCompleteListener {
            onComplete()
        }
    }
}
