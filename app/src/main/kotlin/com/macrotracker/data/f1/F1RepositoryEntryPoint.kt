package com.macrotracker.data.f1

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface F1RepositoryEntryPoint {
    fun f1Repository(): F1Repository
}

internal fun Context.f1Repository(): F1Repository =
    EntryPointAccessors.fromApplication(applicationContext, F1RepositoryEntryPoint::class.java).f1Repository()

