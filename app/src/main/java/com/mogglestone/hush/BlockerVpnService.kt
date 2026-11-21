package com.mogglestone.hush

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import java.io.FileInputStream

/**
 * A local "black hole" VPN.
 *
 * Only the blocked apps (the ones the user selected, plus their relatives when
 * that option is on) are routed into this VPN via addAllowedApplication. All
 * their IPv4/IPv6 traffic goes to the tun interface, and we read and discard
 * every packet, so nothing reaches the real internet. Other apps are untouched.
 *
 * While blocking, their background processes are also killed on a timer so
 * connections opened before the block, and processes the system restarts
 * (push, alarms, sync), don't linger.
 */
class BlockerVpnService : VpnService() {

    private var tun: ParcelFileDescriptor? = null
    private var worker: Thread? = null
    private val handler = Handler(Looper.getMainLooper())
    private var blocked: Set<String> = emptySet()

    private val killTick = object : Runnable {
        override fun run() {
            Killer.killBackground(this@BlockerVpnService, blocked)
            handler.postDelayed(this, KILL_INTERVAL_MS)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopBlocking()
            return START_NOT_STICKY
        }
        startBlocking()
        return START_STICKY
    }

    private fun startBlocking() {
        stopTunnel()

        val selected = Prefs.blockedPackages(this)
        if (selected.isEmpty()) {
            Prefs.setActive(this, false)
            stopSelf()
            return
        }
        val packages = Related.expand(this, selected)

        val builder = Builder()
            .setSession("NetBlocker")
            .addAddress("10.255.0.1", 32)
            .addAddress("fd00:255::1", 128)
            .addRoute("0.0.0.0", 0)
            .addRoute("::", 0)
            .setMtu(1500)
            .setBlocking(true)

        val applied = LinkedHashSet<String>()
        for (pkg in packages) {
            try {
                builder.addAllowedApplication(pkg)
                applied.add(pkg)
            } catch (_: Exception) {
                // App was uninstalled since it was selected; skip it.
            }
        }
        if (applied.isEmpty()) {
            Prefs.setActive(this, false)
            stopSelf()
            return
        }

        val fd = builder.establish()
        if (fd == null) {
            // VPN permission was revoked.
            Prefs.setActive(this, false)
            stopSelf()
            return
        }
        tun = fd
        blocked = applied
        Prefs.setActive(this, true)

        worker = Thread {
            val input = FileInputStream(fd.fileDescriptor)
            val buffer = ByteArray(32767)
            try {
                // Read and drop every packet. No reply is ever written back.
                while (!Thread.currentThread().isInterrupted) {
                    if (input.read(buffer) < 0) break
                }
            } catch (_: Exception) {
                // Interface closed; exit quietly.
            }
        }.also {
            it.name = "netblocker-drain"
            it.start()
        }

        handler.removeCallbacks(killTick)
        if (Prefs.killProcesses(this)) handler.post(killTick)
    }

    private fun stopTunnel() {
        handler.removeCallbacks(killTick)
        worker?.interrupt()
        worker = null
        try {
            tun?.close()
        } catch (_: Exception) {
        }
        tun = null
    }

    private fun stopBlocking() {
        stopTunnel()
        Prefs.setActive(this, false)
        stopSelf()
    }

    override fun onRevoke() {
        // User turned the VPN off from system settings.
        stopBlocking()
        super.onRevoke()
    }

    override fun onDestroy() {
        stopTunnel()
        Prefs.setActive(this, false)
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.mogglestone.hush.STOP"
        private const val KILL_INTERVAL_MS = 15_000L

        fun start(context: Context) {
            context.startService(Intent(context, BlockerVpnService::class.java))
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, BlockerVpnService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}
