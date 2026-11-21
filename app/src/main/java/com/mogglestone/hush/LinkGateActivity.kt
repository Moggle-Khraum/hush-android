package com.mogglestone.hush

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast

/**
 * RedirectBlock. Hush registers for http/https links, so Android sends links here.
 *
 *  - Blocked domain, RedirectBlock on, and the sender is unknown or listed
 *    (empty list = block all senders): drop it silently.
 *  - Anything else: hand it to a real browser (never Hush itself, to avoid a loop).
 *
 * Limits: apps with their own in-app browser never send links here, and apps
 * that name Chrome directly bypass Hush. Android's referrer is not always set.
 */
class LinkGateActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.ensureSeeded(this)

        val uri: Uri? = intent?.data
        val scheme = uri?.scheme?.lowercase()
        if (uri == null || (scheme != "http" && scheme != "https")) {
            finish()
            return
        }

        val host = uri.host?.lowercase()?.trimEnd('.') ?: ""
        val blocked = host.isNotEmpty() && Prefs.blocklist(this).any { d ->
            host == d || host.endsWith(".$d")
        }

        val source = referrer?.takeIf { it.scheme == "android-app" }?.host
        val list = Prefs.redirectPackages(this)
        // Empty list = block links from any sender. Otherwise block when the
        // sender is unknown (we can't prove it's allowed) or explicitly listed.
        val fromSelected = list.isEmpty() || source == null || source in list

        if (Prefs.redirectOn(this) && blocked && fromSelected) {
            Toast.makeText(this, "Blocked link to $host", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val browser = findBrowser()
        if (browser == null) {
            Toast.makeText(this, "No browser found to open this link", Toast.LENGTH_LONG).show()
        } else {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage(browser))
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(this, "Couldn't open link", Toast.LENGTH_LONG).show()
            }
        }
        finish()
    }

    /** First enabled browser that is not Hush. */
    private fun findBrowser(): String? {
        val probe = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .addCategory(Intent.CATEGORY_DEFAULT)
        return packageManager
            .queryIntentActivities(probe, PackageManager.MATCH_DEFAULT_ONLY)
            .asSequence()
            .map { it.activityInfo }
            .filter { it.enabled && it.packageName != packageName }
            .map { it.packageName }
            .distinct()
            .firstOrNull()
    }
}
