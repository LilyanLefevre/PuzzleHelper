package com.lilyan_lefevre.puzzleit.di

import android.content.Context
import androidx.room.Room
import com.lilyan_lefevre.puzzleit.shared.database.AppDatabase
import com.lilyan_lefevre.puzzleit.shared.database.ProjectDao
import com.lilyan_lefevre.puzzleit.shared.database.ProjectMigration
import com.lilyan_lefevre.puzzleit.shared.database.ProjectMigration2
import com.lilyan_lefevre.puzzleit.shared.database.ProjectMigration3
import com.lilyan_lefevre.puzzleit.shared.database.ProjectMigration4
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "puzzle_database"
        )
        .addMigrations(
            ProjectMigration,
            ProjectMigration2,
            ProjectMigration3,
            ProjectMigration4
        )
        .build()
    }

    @Provides
    fun provideProjectDao(database: AppDatabase): ProjectDao {
        return database.projectDao()
    }
}
