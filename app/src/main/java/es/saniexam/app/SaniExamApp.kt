package es.saniexam.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point. Hilt graph is rooted here.
 *
 * No background work, no networking. Offline-first by design.
 */
@HiltAndroidApp
class SaniExamApp : Application()
