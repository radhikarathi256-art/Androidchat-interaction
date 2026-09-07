package com.example.androidchat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// MARK: - Palette (shared with the SwiftUI seeker app)

private val InkDark = Color(0xFF1D2939)
private val SentBg = Color(0xFFFCE1D7)
private val ChatBg = Color(0xFFF0F5FF)
private val Muted = Color(0xFF98A2B3)
private val Subtle = Color(0xFF667085)
private val PillBg = Color(0xFFF2F5F7)
private val LiveGreen = Color(0xFF039954)
private val SendOrange = Color(0xFFF06938)
private val PillStroke = Color(0xFFD1D6DE)
private val PillText = Color(0xFF334054)
private val EndRed = Color(0xFFD92E21)
private val PanelCard = Color(0xFFFDF0EB)
private val PanelStroke = Color(0xFFF9C3B0)
private val StarGold = Color(0xFFFFD029)

private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { ChatScreen() }
    }
}

// The square corner is the TAIL, and it sits at the bottom on both sides, so
// each bubble grows up and inward out of its own tail. The received bubble used
// to carry its square corner at the TOP-start, which meant it grew away from
// its tail while mine grew out of it.
private fun bubbleShape(isMine: Boolean) = RoundedCornerShape(
    topStart = 16.dp,
    topEnd = 16.dp,
    bottomEnd = if (isMine) 0.dp else 16.dp,
    bottomStart = if (isMine) 16.dp else 0.dp,
)

// ============================================================================
// MOTION TEST FLAG — the only line to edit. Mirrors iOS FLAG_EASE.
// `true` = decelerate tweens (what WhatsApp actually uses). `false` = springs.
//
// Springs were the wrong call. A spring settles by ringing, and rows with
// different distances to travel ring out of phase with one another, so a reflow
// reads as a dozen bubbles each wobbling on their own clock. A decelerate tween
// has no overshoot: every row starts and stops on the same frame and the whole
// block moves as one piece.
//
// LinearOutSlowIn, not FastOutSlowIn. FastOutSlowIn eases in AND out, so
// content creeps for the first few frames before it commits — that soft start
// is what makes motion feel mushy. Entering content should leave immediately
// and only decelerate at the end.
//
// There is no Android twin of iOS FLAG_SIZE_ANCHOR: this list is a
// reverseLayout LazyColumn, which is pinned to the bottom by construction.
// ============================================================================
private const val FLAG_EASE = true

/**
 * How long a pause counts as "stopped typing" (§3). Same number on iOS.
 */
private const val TYPING_IDLE_MS = 1_200L

// Two tokens, split by blast radius, and one spec for both directions of any
// given event so enter and exit travel the same distance at the same rate (#2).

/**
 * `layout` — anything whose motion moves OTHER content. Critically damped:
 * overshoot here is what reads as the whole screen bouncing when a message
 * lands. iMessage never bounces, it just settles.
 *
 * 400ms, not 260ms. 260 is about as fast as a curve can be while still being
 * legible, and at that speed a reflow reads as a flinch however clean it is —
 * there is no time to see anything travel, only to notice that it moved. The eye
 * reads a move as deliberate from roughly 350ms. iMessage sits at 350-400 and
 * that is the whole difference between "it jumped" and "it slid".
 */
/**
 * Decelerate, but gentler off the line than the platform's own
 * `LinearOutSlowInEasing`, which is (0, 0, 0.2, 1). Both are pure ease-outs —
 * no ease-in, monotonic decay — but near t=0 a (0, 0, x2, 1) curve has slope
 * 1/x2, so 0.2 leaves at 5x the average speed of the move and 0.5 leaves at 2x.
 * Measured on iOS with the identical curve, on the longest travel in the chat
 * (a typing indicator appearing, ~127px), 0.2 put 17% of the whole distance
 * into the first frame — a step the eye reads as a jump however clean the
 * remaining 24 frames are. At 0.5 the first frame is ~7%.
 */
private val Decelerate = CubicBezierEasing(0f, 0f, 0.5f, 1f)

