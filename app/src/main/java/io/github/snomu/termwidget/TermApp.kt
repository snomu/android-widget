package io.github.snomu.termwidget

import android.app.Application
import io.github.snomu.termwidget.work.RefreshScheduler

class TermApp : Application() {
    override fun onCreate() {
        super.onCreate()
        RefreshScheduler.ensurePeriodic(this)
    }
}
