package neth.iecal.curbox.data.sync

import android.app.Activity
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The full build never charges for sync, so everyone is always entitled. */
class SyncEntitlement(@Suppress("UNUSED_PARAMETER") context: Context) {
    val billing: StateFlow<SyncBillingStatus> = MutableStateFlow(SyncBillingStatus())
    fun refresh() {}
    fun launchPurchase(@Suppress("UNUSED_PARAMETER") activity: Activity) {}
}