// 460. The number moved three times before landing here, and each move rules
// out a wrong explanation, so the history is worth keeping:
//
//   480 — on the theory that Android needs more room than iOS's 420 because
//         each row animates on its own rather than reflowing as one VStack.
//         That holds only for SENT messages, where the list travels a full
//         bubble height.
//   320 — because a RECEIVED message travels much less: the typing dots already
//         occupied the space, so the net movement is bubble height minus pill
//         height, roughly a third as far. The same 480ms over a third of the
//         distance is a third of the speed, and slow short travel does not read
//         as smooth, it reads as floating. That float is what gets described as
//         a spring, and there is no spring in this file.
//   420 — iOS's layoutSpring duration exactly, once the brief became parity.
//   460 — 420 taken 10% slower, judged by eye on device, because iOS's own
//         number still read as hurried here.
//
// The duration is shared between the bubble's grow and the neighbours' slide,
// so it has to suit the shorter of the two journeys.
//
// It also lands the fade for free: alpha in appearModifier is derived from this
// animation's own progress and completes at p = 0.40, which on
// cubic-bezier(0, 0, 0.5, 1) is t = 0.238 -> 0.238 x 460ms = 110ms, matching
// iOS's hardcoded 110ms opacity transition almost exactly. Change this duration
// and the fade silently drifts away from iOS.
private val LayoutSpring: FiniteAnimationSpec<Float> =
    if (FLAG_EASE) tween(460, easing = Decelerate)
    else spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

/**
 * `enter` — small elements that move nothing but themselves: a bubble's own
 * scale and alpha, the typing dots' own scale, the scroll-to-bottom pill. 0.80
 * leaves a couple of pixels of overshoot at the 0.85 entrance scale, which reads
 * as life rather than wobble. Only safe because nothing is laid out against it,
 * so never use it for a size or placement animation.
 */
private val EnterSpring: FiniteAnimationSpec<Float> =
    if (FLAG_EASE) tween(280, easing = Decelerate)
    else spring(
        dampingRatio = 0.80f,
        stiffness = Spring.StiffnessMediumLow,
    )

/**
 * `layout`, for placement. #3 the one that stops the jump: a new row claims its
 * full height on the very first frame, so without this every older message
 * teleports upward by that height and only then does the bubble scale. Placement
 * animation makes the neighbours slide to their new positions instead.
 */
private val PlacementSpring: FiniteAnimationSpec<IntOffset> =
    if (FLAG_EASE) tween(460, easing = Decelerate)
    else spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
        visibilityThreshold = IntOffset.VisibilityThreshold,
    )

/**
 * #4 bubbles scale in rather than popping, and #2 the motion is symmetric —
 * the same scale drives both directions.
 *
 * `animate = false` renders at rest. A LazyColumn throws away offscreen rows and
 * rebuilds them on the way back, which re-ran this animation every time an old
 * bubble scrolled into view — the chat appeared to twitch while scrolling.
 */
/**
 * The entrance. ONE entrance, used by every bubble — sent or received.
 *
 * Width only, sweeping out of the tail, height correct from frame one. A uniform
 * scale grows the bubble diagonally so its top edge travels upward the whole
 * time, which reads as "growing from the top". Worse, a graphicsLayer scale is
 * paint-only — the row claims its FULL height on frame one regardless — so the
 * conversation above jumps up immediately while the bubble is still short,
 * leaving a gap the bubble then spends the whole animation climbing into.
 * Holding scaleY at 1 removes both problems: only one axis moves.
 *
 * A separate, larger start for the bubble that replaces the typing dots was
 * tried and reverted. Scaling height as well as width to match the pill's
 * footprint reintroduced exactly the "grows from the top" look, and it made
 * receiving animate differently from sending when sending is the reference.
 * Receiving is now the same code on the same clock, which means anything that
 * still reads wrong on receiving belongs to the dots' exit — a different row,
 * in a different list slot, driven by a different animator.
 */
/**
 * ONE entrance, identical for sent and received. Do not special-case the
 * message that replaces the typing dots.
 *
 * Starting that message at the pill's 59x31dp footprint, opaque from frame one,
 * so the dots appear to BECOME the bubble, has now been tried twice and judged
 * worse both times — once with a width-only grow, and again after the transform
 * origin was corrected to the bubble's own tail corner. Fixing the anchor did
 * not rescue it. Whatever is left on the receiving side, a bigger starting size
 * is not the answer; the remaining suspect is the dots' exit, which is a
 * different row in a different list slot on a different animator.
 */
