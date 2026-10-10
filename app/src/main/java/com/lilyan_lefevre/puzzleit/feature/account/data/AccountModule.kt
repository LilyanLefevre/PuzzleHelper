package com.lilyan_lefevre.puzzleit.feature.account.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.IOException
import java.security.GeneralSecurityException
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton
import okhttp3.OkHttpClient

/** The encrypted preferences that hold the signed-in account. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AccountPrefs

@Module
@InstallIn(SingletonComponent::class)
object AccountModule {

    /** Photos go up and down: a long read timeout on a slow home connection, but a short one to notice a server that is off. */
    @Provides
    @Singleton
    fun provideHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    /** Jetpack Security: values are encrypted (AES-256-GCM) with a key that never leaves the Android Keystore. */
    @Provides
    @Singleton
    @AccountPrefs
    fun provideAccountPrefs(@ApplicationContext context: Context): SharedPreferences {
        fun open() = EncryptedSharedPreferences.create(
            context, "account_secure", MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
        return try {
            open()
        } catch (e: GeneralSecurityException) {
            // The Keystore lost its key (restore, factory reset of the keys): the stored token is unreadable, so sign in again.
            context.deleteSharedPreferences("account_secure"); open()
        } catch (e: IOException) {
            context.deleteSharedPreferences("account_secure"); open()
        }
    }
}
