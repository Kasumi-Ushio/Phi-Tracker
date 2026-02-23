package org.kasumi321.ushio.phitracker.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.kasumi321.ushio.phitracker.data.repository.PhigrosRepositoryImpl
import org.kasumi321.ushio.phitracker.domain.repository.PhigrosRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindPhigrosRepository(
        impl: PhigrosRepositoryImpl
    ): PhigrosRepository
}