@Composable
private fun appearModifier(isMine: Boolean, animate: Boolean): Modifier {
    if (!animate) return Modifier
    // LayoutSpring, not EnterSpring. The bubble's own grow and the neighbours'
    // slide are one event and have to be one curve — on two specs they ended
    // 40ms apart, and a bubble that finishes settling while the rows around it
    // are still moving is read as a hitch even though both curves are clean.
    //
    // An Animatable, not `animateFloatAsState` driven off a boolean flipped in a
    // LaunchedEffect. That pattern costs two frames before anything is drawn —
    // one to compose with the flag still false, one more for the state change to
    // retarget the animation. An Animatable is already at its start value when
    // the first frame is composed, so frame one draws the grow's first step.
    val anim = remember { Animatable(0f) }
    LaunchedEffect(Unit) { anim.animateTo(1f, LayoutSpring) }
    val p = anim.value
    return Modifier.graphicsLayer {
        // Uniform, both axes, 0 -> 1 — iOS's `.scale(scale: 0, anchor:)`.
        // This was width-only (scaleY pinned at 1) to dodge the "grows from the
        // top" look described above. That dodge is now gone because the brief is
        // to match iOS, and iOS grows both axes. If the top-edge climb comes
        // back, the cause is the row claiming its full height on frame one while
        // the bubble is still short — fix that at the row, not by flattening an
        // axis here, or the two platforms diverge again.
        scaleX = p
        scaleY = p
        alpha = (p / 0.40f).coerceIn(0f, 1f)
        transformOrigin = TransformOrigin(if (isMine) 1f else 0f, 1f)
    }
}

@Composable
private fun Appear(
    isMine: Boolean,
    animate: Boolean,
    content: @Composable () -> Unit,
) {
    Box(appearModifier(isMine, animate)) { content() }
}

