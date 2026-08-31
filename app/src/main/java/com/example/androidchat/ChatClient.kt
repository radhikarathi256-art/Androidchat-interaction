                                    package com.example.androidchat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlin.math.pow

// Supabase Realtime over a plain WebSocket.
// Wire contract shared with the iOS seeker app and the web console:
//   topic          realtime:chat:demo
//   presence key   equals the role name ("seeker" | "astrologer")
//   broadcast      msg | typing | read
//   msg payload    { id, kind, from, ts, text?, imageUrl?, replyToId? }

private val SUPABASE_HOST = BuildConfig.SUPABASE_HOST
private const val ROOM = "demo"

// Credentials come from local.properties via BuildConfig (gitignored),
// so this file is safe to publish. See README for setup.
private val SUPABASE_KEY = BuildConfig.SUPABASE_KEY

enum class Role { seeker, astrologer }

// This app is the astrologer. Flip these two lines to turn it into a seeker.
private val MY_ROLE = Role.astrologer
private val PEER_ROLE = Role.seeker

enum class Sender { seeker, astrologer, system }
enum class Kind { text, voice, image }

/**
 * Declared in order, so the compiler-generated `compareTo` gives us "may only
 * move forward" for free. Acks race — a `read` can overtake its own
 * `delivered` — and a tick sliding backwards is a visible regression.
 * [ChatClient.advance] is the only writer and it enforces this.
 */
enum class DeliveryStatus { sending, sent, delivered, read }

data class Message(
    val id: String,
    val kind: Kind = Kind.text,
    val from: Sender,
    val text: String? = null,
    val imageUrl: String? = null,
    val ts: Long = System.currentTimeMillis(),
    /** Only meaningful on our own bubbles; incoming and system rows draw no ticks. */
    val status: DeliveryStatus = DeliveryStatus.sent,
    val isAdmin: Boolean = false,
    val replyToId: String? = null,
)

class ChatClient : ViewModel() {

    /** Which side of the conversation this app is, so the UI knows which bubbles are ours. */
    val mySender: Sender = Sender.valueOf(MY_ROLE.name)

    val msgs = mutableStateListOf<Message>()
    var peerTyping by mutableStateOf(false); private set
    var peerRecording by mutableStateOf(false); private set

    /**
     * True only while a message is taking the typing indicator's place. The dots
     * have to vanish in one frame in that case — a row being removed keeps its
     * layout space until its exit finishes, so an animated exit means the dying
     * dots and the arriving bubble both claim room for a moment, shoving the
     * conversation up and then dragging it back down. Every other time the dots
     * go away nothing is competing for that space, and an instant vanish is pure
     * loss.
     */
    var typingHandoff by mutableStateOf(false); private set
    var joined by mutableStateOf(false); private set
    var connected by mutableStateOf(false); private set

    private val http = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private var ws: WebSocket? = null
    private var ref = 0
    private var retries = 0
    private var didTrack = false
    private var closedByUs = false
    private var typingSent = false
    private var heartbeat: Job? = null

    private val topic get() = "realtime:chat:$ROOM"

    // MARK: Connect

    fun connect() {
        closedByUs = false
        didTrack = false
        heartbeat?.cancel()
        ws?.cancel()

        val url = "wss://$SUPABASE_HOST/realtime/v1/websocket" +
                "?apikey=$SUPABASE_KEY&vsn=1.0.0"
        ws = http.newWebSocket(Request.Builder().url(url).build(), listener)

        heartbeat = viewModelScope.launch {
            while (true) {
                delay(25_000)
                sendRaw("phoenix", "heartbeat", JSONObject())
            }
        }
    }

    fun disconnect() {
        closedByUs = true
        heartbeat?.cancel()
        heartbeat = null
        ws?.close(1000, null)
        connected = false
    }

    /**
     * Called on every foreground. The OS tears the socket down while the app is
     * away and by the time the user returns the backoff in [scheduleReconnect] has
     * usually saturated at its 30s cap, so the chat sat dead for up to half a
     * minute. Deliberately unconditional: `connected` still reads a stale `true`
     * after a suspend, because the failure callback never got a chance to run.
     * [connect] drops any existing socket first, so calling this when we happen to
     * be healthy costs one quick rejoin.
     */
    fun reconnect() {
        retries = 0
        connect()
    }

    private fun join() {
        sendRaw(topic, "phx_join", JSONObject().apply {
            put("config", JSONObject().apply {
                put("broadcast", JSONObject().put("self", false))
                // enabled=true is required to receive presence_state on join.
                // Without it we only get presence_diff, so a peer who was already
                // connected before us is never seen.
                put("presence", JSONObject().put("key", MY_ROLE.name).put("enabled", true))
            })
        })
    }

    /**
     * Passing presence.key in the join config does NOT put us in presenceState().
     * Supabase only registers us after an explicit track frame — without this the
     * other side sits on "waiting" forever.
     */
    private fun trackPresence() {
        sendRaw(topic, "presence", JSONObject().apply {
            put("type", "presence")
            put("event", "track")
            put("payload", JSONObject().put("role", MY_ROLE.name))
        })
    }

    private fun scheduleReconnect() {
        if (closedByUs) return
        closedByUs = true          // stop this dead socket queueing more retries
        heartbeat?.cancel()
        connected = false
        retries += 1
        val delaySec = min(2.0.pow(retries), 30.0)
        viewModelScope.launch {
            delay((delaySec * 1000).toLong())
            connect()
        }
    }

    // MARK: Send

