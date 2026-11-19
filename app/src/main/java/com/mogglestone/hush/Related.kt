package com.mogglestone.hush

import android.content.Context
import android.content.pm.PackageManager

/**
 * Finds apps that belong to the same "family" as a selected app, so blocking
 * Messenger also covers e.g. Facebook, Meta services and helper packages that
 * could otherwise keep a connection alive on its behalf.
 *
 * Two packages are related when they share a sharedUserId, or the same first
 * two package segments (com.facebook.orca ~ com.facebook.katana).
 * Platform/vendor prefixes are never expanded, so selecting one Google or OEM
 * app can't drag in Play Services or the whole system.
 *
 * Callers should run this off the main thread: it scans every installed app.
 */
object Related {
    private const val TAG = "Hush"

    // Prefixes that cover many unrelated apps; never used for grouping.
    private val NEVER_GROUP = setOf(
        "android", "com.android", "com.google", "com.samsung", "com.sec",
        "com.qualcomm", "com.mediatek", "com.miui", "com.xiaomi", "com.huawei",
        "com.oppo", "com.coloros", "com.vivo", "com.oneplus", "org.codeaurora"
    )

    // Never block these, even if they look related.
    private val PROTECTED = setOf(
        "com.google.android.gms", "com.google.android.gsf", "com.android.vending"
    )

    private fun prefix(pkg: String): String =
        pkg.split('.').take(2).joinToString(".")

    // sharedUserId is deprecated on Android 10+, but it's still the only way to
    // detect packages that share a UID.
    @Suppress("DEPRECATION")
    private fun sharedIdOf(pm: PackageManager, pkg: String): String? =
        try {
            pm.getPackageInfo(pkg, 0).sharedUserId
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }

    /** Packages (other than the given ones) that count as related to them. */
    fun relatedTo(context: Context, packages: Set<String>): Set<String> {
        if (packages.isEmpty()) return emptySet()
        val pm = context.packageManager
        val installed = pm.getInstalledApplications(0).map { it.packageName }

        val prefixes = packages.map(::prefix).filter { it !in NEVER_GROUP }.toSet()
        val sharedIds = packages.mapNotNullTo(HashSet()) { sharedIdOf(pm, it) }

        val out = LinkedHashSet<String>()
        for (name in installed) {
            if (name == context.packageName || name in packages || name in PROTECTED) continue
            // Cheap prefix check first; only look up sharedUserId when needed.
            val samePrefix = prefix(name) in prefixes
            val sameShared = !samePrefix && sharedIds.isNotEmpty() &&
                sharedIdOf(pm, name)?.let { it in sharedIds } == true
            if (samePrefix || sameShared) out.add(name)
        }
        return out
    }

    /** Selected packages plus their relatives (if the option is on). */
    fun expand(context: Context, packages: Set<String>): Set<String> =
        if (Prefs.includeRelated(context)) packages + relatedTo(context, packages) else packages
}