// `WindowInsets.imeAnimationTarget` is still marked experimental. It is the only
// way to learn where the keyboard is heading rather than where it currently is,
// which is what lets the composer run our own curve instead of the OEM's.
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatScreen(chat: ChatClient = viewModel()) {
    var draft by remember { mutableStateOf("") }
    var replyTo by remember { mutableStateOf<Message?>(null) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    // ON_START is the only place that connects — it replays immediately when the
    // observer is attached, so this covers first launch as well as every return
    // from the background. The OS kills the socket while the app is away, and the
    // exponential backoff alone left the chat dead for up to 30s after reopening.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) chat.reconnect()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // §3 typing debounce. This effect only runs when `draft` CHANGES, so a bare
    // `setTyping(draft.isNotBlank())` could never express "stopped typing" —
    // stopping is the absence of a change. The peer kept seeing dots until the
    // message was sent or the field was cleared. Restarting the effect cancels
    // the pending delay, so the 1.2s elapses only once typing actually stops.
    LaunchedEffect(draft) {
        if (draft.isBlank()) {
            chat.setTyping(false)
            return@LaunchedEffect
        }
        chat.setTyping(true)
        delay(TYPING_IDLE_MS)
        chat.setTyping(false)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
    ) {
        Header(chat)

        MessageList(
            chat = chat,
            modifier = Modifier.weight(1f),
            onReply = { replyTo = it },
            onTapEmptyArea = {
                focus.clearFocus()
                keyboard?.hide()
            },
        )

        // #6 one inset for the whole bottom stack. Previously the outer Column
        // had imePadding() while the composer also had navigationBarsPadding() —
        // the keyboard height already contains the nav bar, so both grew at once
        // and the bar visibly double-stepped. The union moves it exactly once,
        // on the keyboard's own curve.
        // Eased, rather than tracking the IME pixel for pixel.
        //
        // `WindowInsets.ime` reports the keyboard's live position, and following
        // it welds the composer to the keys — measured, the composer and the list
        // moved in byte-identical steps. Correct, but it inherits the keyboard's
        // own curve, and Samsung's is ~130ms that ramps up for four frames before
        // it commits. `imeAnimationTarget` reports where the keyboard is going
        // rather than where it is, so driving the padding from that through our
        // own Decelerate curve gives the composer a real ease at a duration we
        // choose. 260ms is deliberately close to the keyboard's own so the two
        // never visibly separate — the content leads the keys by a few frames at
        // the start and that is the whole cost.
        //
        // To go back to pixel-locked, use `WindowInsets.ime` in place of
        // `imeAnimationTarget` and drop the animateIntAsState.
        val density = LocalDensity.current
        val imeTarget = WindowInsets.imeAnimationTarget
            .union(WindowInsets.navigationBars)
            .getBottom(density)
        val bottomPad by animateIntAsState(
            targetValue = imeTarget,
            animationSpec = tween(260, easing = Decelerate),
            label = "imePad",
        )
        Column(
            Modifier.padding(bottom = with(density) { bottomPad.toDp() })
        ) {
            ReplyHud(replyTo) { replyTo = null }

            Composer(
                draft = draft,
                onDraftChange = { draft = it },
                onSend = {
                    val t = draft.trim()
                    if (t.isNotEmpty()) {
                        draft = ""
                        chat.send(t, replyTo?.id)
                        replyTo = null
                    }
                },
            )
        }
    }
}

@Composable
private fun MessageList(
    chat: ChatClient,
    modifier: Modifier = Modifier,
    onReply: (Message) -> Unit,
    onTapEmptyArea: () -> Unit,
) {
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // reverseLayout keeps the list pinned to the bottom, so appending a message
    // never shifts what you're already looking at (#3).
    val atBottom by remember {
        derivedStateOf { state.firstVisibleItemIndex == 0 && state.firstVisibleItemScrollOffset < 48 }
    }

    var unread by remember { mutableIntStateOf(0) }
    var seen by remember { mutableIntStateOf(chat.msgs.size) }

    LaunchedEffect(chat.msgs.size) {
        val added = chat.msgs.size - seen
        seen = chat.msgs.size
        if (added <= 0) return@LaunchedEffect
        val last = chat.msgs.lastOrNull()
        when {
            // No scroll at all. reverseLayout already pins index 0 to the
            // bottom, so there is nothing to close — and a scroll here would be
            // a THIRD animation on the same pixels as the row's placement
            // animation and the bubble's own scale. The previous guard only
            // skipped an offset of exactly 0, which after any touch it almost
            // never is, so this fired on most messages. That was the jump.
            atBottom -> unread = 0

            // The one case that genuinely needs a scroll: you sent something
            // while scrolled up, so nothing pins you to it. The movement is
            // large, intentional and on its own — the new row's entrance is
            // off-screen while it happens, so there is nothing to collide with.
            last?.from == chat.mySender -> {
                state.animateScrollToItem(0)
                unread = 0
            }

            else -> unread += added  // #5 count what arrived while scrolled up
        }
    }

    LaunchedEffect(atBottom) { if (atBottom) unread = 0 }

    val showIndicator = chat.peerTyping || chat.peerRecording

    /** Ids whose entrance animation has already played. */
    val animatedIds = remember { mutableSetOf<String>() }

    Box(modifier.fillMaxWidth().background(ChatBg)) {
        LazyColumn(
            state = state,
            reverseLayout = true,
            contentPadding = PaddingValues(16.dp),
            // Alignment.Bottom is not optional here. reverseLayout only defaults
            // the arrangement to Bottom when you don't pass one — a bare
            // spacedBy() silently packs a short chat to the top of the screen.
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.Bottom),
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { onTapEmptyArea() }
                },
        ) {
            item(key = "typing") {
                AnimatedVisibility(
                    visible = showIndicator,
                    // #1 the dots turn into the message.
                    //
                    // Entering, the slot opens its height smoothly — nothing is
                    // racing it, so the list just makes room for the dots.
                    //
                    // Leaving is the opposite problem and needs the opposite
                    // answer. The dots only ever disappear because a message is
                    // arriving, and that message claims its full height on the
                    // very first frame. If the typing row gives its height back
                    // gradually (the old shrinkVertically over 260ms) then for a
                    // quarter of a second BOTH rows are in the layout: the list
                    // gets shoved up by a whole bubble and then dragged back down
                    // as the dots deflate. Two opposite slides on the same pixels
                    // — the shake.
                    //
                    // So exit releases the space in one frame, matching iOS's
                    // `.identity`. The net height change is just the difference
                    // between the two, animateItem carries the neighbours over it
                    // in a single move, and the bubble grows from 0 out of the
                    // corner the dots were sitting in — so it reads as the dots
                    // becoming the message.
                    //
                    // Entering has no expandVertically any more, and that is the
                    // fix for the choppy morph. Height and placement are two
                    // different animation systems and they were both running:
                    // while this slot's height eased open, every row below it got
                    // a NEW placement target on every single frame, and
                    // animateItem re-aimed at it every frame. A spec that is
                    // retargeted 24 times can never travel its curve — it just
                    // chases, which is precisely the mush that was visible.
                    //
                    // Now the slot takes its full height in one frame and
                    // placement animation is the only thing moving list content,
                    // so the neighbours get one clean 400ms slide. This is also
                    // what iOS does — a SwiftUI `.transition(.scale)` never
                    // animated height either, so the two platforms now match.
                    // 0.60 on EnterSpring — iOS's `.scale(scale: 0.60,
                    // anchor: .topLeading)` on `enterSpring`, matched exactly.
                    // This was 0.82 on LayoutSpring, a known parity gap: at 0.82
                    // the dots are already four-fifths grown on their first frame,
                    // so almost none of the entrance is visible and it reads as a
                    // pop rather than a grow.
                    enter = scaleIn(EnterSpring, 0.60f, TransformOrigin(0f, 1f)) +
                            fadeIn(EnterSpring),
                    // ...but only when a message is actually taking the slot. If the
                    // peer merely stopped typing, nothing is competing for that
                    // space, and cutting the dots in a single frame is a hard edit
                    // against the slide. Then the dots shrink back into the
                    // corner they grew out of, mirroring their entrance.
                    exit = if (chat.typingHandoff) {
                        shrinkVertically(tween(0), Alignment.Bottom) + fadeOut(tween(0))
                    } else {
                        scaleOut(EnterSpring, 0.60f, TransformOrigin(0f, 1f)) +
                                fadeOut(EnterSpring) +
                                // Duration written out because shrinkVertically
                                // wants a spec over IntSize, not the Float that
                                // EnterSpring is. Keep the two numbers equal.
                                shrinkVertically(
                                    tween(280, easing = Decelerate),
                                    Alignment.Bottom,
                                )
                    },
                ) {
                    Row(Modifier.fillMaxWidth()) {
                        TypingBubble()
                        Spacer(Modifier.weight(1f))
                    }
                }
            }

            items(chat.msgs.asReversed(), key = { it.id }) { m ->
                // #3 the neighbours slide out of the way. Appear handles the new
                // row's own scale, so no fade spec here — two opacity curves on
                // the same row fight each other.
                val slide = Modifier.animateItem(
                    fadeInSpec = null,
                    placementSpec = PlacementSpring,
                    fadeOutSpec = null,
                )
                // `add` returns true only the first time we ever see this id. The
                // set lives in the list, which outlives the recycled rows, so a
                // bubble scrolling back into view renders at rest.
                val isNew = remember(m.id) { animatedIds.add(m.id) }
                if (m.isAdmin || m.from == Sender.system) {
                    Box(slide) {
                        Appear(isMine = false, animate = isNew) { SystemPill(m.text ?: "") }
                    }
                } else {
                    Box(slide) {
                        SwipeToReply(onReply = { onReply(m) }) {
                            // The scale goes THROUGH to the bubble, not around
                            // the full-width row — see BubbleView's doc comment.
                            BubbleView(
                                m, chat,
                                bubbleModifier = appearModifier(
                                    isMine = m.from == chat.mySender,
                                    animate = isNew,
                                ),
                            )
                        }
                    }
                }
            }
        }

        // #5 scroll-to-bottom, only while scrolled up, with a new-message count
        AnimatedVisibility(
            visible = !atBottom,
            enter = scaleIn(EnterSpring, initialScale = 0.6f) + fadeIn(EnterSpring),
            exit = scaleOut(EnterSpring, targetScale = 0.6f) + fadeOut(EnterSpring),
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) {
            ScrollToBottom(unread) {
                scope.launch { state.animateScrollToItem(0) }
                unread = 0
            }
        }
    }
}

