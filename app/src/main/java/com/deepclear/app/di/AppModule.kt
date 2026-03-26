package com.deepclear.app.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Application-level Hilt dependency injection module.
 * Bindings will be added as features are implemented.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule
