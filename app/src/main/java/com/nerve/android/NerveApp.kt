package com.nerve.android

import android.app.Application
import com.nerve.android.domain.channel.InMemoryChannelStore
import com.nerve.android.domain.channel.RealChannelEventProcessor
import com.nerve.android.domain.dm.DmEventMapper
import com.nerve.android.domain.dm.DmSessionManager
import com.nerve.android.domain.server.NerveClientFactory
import com.nerve.android.domain.server.RealServerRegistry
import com.nerve.android.domain.server.ServerConfigStore
import com.nerve.android.domain.server.SharedPrefsServerConfigStore
import com.nerve.android.presentation.channels.ChannelsViewModel
import com.nerve.android.presentation.chat.ChatViewModel
import com.nerve.android.presentation.nodes.NodesViewModel
import com.nerve.android.presentation.server.ServerViewModel
import com.nerve.android.transport.ClientRegistration
import com.nerve.android.transport.RealNerveClient
import com.nerve.android.update.HttpVersionFetcher
import com.nerve.android.update.UpdateChecker
import com.nerve.android.update.UpdateViewModel
import com.nerve.android.util.Logger
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first

data class ResolvedDmEntry(
    val serverId: String,
    val nodeId: String,
    val nodeName: String,
) {
    val key: String
        get() = "$serverId:$nodeId"
}

internal const val APP_VERSION_URL = "http://100.75.43.90/nerve-app-version.json"

class NerveApp : Application() {
    lateinit var configStore: ServerConfigStore
        private set
    lateinit var serverRegistry: RealServerRegistry
        private set
    lateinit var sessionManager: DmSessionManager
        private set
    lateinit var dmEventMapper: DmEventMapper
        private set
    lateinit var channelStore: InMemoryChannelStore
        private set
    lateinit var channelEventProcessor: RealChannelEventProcessor
        private set

    private val startCalls = AtomicInteger(0)
    private val enteredDmKeys = CopyOnWriteArrayList<String>()
    private val subscribedDmKeys = CopyOnWriteArrayList<String>()
    private val assemblyTrace = CopyOnWriteArrayList<String>()
    private val clientFactoryTrace = CopyOnWriteArrayList<String>()
    @Volatile
    private var resolvedEntry: ResolvedDmEntry? = null
    @Volatile
    private var availableEntryKeys: List<String> = emptyList()

    override fun onCreate() {
        super.onCreate()
        Logger.init(this)
        configStore = SharedPrefsServerConfigStore(
            getSharedPreferences(SharedPrefsServerConfigStore.PREFS_NAME, MODE_PRIVATE),
        )
        sessionManager = DmSessionManager()
        dmEventMapper = DmEventMapper()
        channelStore = InMemoryChannelStore()
        channelEventProcessor = RealChannelEventProcessor(channelStore)
        serverRegistry = RealServerRegistry(
            configStore = configStore,
            clientFactory = NerveClientFactory { serverId ->
                clientFactoryTrace += "real:$serverId"
                RealNerveClient()
            },
            scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO),
        )
    }

    fun createChatViewModel(): ChatViewModel =
        ChatViewModel(
            sessionManager = sessionManager,
            mapper = dmEventMapper,
            serverRegistry = serverRegistry,
            dispatcher = Dispatchers.Main.immediate,
            onSubscribed = { serverId, nodeId ->
                subscribedDmKeys += "$serverId:$nodeId"
                assemblyTrace += "client:subscribe:$serverId:$nodeId"
            },
        )

    fun createNodesViewModel(): NodesViewModel =
        NodesViewModel(
            serverRegistry = serverRegistry,
            dispatcher = Dispatchers.Main.immediate,
        )

    fun createChannelsViewModel(): ChannelsViewModel =
        ChannelsViewModel(
            channelStore = channelStore,
            channelEventProcessor = channelEventProcessor,
            serverRegistry = serverRegistry,
            dispatcher = Dispatchers.Main.immediate,
        )

    fun createServerViewModel(): ServerViewModel =
        ServerViewModel(
            serverRegistry = serverRegistry,
            dispatcher = Dispatchers.Main.immediate,
        )

    fun createUpdateViewModel(): UpdateViewModel =
        UpdateViewModel(
            checker = UpdateChecker(
                currentVersionCode = BuildConfig.VERSION_CODE,
                fetchPayload = HttpVersionFetcher(versionUrl = APP_VERSION_URL),
            ),
            dispatcher = Dispatchers.Main.immediate,
        )

    suspend fun ensureStarted() {
        if (startCalls.compareAndSet(0, 1)) {
            assemblyTrace += "start:begin"
            serverRegistry.start(ClientRegistration(name = "android-ui"))
            resolveEntry()
            assemblyTrace += "start:end"
        }
    }

    fun recordEnteredDm(serverId: String, nodeId: String) {
        enteredDmKeys += "$serverId:$nodeId"
        assemblyTrace += "chatRoute:enterDm:$serverId:$nodeId"
    }

    fun debugStartCalls(): Int = startCalls.get()

    fun debugEnteredDmKeys(): List<String> = enteredDmKeys.toList()

    fun debugSubscribedDmKeys(): List<String> = subscribedDmKeys.toList()

    fun debugAssemblyTrace(): List<String> = assemblyTrace.toList()

    fun debugClientFactoryTrace(): List<String> = clientFactoryTrace.toList()

    fun resolvedEntry(): ResolvedDmEntry? = resolvedEntry

    fun debugResolvedEntryKey(): String? = resolvedEntry?.key

    fun debugAvailableEntryKeys(): List<String> = availableEntryKeys

    private suspend fun resolveEntry() {
        val nodes = serverRegistry.nodes.first()
        availableEntryKeys = nodes.map { "${it.serverId}:${it.node.id}" }
        resolvedEntry = nodes.firstOrNull()?.let { node ->
            ResolvedDmEntry(
                serverId = node.serverId,
                nodeId = node.node.id,
                nodeName = node.node.name,
            )
        }
        assemblyTrace += "entry:resolved:${resolvedEntry?.key ?: "none"}"
    }
}