@Composable
private fun SwipeToReply(onReply: () -> Unit, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val maxDrag = with(density) { 72.dp.toPx() }
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxWidth()) {
        val progress = (offset.value / maxDrag).coerceIn(0f, 1f)
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .alpha(progress)
                .size(28.dp)
                .background(PillBg, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.Text("\u21A9", fontSize = 15.sp, color = Subtle)
        }

        Box(
            Modifier
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val fired = offset.value > maxDrag * 0.6f
                            scope.launch {
                                offset.animateTo(0f, EnterSpring)
                            }
                            if (fired) onReply()
                        },
                        onDragCancel = { scope.launch { offset.animateTo(0f, EnterSpring) } },
                        onHorizontalDrag = { _, delta ->
                            scope.launch {
                                offset.snapTo((offset.value + delta).coerceIn(0f, maxDrag))
                            }
                        },
                    )
                }
        ) { content() }
    }
}

// Read blue, sampled from the design export "Group 1261158377.svg". The tick
// grey is just Muted, which now matches the export exactly.
private val TickBlue = Color(0xFF0BA5EC)

// Glyph metrics in dp, lifted straight from the SVG path data. One SVG user
// unit is one dp: the exported bubble is 268 units wide, which is 70% of a
// 390pt screen — the same cap the bubble uses.
private const val TickW = 10.4f    // one checkmark
private const val TickGap = 3.2f   // horizontal offset between the two
private const val SlotW = TickW + TickGap
private const val SlotH = 8f
private const val ClockD = 6.667f

