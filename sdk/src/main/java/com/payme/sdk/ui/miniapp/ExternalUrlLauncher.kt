package com.payme.sdk.ui.miniapp

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.payme.sdk.PayMEMiniApp

/** Launches URLs allowed by [ExternalUrlPolicy]. URLs are never logged: they can carry tokens. */
internal object ExternalUrlLauncher {
    fun open(context: Context, rawUrl: String?): Boolean {
        val target = ExternalUrlPolicy.classify(rawUrl)
        val intent = when (target) {
            ExternalUrlPolicy.Target.Blocked -> return false
            is ExternalUrlPolicy.Target.View -> Intent(Intent.ACTION_VIEW, Uri.parse(target.uri))
            is ExternalUrlPolicy.Target.IntentUri -> try {
                Intent.parseUri(target.uri, Intent.URI_INTENT_SCHEME).apply {
                    component = null
                    selector = null
                }
            } catch (e: Exception) {
                Log.d(PayMEMiniApp.TAG, "external url rejected: ${e.javaClass.simpleName}")
                return false
            }
        }
        if (ExternalUrlPolicy.blocksDataScheme(intent.scheme)) return false
        return try {
            start(context, intent)
        } catch (_: ActivityNotFoundException) {
            val fallback = if (target is ExternalUrlPolicy.Target.IntentUri) {
                ExternalUrlPolicy.httpsFallback(intent.getStringExtra(BROWSER_FALLBACK_URL))
            } else null
            fallback != null && try {
                start(context, Intent(Intent.ACTION_VIEW, Uri.parse(fallback)))
            } catch (_: Exception) {
                false
            }
        } catch (e: Exception) {
            Log.d(PayMEMiniApp.TAG, "external url failed: ${e.javaClass.simpleName}")
            false
        }
    }

    private fun start(context: Context, intent: Intent): Boolean {
        intent.addCategory(Intent.CATEGORY_BROWSABLE)
        val activity = context.findActivity()
        if (activity == null) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        (activity ?: context).startActivity(intent)
        return true
    }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

    private const val BROWSER_FALLBACK_URL = "browser_fallback_url"
}
