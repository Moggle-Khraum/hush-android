package com.mogglestone.hush

import android.content.Context

/** Settings for both modes. Each mode keeps its own app list and on/off state. */
object Prefs {
    private const val FILE = "hush"
    private const val KEY_NET_PACKAGES = "netblock_packages"
    private const val KEY_NET_ACTIVE = "netblock_active"
    private const val KEY_REDIRECT_PACKAGES = "redirect_packages"
    private const val KEY_REDIRECT_ACTIVE = "redirect_active"
    private const val KEY_RELATED = "include_related"
    private const val KEY_KILL = "kill_processes"
    private const val KEY_THEME = "theme_mode"
    private const val KEY_REDIRECT_SITES = "redirect_sites"
    private const val KEY_REDIRECT_UNKNOWN = "redirect_unknown"
    const val DEFAULT_SITES = "shopee,lazada"

    const val THEME_SYSTEM = "system"
    const val THEME_LIGHT = "light"
    const val THEME_DARK = "dark"

    private fun sp(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    // NetBlock
    fun netblockPackages(c: Context): Set<String> =
        sp(c).getStringSet(KEY_NET_PACKAGES, emptySet()) ?: emptySet()

    fun setNetblockPackages(c: Context, packages: Set<String>) {
        sp(c).edit().putStringSet(KEY_NET_PACKAGES, packages.toSet()).apply()
    }

    fun isNetblockActive(c: Context): Boolean = sp(c).getBoolean(KEY_NET_ACTIVE, false)

    fun setNetblockActive(c: Context, active: Boolean) {
        sp(c).edit().putBoolean(KEY_NET_ACTIVE, active).apply()
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

    // RedirectBlock
    fun redirectPackages(c: Context): Set<String> =
        sp(c).getStringSet(KEY_REDIRECT_PACKAGES, emptySet()) ?: emptySet()

    fun setRedirectPackages(c: Context, packages: Set<String>) {
        sp(c).edit().putStringSet(KEY_REDIRECT_PACKAGES, packages.toSet()).apply()
    }

    fun isRedirectActive(c: Context): Boolean = sp(c).getBoolean(KEY_REDIRECT_ACTIVE, false)

    fun setRedirectActive(c: Context, active: Boolean) {
        sp(c).edit().putBoolean(KEY_REDIRECT_ACTIVE, active).apply()
    }

    /** Theme choice: THEME_SYSTEM, THEME_LIGHT or THEME_DARK. */
    fun themeMode(c: Context): String = sp(c).getString(KEY_THEME, THEME_SYSTEM) ?: THEME_SYSTEM

    fun setThemeMode(c: Context, mode: String) {
        sp(c).edit().putString(KEY_THEME, mode).apply()
    }

    /** Raw text the user typed, e.g. "shopee, lazada". */
    fun redirectSitesRaw(c: Context): String =
        sp(c).getString(KEY_REDIRECT_SITES, DEFAULT_SITES) ?: DEFAULT_SITES

    fun setRedirectSitesRaw(c: Context, text: String) {
        sp(c).edit().putString(KEY_REDIRECT_SITES, text).apply()
    }

    /** Words to match against each domain label, lowercased. */
    fun redirectSites(c: Context): Set<String> =
        redirectSitesRaw(c).split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()

    /** Also drop links whose sender Android doesn't report. */
    fun redirectUnknown(c: Context): Boolean = sp(c).getBoolean(KEY_REDIRECT_UNKNOWN, false)

    fun setRedirectUnknown(c: Context, value: Boolean) {
        sp(c).edit().putBoolean(KEY_REDIRECT_UNKNOWN, value).apply()
    }
}