/**
 * Four delivery states in a slot of fixed size, so a status change can never
 * alter the bubble's width or height (§13/§14):
 *
 *   sending   — outline clock
 *   sent      — one grey tick
 *   delivered — two grey ticks
 *   read      — two blue ticks
 *
 * The glyphs are drawn from the design's own path data rather than assembled
 * out of Icons.Filled.Check. Two reasons: Material's check is a different
 * shape, and the design's tick has a bevelled tail that reads as deliberate at
 * this size. The clock is not in Compose's core icon set at all, and pulling
 * material-icons-extended for one glyph costs megabytes.
 *
 * ALIGNMENT: the pair is right-aligned, matching the SVG, where the single tick
 * and the right-hand tick of the pair share an edge at x=316. So going from
 * sent to delivered, the tick already on screen does not move and the new one
 * appears to its left. Growing rightward instead shifts the existing tick and
 * reads as a nudge.
 *
 * RECOMPOSITION: the animated values are held as State objects and read inside
 * the Canvas draw lambda, which runs in the draw phase. Reading them out here
 * with `by` would recompose this composable — and re-measure the bubble's Row —
 * on every frame of each 180ms transition, roughly 11 frames per change and
 * four changes per message. This way nothing above the draw phase re-runs.
 *
 * Colour is interpolated in the same lambda rather than with
 * animateColorAsState, for the same reason, and it also avoids stacking a blue
 * copy over a grey one and blending muddily through the crossfade.
 */
