package com.lilyan_lefevre.puzzleit.core.database

import android.content.Context
import androidx.room.Room
import com.lilyan_lefevre.puzzleit.feature.history.data.ScanRecordDao
import com.lilyan_lefevre.puzzleit.feature.progress.data.ProgressPhotoDao
import com.lilyan_lefevre.puzzleit.feature.project.data.ProjectDao
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
            ProjectMigration4,
            ProjectMigration5,
            ProjectMigration6
        )
        .build()
    }

    @Provides
    fun provideScanRecordDao(database: AppDatabase): ScanRecordDao = database.scanRecordDao()

    @Provides
    fun provideProgressPhotoDao(database: AppDatabase): ProgressPhotoDao = database.progressPhotoDao()

    @Provides
    fun provideProjectDao(database: AppDatabase): ProjectDao {
        return database.projectDao()
    }
}
