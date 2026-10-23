package com.towerchat.app.di

import com.towerchat.app.data.session.ChatSessionManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideChatSessionManager(): ChatSessionManager {
        return ChatSessionManager()
    }
}