@Composable
private fun StatusTicks(status: DeliveryStatus) {
    val clock = animateFloatAsState(
        if (status == DeliveryStatus.sending) 1f else 0f, tween(180), label = "clock",
    )
    val ticks = animateFloatAsState(
        if (status == DeliveryStatus.sending) 0f else 1f, tween(180), label = "ticks",
    )
    val second = animateFloatAsState(
        if (status >= DeliveryStatus.delivered) 1f else 0f, tween(180), label = "second",
    )
    val blue = animateFloatAsState(
        if (status == DeliveryStatus.read) 1f else 0f, tween(180), label = "blue",
    )

    Canvas(Modifier.width(SlotW.dp).height(SlotH.dp)) {
        fun u(v: Float) = v.dp.toPx()

        // The design's checkmark, origin at its own top-left.
        fun tick(dx: Float) = Path().apply {
            moveTo(u(dx + 10.400f), u(0.844f))
            lineTo(u(dx + 3.302f), u(8.000f))
            lineTo(u(dx + 0.000f), u(4.669f))
            lineTo(u(dx + 0.837f), u(3.825f))
            lineTo(u(dx + 3.302f), u(6.312f))
            lineTo(u(dx + 9.563f), u(0.000f))
            close()
        }

        val tint = lerp(Muted, TickBlue, blue.value)
        // Front tick sits on the right and never moves; the second joins on its
        // left. Drawn in one layer, so the overlap cannot double-darken.
        drawPath(tick(TickGap), tint, alpha = ticks.value)
        drawPath(tick(0f), tint, alpha = ticks.value * second.value)

        if (clock.value > 0f) {
            val s = u(1f)
            val cx = u(SlotW - 0.667f - ClockD / 2f)
            val cy = u(SlotH / 2f)
            val c = Offset(cx, cy)
            drawCircle(
                Muted, u(ClockD / 2f) - s / 2f, c,
                alpha = clock.value, style = Stroke(s),
            )
            drawLine(
                Muted, Offset(cx, cy - u(1.333f)), c,
                strokeWidth = s, cap = StrokeCap.Round, alpha = clock.value,
            )
            drawLine(
                Muted, c, Offset(cx + u(0.833f), cy + u(0.833f)),
                strokeWidth = s, cap = StrokeCap.Round, alpha = clock.value,
            )
        }
    }
}

/**
 * @param bubbleModifier applied to the bubble itself, NOT to the row.
 *
 * The entrance scale has to land here. It used to wrap the whole of BubbleView
 * via `Appear`, but BubbleView's root is a `fillMaxWidth` Row, so a
 * `TransformOrigin(1f, 1f)` resolved to the bottom-right of the SCREEN rather
 * than the bottom-right of the bubble. The bubble then collapsed toward the
 * screen edge — sliding in from the side — instead of growing out of its own
 * tail corner, and it shrank in proportion to the screen's width rather than
 * its own. Anchored on the Column, 0f/1f are the bubble's real corners.
 */
