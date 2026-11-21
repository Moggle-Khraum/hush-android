package com.mogglestone.hush

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * Ends running processes of blocked apps so their open connections die too.
 *
 * Without root, Android only lets a normal app call killBackgroundProcesses(),
 * which stops cached/background processes. It cannot force-stop an app that is
 * in the foreground, and it cannot delete another app's files. For a full
 * force-stop the user has to tap "Force stop" on the app's settings page,
 * which openForceStopPage() opens.
 */
object Killer {

    fun killBackground(context: Context, packages: Collection<String>) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (pkg in packages) {
            try {
                am.killBackgroundProcesses(pkg)
            } catch (_: Exception) {
            }
        }
    }

    fun openForceStopPage(context: Context, pkg: String) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", pkg, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
