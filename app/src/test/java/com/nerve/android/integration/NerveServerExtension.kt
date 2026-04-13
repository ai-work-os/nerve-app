package com.nerve.android.integration

import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext
import java.io.File
import java.net.ServerSocket

/**
 * JUnit 5 extension that starts a real nerve server before all tests and stops it after.
 *
 * - Picks a random available port
 * - Uses a temp data directory (cleaned up after)
 * - Locates the nerve project via NERVE_SERVER_DIR env var, system property, or well-known paths
 */
class NerveServerExtension : BeforeAllCallback, AfterAllCallback {

    var port: Int = 0
        private set

    val wsUrl: String get() = "ws://localhost:$port"

    private var process: Process? = null
    private var dataDir: File? = null

    override fun beforeAll(context: ExtensionContext) {
        val nerveDir = findNerveDir()
        Assumptions.assumeTrue(nerveDir != null, "nerve server directory not found")
        Assumptions.assumeTrue(
            File(nerveDir!!, "src/cli.ts").exists(),
            "nerve cli.ts not found at ${nerveDir.absolutePath}"
        )

        val npxCheck = try {
            ProcessBuilder("npx", "--version")
                .redirectErrorStream(true)
                .start()
                .also { it.waitFor() }
                .exitValue() == 0
        } catch (_: Exception) { false }
        Assumptions.assumeTrue(npxCheck, "npx not available")

        port = findFreePort()
        dataDir = File("/tmp/nerve-test-$port").also { it.mkdirs() }

        val pb = ProcessBuilder(
            "npx", "tsx", "src/cli.ts", "serve",
            "--port", port.toString(),
            "--data", dataDir!!.absolutePath,
            "--no-guardian",
        )
        pb.directory(nerveDir)
        pb.redirectErrorStream(true)
        pb.environment()["PATH"] = System.getenv("PATH") ?: "/usr/local/bin:/usr/bin:/bin"

        process = pb.start()

        // Drain stdout to prevent pipe buffer blocking
        Thread({
            process!!.inputStream.bufferedReader().use { r -> r.lines().forEach { } }
        }, "nerve-server-drain").apply { isDaemon = true; start() }

        // Wait for server to be ready (up to 15s)
        val deadline = System.currentTimeMillis() + 15_000
        var ready = false
        while (System.currentTimeMillis() < deadline) {
            if (!process!!.isAlive) {
                throw IllegalStateException("nerve server exited with code ${process!!.exitValue()}")
            }
            try {
                java.net.Socket("localhost", port).use { ready = true }
                break
            } catch (_: Exception) {
                Thread.sleep(200)
            }
        }
        if (!ready) {
            process!!.destroyForcibly()
            throw IllegalStateException("nerve server did not start within 15s on port $port")
        }
    }

    override fun afterAll(context: ExtensionContext) {
        process?.let { it.destroyForcibly(); it.waitFor() }
        process = null
        dataDir?.deleteRecursively()
        dataDir = null
    }

    private fun findNerveDir(): File? {
        System.getenv("NERVE_SERVER_DIR")?.let { File(it) }?.takeIf { it.isDirectory }?.let { return it }
        System.getProperty("nerve.server.dir")?.let { File(it) }?.takeIf { it.isDirectory }?.let { return it }

        val cwd = File(System.getProperty("user.dir") ?: ".")
        for (rel in listOf("../nerve", "../../nerve")) {
            val f = File(cwd, rel).canonicalFile
            if (f.isDirectory && File(f, "src/cli.ts").exists()) return f
        }

        val home = System.getProperty("user.home")
        for (path in listOf("$home/work/worktree/ai-work-os/nerve", "$home/work/ai-work-os/nerve")) {
            val f = File(path)
            if (f.isDirectory && File(f, "src/cli.ts").exists()) return f
        }
        return null
    }

    companion object {
        fun findFreePort(): Int = ServerSocket(0).use { it.localPort }
    }
}