@Composable
private fun BubbleView(msg: Message, chat: ChatClient, bubbleModifier: Modifier = Modifier) {
    val isMine = msg.from == chat.mySender
    val quoted = msg.replyToId?.let { id -> chat.msgs.firstOrNull { it.id == id } }
    // §11 — hug the content, but never grow past ~70% of the screen.
    val maxBubble = (LocalConfiguration.current.screenWidthDp * 0.70f).dp

    Row(Modifier.fillMaxWidth()) {
        if (isMine) Spacer(Modifier.weight(1f).widthIn(min = 50.dp))

        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = bubbleModifier
                .widthIn(max = maxBubble)
                .shadow(1.dp, bubbleShape(isMine))
                .background(if (isMine) SentBg else Color.White, bubbleShape(isMine))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            if (quoted != null) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(InkDark.copy(alpha = 0.06f))
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(Modifier.width(2.dp).height(28.dp).background(SendOrange))
                    Column {
                        androidx.compose.material3.Text(
                            if (quoted.from == chat.mySender) "You" else "Seeker",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = SendOrange,
                        )
                        androidx.compose.material3.Text(
                            quoted.text ?: "[${quoted.kind.name}]",
                            fontSize = 12.sp,
                            color = Subtle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            msg.text?.let {
                androidx.compose.material3.Text(
                    text = it,
                    fontSize = 15.sp,
                    color = InkDark,
                    textAlign = TextAlign.Start,
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                androidx.compose.material3.Text(
                    timeFmt.format(Date(msg.ts)), fontSize = 10.sp, color = Subtle,
                )
                if (isMine) StatusTicks(msg.status)
            }
        }

        if (!isMine) Spacer(Modifier.weight(1f).widthIn(min = 50.dp))
    }
}

@Composable
private fun TypingBubble() {
    val phase by rememberInfiniteTransition(label = "typing").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1150, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .shadow(1.dp, bubbleShape(false))
            .background(Color.White, bubbleShape(false))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        repeat(3) { i ->
            // One cosine per dot, phase-shifted. `(1 - cos)/2` runs 0 -> 1 -> 0
            // and meets itself at both ends with matching slope, so the loop has
            // no seam. The old version lifted a dot for 30% of the cycle and
            // parked it for the other 70%, then hard-cut its alpha between 0.35
            // and 1 — a boolean step, which is what read as chop.
            var p = (phase - i * 0.18f) % 1f
            if (p < 0f) p += 1f
            val w = (1f - cos(p * 2f * PI.toFloat())) / 2f
            Box(
                Modifier
                    .size(7.dp)
                    // Scale, not offset, so the bubble's height never changes
                    // and the conversation above it stays still.
                    .graphicsLayer {
                        val s = 0.72f + 0.38f * w
                        scaleX = s
                        scaleY = s
                        alpha = 0.32f + 0.68f * w
                    }
                    .background(Muted, CircleShape)
            )
        }
    }
}

@Composable
private fun SystemPill(text: String) {
    androidx.compose.material3.Text(
        text = text,
        fontSize = 14.sp,
        color = PillText,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .background(PillBg, RoundedCornerShape(12.dp))
            .border(1.dp, PillStroke, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun ScrollToBottom(unread: Int, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .shadow(3.dp, CircleShape)
            .background(Color.White, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = if (unread > 0) 12.dp else 8.dp, vertical = 8.dp),
    ) {
        Icon(Icons.Filled.KeyboardArrowDown, "Scroll to latest", Modifier.size(20.dp), InkDark)
        if (unread > 0) {
            androidx.compose.material3.Text(
                if (unread > 99) "99+" else "$unread",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = SendOrange,
            )
        }
    }
}

@Composable
private fun ReplyHud(replyTo: Message?, onClear: () -> Unit) {
    AnimatedVisibility(
        visible = replyTo != null,
        enter = fadeIn(LayoutSpring),
        exit = fadeOut(LayoutSpring),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(PillBg)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Box(Modifier.width(2.dp).height(32.dp).background(SendOrange))
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 8.dp)
            ) {
                androidx.compose.material3.Text(
                    "Replying to",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = SendOrange,
                )
                androidx.compose.material3.Text(
                    replyTo?.text ?: "",
                    fontSize = 13.sp,
                    color = Subtle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                Icons.Filled.Close, "Cancel reply",
                Modifier.size(18.dp).clickable(onClick = onClear), Subtle,
            )
        }
    }
}

@Composable
private fun Header(chat: ChatClient) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(Color.White)
            .padding(horizontal = 16.dp),
    ) {
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Back", Modifier.size(24.dp), InkDark)

        // The photo is always present — only the subtitle changes state, so the
        // header never reflows mid-conversation.
        Box(
            Modifier
                .size(45.dp)
                .background(SentBg, CircleShape),
            Alignment.Center,
        ) {
            androidx.compose.material3.Text(
                "SK", fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                color = InkDark.copy(alpha = 0.6f),
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            androidx.compose.material3.Text(
                "Seeker",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = InkDark,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (chat.connected) {
                    Box(Modifier.size(7.dp).background(LiveGreen, CircleShape))
                    androidx.compose.material3.Text(
                        "Free Chat", fontSize = 12.sp,
                        fontWeight = FontWeight.Medium, color = LiveGreen,
                    )
                } else {
                    androidx.compose.material3.Text(
                        "Connecting\u2026", fontSize = 12.sp, color = Subtle,
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))
    }
}


@Composable
private fun Composer(
    draft: String,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Icon(Icons.Filled.Add, null, Modifier.size(24.dp), Subtle)

        Box(
            Modifier
                .weight(1f)
                .background(PillBg, CircleShape)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            if (draft.isEmpty()) {
                androidx.compose.material3.Text(
                    "Type a message...", fontSize = 16.sp, color = Muted,
                )
            }
            BasicTextField(
                value = draft,
                onValueChange = onDraftChange,
                textStyle = TextStyle(fontSize = 16.sp, color = InkDark),
                maxLines = 5,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Icon(
            Icons.AutoMirrored.Filled.Send,
            "Send",
            Modifier
                .size(26.dp)
                .clickable(enabled = draft.isNotBlank(), onClick = onSend),
            if (draft.isBlank()) Muted else SendOrange,
        )
    }
}
