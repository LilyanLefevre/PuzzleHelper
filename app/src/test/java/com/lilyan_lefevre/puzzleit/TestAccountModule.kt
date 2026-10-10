package com.lilyan_lefevre.puzzleit

import android.content.Context
import android.content.SharedPreferences
import com.lilyan_lefevre.puzzleit.feature.account.data.AccountModule
import com.lilyan_lefevre.puzzleit.feature.account.data.AccountPrefs
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton
import okhttp3.OkHttpClient

/** The JVM has no Android Keystore, so the encrypted account preferences are replaced by plain ones (signed in). */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [AccountModule::class])
object TestAccountModule {

    @Provides
    @Singleton
    fun provideHttpClient(): OkHttpClient = OkHttpClient()

    @Provides
    @Singleton
    @AccountPrefs
    fun provideAccountPrefs(@ApplicationContext context: Context): SharedPreferences =
        context.getSharedPreferences("test_account", Context.MODE_PRIVATE).also {
            it.edit().clear().putString("server", "http://127.0.0.1:1").putString("token", "test").putString("email", "unit@test.dev").commit()
        }
}
