package com.fieldops.app

/**
 * App constants. The backend URL comes from BuildConfig so debug and
 * release variants can point at different Static Web Apps slots without
 * touching source. See app/build.gradle.kts buildConfigField.
 */
object Constants {
    val BASE_URL: String = BuildConfig.BASE_URL
}
