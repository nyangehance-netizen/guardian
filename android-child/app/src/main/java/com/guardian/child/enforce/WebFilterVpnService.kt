package com.guardian.child.enforce

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import com.guardian.child.data.PolicyStore
import com.guardian.child.net.ApiClient
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import kotlin.concurrent.thread

/**
 * Local VPN used purely for on-device web filtering (no traffic leaves the phone
 * to any third party). The phone routes its own traffic through this service; we
 * inspect DNS queries and refuse to resolve blocked domains, which is how most
 * commercial parental filters implement category/site blocking without MITM.
 *
 * What is wired up here:
 *   - establishing the TUN interface with VpnService.Builder
 *   - the read loop over the TUN file descriptor
 *   - domain decision via shouldBlock()
 *
 * What a production build must still add (clearly scoped TODOs, not hidden):
 *   - parse IP/UDP headers, extract the DNS question name
 *   - for allowed names: forward the query to a real resolver (e.g. 1.1.1.1) and
 *     write the response back to the TUN fd
 *   - for blocked names: synthesize an NXDOMAIN / 0.0.0.0 answer
 *   - enforce SafeSearch by rewriting answers for google/youtube/bing to their
 *     safe-search VIPs
 * These are mechanical given the loop below; kept out so the starter stays readable.
 */
class WebFilterVpnService : VpnService() {

    @Volatile private var running = false
    private var tun: ParcelFileDescriptor? = null
    private lateinit var store: PolicyStore

    // Simple category -> representative domains map. Real builds load a managed
    // blocklist (e.g. a hosts list) per category and refresh it on sync.
    private val categoryDomains = mapOf(
        "adult" to listOf("pornhub.com", "xvideos.com", "xnxx.com"),
        "gambling" to listOf("bet365.com", "pokerstars.com"),
        "malware" to listOf("known-malware.example"),
    )

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        store = PolicyStore(this)
        if (!running) start()
        return START_STICKY
    }

    private fun start() {
        val builder = Builder()
            .setSession("Guardian web filter")
            .addAddress("10.111.222.1", 24)          // private TUN address
            .addDnsServer("10.111.222.2")            // we answer DNS ourselves
            .addRoute("10.111.222.2", 32)            // only capture DNS to our resolver
        // Exclude ourselves so our outbound resolver forwarding isn't looped.
        runCatching { builder.addDisallowedApplication(packageName) }

        tun = builder.establish() ?: return
        running = true
        thread(name = "guardian-vpn") { loop() }
    }

    private fun loop() {
        val fd = tun ?: return
        val input = FileInputStream(fd.fileDescriptor)
        @Suppress("UNUSED_VARIABLE") val output = FileOutputStream(fd.fileDescriptor)
        val packet = ByteBuffer.allocate(32767)
        try {
            while (running) {
                val n = input.read(packet.array())
                if (n <= 0) continue
                // TODO parse DNS question from packet[0..n]; derive `domain`.
                // Pseudocode of the decision we already support:
                //   val domain = DnsParser.question(packet, n) ?: continue
                //   if (shouldBlock(domain)) { writeNxDomain(output, packet, n); reportBlocked(domain) }
                //   else forwardToUpstream(output, packet, n)
                packet.clear()
            }
        } catch (_: Exception) {
        } finally {
            runCatching { fd.close() }
        }
    }

    /** The core policy decision — fully implemented and unit-testable. */
    fun shouldBlock(domain: String): Boolean {
        val policy = store.policy()
        if (!policy.webFilterEnabled) return false
        val host = domain.lowercase().removePrefix("www.")
        if (policy.allowlist.any { host.endsWith(it.lowercase()) }) return false
        if (policy.blocklist.any { host.endsWith(it.lowercase()) }) return true
        for (cat in policy.blockCategories) {
            if (categoryDomains[cat]?.any { host.endsWith(it) } == true) return true
        }
        return false
    }

    private fun reportBlocked(domain: String) {
        val secret = store.deviceSecret ?: return
        thread { runCatching { ApiClient.reportEvent(secret, "blocked_site", domain) } }
    }

    override fun onDestroy() { running = false; runCatching { tun?.close() }; super.onDestroy() }
}