    fun send(text: String, replyToId: String? = null) {
        val id = UUID.randomUUID().toString()
        msgs.add(
            Message(
                id = id,
                from = Sender.valueOf(MY_ROLE.name),
                text = text,
                status = DeliveryStatus.sending,
                replyToId = replyToId,
            )
        )
        val queued = sendRaw(topic, "broadcast", JSONObject().apply {
            put("type", "broadcast")
            put("event", "msg")
            put("payload", JSONObject().apply {
                put("id", id)
                put("kind", "text")
                put("from", MY_ROLE.name)
                put("ts", System.currentTimeMillis())
                put("text", text)
                if (replyToId != null) put("replyToId", replyToId)
            })
        })
        // Handed to an open socket = Sent. If the socket is down the tick
        // correctly stays faint on Sending.
        if (queued) advance(id, DeliveryStatus.sent)
        setTyping(false)
    }

    /**
     * The only writer of [Message.status], and it only ever moves forward.
     *
     * Replaces the element in place so the row keeps its identity and the
     * LazyColumn reuses the same node (§15) — the bubble must not be rebuilt
     * when a tick changes. Nothing here touches geometry.
     */
    private fun advance(id: String, status: DeliveryStatus) {
        val i = msgs.indexOfFirst { it.id == id }
        if (i < 0 || msgs[i].status >= status) return
        msgs[i] = msgs[i].copy(status = status)
    }

    /**
     * Tells the sender their message landed. `delivered` on arrival, `read`
     * once it is in the list — they land within a frame of each other.
     */
    private fun ack(event: String, id: String) {
        sendRaw(topic, "broadcast", JSONObject().apply {
            put("type", "broadcast")
            put("event", event)
            put("payload", JSONObject().put("id", id))
        })
    }

    fun setTyping(on: Boolean) {
        if (!connected || typingSent == on) return
        typingSent = on
        sendRaw(topic, "broadcast", JSONObject().apply {
            put("type", "broadcast")
            put("event", "typing")
            put("payload", JSONObject().apply {
                put("on", on)
                put("mode", "text")
            })
        })
    }

    /**
     * Returns true once the frame is handed to an open socket — that is what
     * turns a bubble from Sending into Sent. If the socket is down it returns
     * false and the tick correctly stays faint.
     */
    private fun sendRaw(topic: String, event: String, payload: JSONObject): Boolean {
        ref += 1
        val frame = JSONObject().apply {
            put("topic", topic)
            put("event", event)
            put("payload", payload)
            put("ref", ref.toString())
        }
        return ws?.send(frame.toString()) ?: false
    }

    // MARK: Receive

    // Every callback ignores sockets that are no longer the current one. Without
    // this, the `ws?.cancel()` inside [connect] fires onFailure on the socket we
    // just discarded, which then queues its own reconnect — so one foreground
    // could leave two live sockets racing to join the same topic.
    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            if (webSocket !== ws) return
            join()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (webSocket !== ws) return
            viewModelScope.launch { handle(text) }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (webSocket !== ws) return
            viewModelScope.launch { scheduleReconnect() }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (webSocket !== ws) return
            viewModelScope.launch { scheduleReconnect() }
        }
    }

    private fun handle(raw: String) {
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return
        val rootEvent = root.optString("event")
        val outer = root.optJSONObject("payload") ?: JSONObject()

        when (rootEvent) {
            "phx_reply" -> {
                // Heartbeat replies arrive on the "phoenix" topic — ignore those.
                if (root.optString("topic") != topic) return
                if (outer.optString("status") != "ok") return
                retries = 0
                connected = true
                if (!didTrack) {
                    didTrack = true
                    trackPresence()
                }
                return
            }

            "presence_state", "presence_diff" -> {
                val src = if (rootEvent == "presence_state") outer
                else outer.optJSONObject("joins") ?: JSONObject()
                val keys = src.keys()
                while (keys.hasNext()) {
                    if (keys.next() == PEER_ROLE.name) {
                        markJoined()
                        break
                    }
                }
                return
            }

            "phx_error", "phx_close" -> {
                scheduleReconnect()
                return
            }

            "broadcast" -> Unit

            else -> return
        }

        val event = outer.optString("event")
        val body = outer.optJSONObject("payload") ?: JSONObject()

        when (event) {
            "msg" -> {
                markJoined()
                val id = body.optString("id").ifEmpty { UUID.randomUUID().toString() }
                val kind = runCatching { Kind.valueOf(body.optString("kind", "text")) }
                    .getOrDefault(Kind.text)
                // Set before the removal so the typing row is already carrying the
                // instant exit by the time it leaves, and cleared once the slide is
                // over so an ordinary "stopped typing" still fades out gently.
                typingHandoff = peerTyping || peerRecording
                peerTyping = false
                peerRecording = false
                if (typingHandoff) {
                    viewModelScope.launch {
                        delay(600)
                        typingHandoff = false
                    }
                }
                if (msgs.none { it.id == id }) {
                    msgs.add(
                        Message(
                            id = id,
                            kind = kind,
                            from = Sender.valueOf(PEER_ROLE.name),
                            text = if (body.isNull("text")) null else body.optString("text"),
                            imageUrl = if (body.isNull("imageUrl")) null else body.optString("imageUrl"),
                            replyToId = if (body.isNull("replyToId")) null else body.optString("replyToId"),
                        )
                    )
                }
                ack("delivered", id)
                ack("read", id)
            }

            "typing" -> {
                markJoined()
                val on = body.optBoolean("on", false)
                val mode = body.optString("mode", "text")
                peerTyping = on && mode == "text"
                peerRecording = on && mode == "voice"
            }

            "delivered" -> advance(body.optString("id"), DeliveryStatus.delivered)

            "read" -> advance(body.optString("id"), DeliveryStatus.read)
        }
    }

    private fun markJoined() {
        if (joined) return
        joined = true
        msgs.add(
            Message(
                id = "joined",
                from = Sender.system,
                text = "Seeker has joined the chat",
                isAdmin = true,
            )
        )
    }
}
