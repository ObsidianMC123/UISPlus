package vn.uisplus

import android.app.Application
import vn.uisplus.focus.Focus
import vn.uisplus.work.Notifier

class UisApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifier.createChannels(this)
        runCatching { Focus.init(this) }
        runCatching { vn.uisplus.bus.BusTracker.init(this); vn.uisplus.bus.BusNotify.channels(this) }
    }
}
