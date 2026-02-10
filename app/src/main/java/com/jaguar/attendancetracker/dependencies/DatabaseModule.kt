package com.jaguar.attendancetracker.dependencies

import android.content.Context
import androidx.room.Room
import com.jaguar.attendancetracker.backend.AttendanceDatabase
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
    fun provideDatabase(
        @ApplicationContext context: Context
    ): AttendanceDatabase = Room.databaseBuilder(
        context, AttendanceDatabase::class.java, "AttendanceDB"
    ).fallbackToDestructiveMigration(
        true
    ).build()
}