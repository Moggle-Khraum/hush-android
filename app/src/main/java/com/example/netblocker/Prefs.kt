package com.example.netblocker

import android.content.Context

object Prefs {
    private const val FILE = "netblocker"
    private const val KEY_PACKAGES = "blocked_packages"
    private const val KEY_ACTIVE = "active"
    private const val KEY_RELATED = "include_related"
    private const val KEY_KILL = "kill_processes"

    private fun sp(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

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
}
