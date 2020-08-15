/*
 * Copyright (C) 2020. by onlymash <im@fiepi.me>, All rights reserved
 *
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */

package onlymash.flexbooru.app

import android.app.Application
import android.graphics.drawable.Drawable
import android.net.Uri
import android.widget.ImageView
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import com.android.billingclient.api.*
import com.bumptech.glide.Glide
import com.mikepenz.materialdrawer.util.AbstractDrawerImageLoader
import com.mikepenz.materialdrawer.util.DrawerImageLoader
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import onlymash.flexbooru.BuildConfig
import onlymash.flexbooru.R
import onlymash.flexbooru.app.Settings.isGoogleSign
import onlymash.flexbooru.app.Settings.isOrderSuccess
import onlymash.flexbooru.app.Settings.nightMode
import onlymash.flexbooru.app.Settings.orderDeviceId
import onlymash.flexbooru.app.Settings.orderId
import onlymash.flexbooru.crash.CrashHandler
import onlymash.flexbooru.data.api.BooruApis
import onlymash.flexbooru.data.api.OrderApi
import onlymash.flexbooru.data.database.MyDatabase
import onlymash.flexbooru.extension.getSignMd5
import onlymash.flexbooru.glide.GlideApp
import onlymash.flexbooru.ui.activity.PurchaseActivity
import org.kodein.di.DI
import org.kodein.di.DIAware

class App : Application(), DIAware {

    companion object {
        lateinit var app: App
    }

    override val di by DI.lazy {
        import(appModule(this@App))
    }

    private val drawerImageLoader = object : AbstractDrawerImageLoader() {
        override fun set(imageView: ImageView, uri: Uri, placeholder: Drawable, tag: String?) {
            GlideApp.with(imageView.context)
                .load(uri)
                .centerCrop()
                .placeholder(ContextCompat.getDrawable(imageView.context, R.drawable.avatar_account))
                .into(imageView)
        }
        override fun cancel(imageView: ImageView) {
            Glide.with(imageView.context).clear(imageView)
        }
    }

    override fun onCreate() {
        super.onCreate()
        app = this
        initial()
    }

    private fun initial() {
        AppCompatDelegate.setDefaultNightMode(nightMode)
        DrawerImageLoader.init(drawerImageLoader)
        if (BuildConfig.DEBUG) {
            return
        }
        CrashHandler.getInstance().init(this)
        checkOrder()
    }

    private fun checkOrder() {
                isOrderSuccess = false
    }

    private fun checkOrderFromCache() {
        val billingClient = BillingClient
            .newBuilder(this)
            .enablePendingPurchases()
            .setListener { _, _ ->  }
            .build()
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingClient.isReady) {
                    val purchases = billingClient.queryPurchases(BillingClient.SkuType.INAPP).purchasesList
                    isOrderSuccess = if (purchases.isNullOrEmpty()) {
                        false
                    } else {
                        val index = purchases.indexOfFirst {
                            it.sku == PurchaseActivity.SKU && it.purchaseState == Purchase.PurchaseState.PURCHASED
                        }
                        if (index >= 0) {
                            val purchase = purchases[index]
                            if (!purchase.isAcknowledged) {
                                val ackParams = AcknowledgePurchaseParams.newBuilder()
                                    .setPurchaseToken(purchase.purchaseToken)
                                    .build()
                                billingClient.acknowledgePurchase(ackParams){}
                            }
                            true
                        } else false
                    }
                    billingClient.endConnection()
                }
            }
            override fun onBillingServiceDisconnected() {
                billingClient.endConnection()
            }
        })
    }
}
