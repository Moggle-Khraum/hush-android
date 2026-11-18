package com.example.netblocker

import android.content.Context
import android.content.pm.ApplicationInfo
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
 */
object Related {

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

    /** Packages (other than the given ones) that count as related to them. */
    fun relatedTo(context: Context, packages: Set<String>): Set<String> {
        if (packages.isEmpty()) return emptySet()
        val pm = context.packageManager
        val installed: List<ApplicationInfo> = pm.getInstalledApplications(0)

        val prefixes = packages.map(::prefix).filter { it !in NEVER_GROUP }.toSet()
        val sharedIds = HashSet<String>()
        for (p in packages) {
            try {
                pm.getPackageInfo(p, 0).sharedUserId?.let { sharedIds.add(it) }
            } catch (_: PackageManager.NameNotFoundException) {
            }
        }

        val out = LinkedHashSet<String>()
        for (info in installed) {
            val name = info.packageName
            if (name == context.packageName || name in packages || name in PROTECTED) continue
            val sameSharedId = try {
                pm.getPackageInfo(name, 0).sharedUserId?.let { it in sharedIds } ?: false
            } catch (_: PackageManager.NameNotFoundException) {
                false
            }
            if (sameSharedId || prefix(name) in prefixes) out.add(name)
        }
        return out
    }

    /** Selected packages plus their relatives (if the option is on). */
    fun expand(context: Context, packages: Set<String>): Set<String> =
        if (Prefs.includeRelated(context)) packages + relatedTo(context, packages) else packages
}
