// JSON shapes of the go-data API (internal/domain and internal/api in go-data).
// Only the fields the app shows. Every field has a default, and Json below maps
// null to that default (Go marshals a nil slice as null) and ignores unknown keys,
// so a field added or removed server-side never breaks decoding.
package ar.godata.monitor

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy

@OptIn(ExperimentalSerializationApi::class)
val json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    namingStrategy = JsonNamingStrategy.SnakeCase
}

@Serializable
data class Stats(
    val cpu: Cpu = Cpu(),
    val ram: Ram = Ram(),
    val swap: Swap = Swap(),
    val load: Load = Load(),
    val disks: List<Disk> = emptyList(),
    val diskIo: List<DiskIo> = emptyList(),
    val net: List<NetIface> = emptyList(),
    val temp: List<Temp> = emptyList(),
    val sockets: Sockets = Sockets(),
    val processes: List<Process> = emptyList(),
) {
    val primaryTemp: Temp? get() = temp.firstOrNull { it.isPrimaryCpu } ?: temp.firstOrNull()
    val rootDisk: Disk? get() = disks.firstOrNull { it.mount == "/" }
}

@Serializable
data class Cpu(
    val usagePct: Double = 0.0,
    val userPct: Double = 0.0,
    val systemPct: Double = 0.0,
    val iowaitPct: Double = 0.0,
    val stealPct: Double = 0.0,
)

@Serializable
data class Ram(
    val totalKb: Long = 0,
    val usedKb: Long = 0,
    val availableKb: Long = 0,
    val freeKb: Long = 0,
    val cachedKb: Long = 0,
    val usedPct: Double = 0.0,
)

@Serializable
data class Swap(val usedKb: Long = 0, val usedPct: Double = 0.0, val present: Boolean = false)

@Serializable
data class Load(
    val load1: Double = 0.0,
    val load5: Double = 0.0,
    val load15: Double = 0.0,
    val normalizedPct: Double = 0.0,
)

@Serializable
data class Disk(
    val mount: String = "",
    val fsType: String = "",
    val totalBytes: Long = 0,
    val usedBytes: Long = 0,
    val availBytes: Long = 0,
    val usedPct: Double = 0.0,
)

@Serializable
data class DiskIo(
    val readBps: Double = 0.0,
    val writeBps: Double = 0.0,
    val readIops: Double = 0.0,
    val writeIops: Double = 0.0,
    val latencyMs: Double = 0.0,
)

@Serializable
data class NetIface(
    val name: String = "",
    val rxBps: Double = 0.0,
    val txBps: Double = 0.0,
    val packetsPs: Double = 0.0,
    val errorsPs: Double = 0.0,
    val dropsPs: Double = 0.0,
)

@Serializable
data class Temp(val celsius: Double = 0.0, val isPrimaryCpu: Boolean = false)

@Serializable
data class Sockets(
    val tcpInUse: Int = 0,
    val tcpOrphan: Int = 0,
    val tcpTimeWait: Int = 0,
    val udpInUse: Int = 0,
    val totalUsed: Int = 0,
)

@Serializable
data class Process(val pid: Int = 0, val name: String = "", val cpuPct: Double = 0.0)

@Serializable
data class HostInfo(
    val hostname: String = "",
    val osName: String = "",
    val osVersion: String = "",
    val kernelVersion: String = "",
    val architecture: String = "",
    val virtualization: String = "",
    val coresTotal: Int = 0,
    val bootTime: String = "",
    val cpuModel: String = "",
    val ramTotalKb: Long = 0,
    val disks: List<PhysicalDisk> = emptyList(),
    val usbDevices: List<UsbDevice> = emptyList(),
)

@Serializable
data class PhysicalDisk(val device: String = "", val model: String = "", val sizeBytes: Long = 0, val kind: String = "")

@Serializable
data class UsbDevice(
    val vendorId: String = "",
    val productId: String = "",
    val manufacturer: String = "",
    val product: String = "",
)

@Serializable
data class Alarms(val alarms: List<Alarm> = emptyList())

@Serializable
data class Alarm(val severity: String = "", val message: String = "", val since: String = "")

@Serializable
data class UptimeSessions(val sessions: List<UptimeSession> = emptyList())

@Serializable
data class UptimeSession(val start: String = "", val durationSeconds: Double = 0.0, val ongoing: Boolean = false)

@Serializable
data class Containers(val containers: List<Container> = emptyList())

@Serializable
data class Container(
    val name: String = "",
    val image: String = "",
    val state: String = "",
    val status: String = "",
    val health: String = "",
    val restartCount: Int = 0,
    val portChips: List<PortChip> = emptyList(),
    val cpuPct: Double = 0.0,
    val memUsedBytes: Long = 0,
    val memPct: Double = 0.0,
    val netRxBps: Double = 0.0,
    val netTxBps: Double = 0.0,
)

@Serializable
data class PortChip(val label: String = "", val published: Boolean = false)

@Serializable
data class NetSummary(
    val enabled: Boolean = false,
    val capability: String = "",
    val activeTotal: Int = 0,
    val activeTcp: Int = 0,
    val activeUdp: Int = 0,
    val uniqueIps: Int = 0,
    val inbound: Int = 0,
    val outbound: Int = 0,
    val outboundEnabled: Boolean = false,
)

@Serializable
data class NetConnections(val connections: List<NetFlow> = emptyList())

@Serializable
data class NetFlow(
    val remoteIp: String = "",
    val remotePort: Int = 0,
    val localPort: Int = 0,
    val proto: String = "",
    val direction: String = "", // inbound | outbound
    val state: String = "",
    val since: String = "",
)

@Serializable
data class NetTop(val items: List<NetCount> = emptyList())

@Serializable
data class NetCount(val key: String = "", val count: Int = 0)

@Serializable
data class NetEvents(val events: List<NetFlowEvent> = emptyList())

@Serializable
data class NetFlowEvent(
    val time: String = "",
    val remoteIp: String = "",
    val localPort: Int = 0,
    val proto: String = "",
)

@Serializable
data class NetIpHistory(val entries: List<NetIpEntry> = emptyList())

@Serializable
data class NetIpEntry(val ip: String = "", val lastSeen: String = "", val connections: Long = 0)

@Serializable
data class SshEvents(val enabled: Boolean = false, val events: List<SshEvent> = emptyList())

@Serializable
data class SshEvent(
    val time: String = "",
    val ip: String = "",
    val user: String = "",
    val action: String = "", // accepted | failed | invalid_user | closed
    val success: Boolean = false,
)

@Serializable
data class ConnEvents(val enabled: Boolean = false, val events: List<ConnEvent> = emptyList())

@Serializable
data class ConnEvent(val time: String = "", val ip: String = "", val localPort: Int = 0)

@Serializable
data class History(val points: List<HistoryPoint> = emptyList())

@Serializable
data class HistoryPoint(val time: Long = 0, val value: Double = 0.0)
