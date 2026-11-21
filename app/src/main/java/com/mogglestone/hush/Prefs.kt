package com.mogglestone.hush

import android.content.Context

/**
 * All settings live here. The two modes are independent:
 *  - NetBlock:      blockedPackages + active (VPN)
 *  - RedirectBlock: redirectPackages + redirectOn + blocklist (link gate)
 */
object Prefs {
    private const val FILE = "netblocker"
    private const val KEY_PACKAGES = "blocked_packages"
    private const val KEY_ACTIVE = "active"
    private const val KEY_RELATED = "include_related"
    private const val KEY_KILL = "kill_processes"
    private const val KEY_REDIRECT_PACKAGES = "redirect_packages"
    private const val KEY_REDIRECT_ON = "redirect_on"
    private const val KEY_BLOCKLIST = "blocklist"

    private fun sp(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    // ---- NetBlock ----
    fun blockedPackages(c: Context): Set<String> =
        sp(c).getStringSet(KEY_PACKAGES, emptySet()) ?: emptySet()

    fun setBlockedPackages(c: Context, packages: Set<String>) {
        sp(c).edit().putStringSet(KEY_PACKAGES, packages.toSet()).apply()
    }

    fun isActive(c: Context): Boolean = sp(c).getBoolean(KEY_ACTIVE, false)

    fun setActive(c: Context, active: Boolean) {
        sp(c).edit().putBoolean(KEY_ACTIVE, active).apply()
    }

    /** Also block apps from the same family as the selected ones. */
    fun includeRelated(c: Context): Boolean = sp(c).getBoolean(KEY_RELATED, true)

    fun setIncludeRelated(c: Context, value: Boolean) {
        sp(c).edit().putBoolean(KEY_RELATED, value).apply()
    }

    /** Keep killing the blocked apps' background processes while blocking. */
    fun killProcesses(c: Context): Boolean = sp(c).getBoolean(KEY_KILL, true)

    fun setKillProcesses(c: Context, value: Boolean) {
        sp(c).edit().putBoolean(KEY_KILL, value).apply()
    }

    // ---- RedirectBlock ----
    fun redirectPackages(c: Context): Set<String> =
        sp(c).getStringSet(KEY_REDIRECT_PACKAGES, emptySet()) ?: emptySet()

    fun setRedirectPackages(c: Context, packages: Set<String>) {
        sp(c).edit().putStringSet(KEY_REDIRECT_PACKAGES, packages.toSet()).apply()
    }

    fun redirectOn(c: Context): Boolean = sp(c).getBoolean(KEY_REDIRECT_ON, false)

    fun setRedirectOn(c: Context, value: Boolean) {
        sp(c).edit().putBoolean(KEY_REDIRECT_ON, value).apply()
    }

    /** Domains whose links are dropped (subdomains included). */
    fun blocklist(c: Context): Set<String> =
        sp(c).getStringSet(KEY_BLOCKLIST, setOf("ggslot.fund")) ?: emptySet()

    fun setBlocklist(c: Context, domains: Set<String>) {
        sp(c).edit().putStringSet(KEY_BLOCKLIST, domains.toSet()).apply()
    }
}
