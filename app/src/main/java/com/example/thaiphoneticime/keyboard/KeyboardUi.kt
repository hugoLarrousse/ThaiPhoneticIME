package com.example.thaiphoneticime.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val BACKSPACE_REPEAT_START_DELAY_MS = 350L
private const val BACKSPACE_REPEAT_INTERVAL_MS = 55L
private const val LONG_PRESS_TRIGGER_MS = 280L
private const val LONG_PRESS_POPUP_MARGIN_PX = 10
private const val PRESSED_BLEND_FACTOR = 0.18f
private const val POPUP_SELECTION_BLEND_FACTOR = 0.24f

private val KeyboardBackground = Color(0xFF1F2A33)
private val KeyBackground = Color(0xFF4A5661)
private val SpecialKeyBackground = Color(0xFF3C4853)
private val EnterKeyBackground = Color(0xFF6FB7B1)
private val PopupBackground = Color(0xFF27333D)
private val KeyTextColor = Color(0xFFEAF0F4)
private val EnterTextColor = Color(0xFF12242A)
private val PressPreviewBackground = Color(0xFF5A6671)

@Composable
fun KeyboardUi(
    rows: List<List<KeySpec>>,
    onKeyAction: (KeyAction) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(KeyboardBackground)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                row.forEach { keySpec ->
                    KeyButton(
                        keySpec = keySpec,
                        modifier = Modifier.weight(keySpec.widthWeight.coerceAtLeast(0.1f)),
                        onKeyAction = onKeyAction
                    )
                }
            }
        }
    }
}

@Composable
private fun KeyButton(
    keySpec: KeySpec,
    modifier: Modifier = Modifier,
    onKeyAction: (KeyAction) -> Unit
) {
    data class PopupOption(val id: Int, val spec: KeySpec)

    val longPressOptions = keySpec.longPress.orEmpty()
    val longPressAction = keySpec.longPressAction
    val popupRows = popupRowsForKey(keySpec, longPressOptions)
    val popupOptionsByRow = remember(keySpec.label, popupRows) {
        var nextId = 0
        popupRows.map { popupRow ->
            popupRow.map { option ->
                PopupOption(id = nextId++, spec = option)
            }
        }
    }
    val popupOptionsById = remember(popupOptionsByRow) {
        popupOptionsByRow
            .flatten()
            .associateBy { it.id }
    }
    val popupOptionBounds = remember(keySpec.label) { mutableStateMapOf<Int, Rect>() }
    var showLongPressPopup by remember(keySpec.label) { mutableStateOf(false) }
    var showKeyPreview by remember(keySpec.label) { mutableStateOf(false) }
    var isPressed by remember(keySpec.label) { mutableStateOf(false) }
    var highlightedPopupOptionId by remember(keySpec.label) { mutableStateOf<Int?>(null) }
    var keyBoundsInWindow by remember(keySpec.label) { mutableStateOf<IntRect?>(null) }
    var hostWindowSize by remember(keySpec.label) { mutableStateOf(IntSize.Zero) }
    var popupContentSize by remember(keySpec.label) { mutableStateOf<IntSize?>(null) }
    val hostView = LocalView.current
    val scope = rememberCoroutineScope()
    val (baseBackgroundColor, textColor) = keyPalette(keySpec)
    val keyBackgroundColor = if (isPressed) {
        lerp(baseBackgroundColor, Color.White, PRESSED_BLEND_FACTOR)
    } else {
        baseBackgroundColor
    }

    Box(modifier = modifier) {
        if (showKeyPreview) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (-48).dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(PressPreviewBackground)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = keySpec.label,
                    color = KeyTextColor
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 50.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(keyBackgroundColor)
                .onGloballyPositioned { coordinates ->
                    val bounds = coordinates.boundsInWindow()
                    keyBoundsInWindow = IntRect(
                        left = bounds.left.roundToInt(),
                        top = bounds.top.roundToInt(),
                        right = bounds.right.roundToInt(),
                        bottom = bounds.bottom.roundToInt()
                    )
                    hostWindowSize = IntSize(
                        width = hostView.width.coerceAtLeast(1),
                        height = hostView.height.coerceAtLeast(1)
                    )
                }
                .pointerInput(keySpec.label, longPressOptions, longPressAction) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var didRepeat = false
                        var isLongPressTriggered = false
                        var isGestureCanceled = false
                        var pointerWindowPosition = windowPositionFromLocal(
                            keyBounds = keyBoundsInWindow,
                            localPosition = down.position
                        )
                        var repeatJob: Job? = null
                        var longPressJob: Job? = null

                        isPressed = true
                        if (keySpec.action is KeyAction.CommitText) {
                            showKeyPreview = true
                        }

                        if (keySpec.action == KeyAction.Backspace) {
                            repeatJob = scope.launch {
                                delay(BACKSPACE_REPEAT_START_DELAY_MS)
                                while (true) {
                                    didRepeat = true
                                    onKeyAction(KeyAction.Backspace)
                                    delay(BACKSPACE_REPEAT_INTERVAL_MS)
                                }
                            }
                        } else {
                            longPressJob = scope.launch {
                                delay(LONG_PRESS_TRIGGER_MS)
                                isLongPressTriggered = true
                                showKeyPreview = false
                                if (longPressOptions.isNotEmpty()) {
                                    popupOptionBounds.clear()
                                    highlightedPopupOptionId = popupOptionsByRow
                                        .firstOrNull()
                                        ?.firstOrNull()
                                        ?.id
                                    showLongPressPopup = true
                                } else if (longPressAction != null) {
                                    onKeyAction(longPressAction)
                                }
                            }
                        }

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                                ?: event.changes.firstOrNull()
                                ?: continue

                            pointerWindowPosition = windowPositionFromLocal(
                                keyBounds = keyBoundsInWindow,
                                localPosition = change.position
                            )

                                if (showLongPressPopup && longPressOptions.isNotEmpty()) {
                                val anchor = keyBoundsInWindow
                                val pointer = pointerWindowPosition
                                val popupSize = popupContentSize
                                if (
                                    anchor != null &&
                                    pointer != null &&
                                    popupSize != null
                                ) {
                                    val popupOrigin = resolvePopupOrigin(
                                        anchor = anchor,
                                        windowSize = hostWindowSize,
                                        popupContentSize = popupSize
                                    )
                                    val pointerInPopup = Offset(
                                        x = pointer.x - popupOrigin.x,
                                        y = pointer.y - popupOrigin.y
                                    )
                                    highlightedPopupOptionId = nearestPopupOptionId(
                                        pointerInPopup = pointerInPopup,
                                        popupOptionBounds = popupOptionBounds
                                    ) ?: highlightedPopupOptionId
                                }
                            }

                            if (change.changedToUpIgnoreConsumed()) {
                                break
                            }
                            if (!change.pressed) {
                                isGestureCanceled = true
                                break
                            }
                        }

                        repeatJob?.cancel()
                        longPressJob?.cancel()

                        if (!isGestureCanceled) {
                            when {
                                keySpec.action == KeyAction.Backspace -> {
                                    if (!didRepeat) {
                                        onKeyAction(KeyAction.Backspace)
                                    }
                                }
                                isLongPressTriggered && longPressOptions.isNotEmpty() -> {
                                    val selectedPopupOption = (
                                        highlightedPopupOptionId?.let(popupOptionsById::get)
                                            ?: popupOptionsByRow.firstOrNull()?.firstOrNull()
                                        )?.spec
                                    selectedPopupOption?.let { onKeyAction(it.action) }
                                }
                                !isLongPressTriggered -> onKeyAction(keySpec.action)
                            }
                        }

                        showLongPressPopup = false
                        showKeyPreview = false
                        highlightedPopupOptionId = null
                        popupOptionBounds.clear()
                        popupContentSize = null
                        isPressed = false
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = keySpec.label,
                color = textColor
            )
        }

        if (showLongPressPopup && longPressOptions.isNotEmpty()) {
            Popup(
                popupPositionProvider = KeyPopupPositionProvider(keyBoundsInWindow),
                onDismissRequest = {
                    showLongPressPopup = false
                    highlightedPopupOptionId = null
                    popupOptionBounds.clear()
                    popupContentSize = null
                },
                properties = PopupProperties(
                    focusable = false,
                    clippingEnabled = false
                )
            ) {
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(PopupBackground)
                        .padding(5.dp)
                        .onGloballyPositioned { coordinates ->
                        popupContentSize = coordinates.size
                    },
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    popupOptionsByRow.forEach { popupRow ->
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            popupRow.forEach { option ->
                                val (optionBackground, optionTextColor) = keyPalette(option.spec)
                                val isHighlighted = highlightedPopupOptionId == option.id
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(
                                            if (isHighlighted) {
                                                lerp(
                                                    optionBackground,
                                                    Color.White,
                                                    POPUP_SELECTION_BLEND_FACTOR
                                                )
                                            } else {
                                                optionBackground
                                            }
                                        )
                                        .onGloballyPositioned { coordinates ->
                                            popupOptionBounds[option.id] = coordinates.boundsInRoot()
                                        }
                                        .padding(horizontal = 11.dp, vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = option.spec.label,
                                        color = optionTextColor
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun windowPositionFromLocal(
    keyBounds: IntRect?,
    localPosition: Offset
): Offset? {
    val bounds = keyBounds ?: return null
    return Offset(
        x = bounds.left + localPosition.x,
        y = bounds.top + localPosition.y
    )
}

private fun nearestPopupOptionId(
    pointerInPopup: Offset,
    popupOptionBounds: Map<Int, Rect>
): Int? {
    if (popupOptionBounds.isEmpty()) return null
    return popupOptionBounds.minByOrNull { (_, bounds) ->
        distanceToBoundsSquared(pointerInPopup, bounds)
    }?.key
}

private fun distanceToBoundsSquared(point: Offset, bounds: Rect): Float {
    val clampedX = point.x.coerceIn(bounds.left, bounds.right)
    val clampedY = point.y.coerceIn(bounds.top, bounds.bottom)
    val dx = point.x - clampedX
    val dy = point.y - clampedY
    return (dx * dx) + (dy * dy)
}

private fun resolvePopupOrigin(
    anchor: IntRect,
    windowSize: IntSize,
    popupContentSize: IntSize
): IntOffset {
    val centeredX = anchor.left + ((anchor.width - popupContentSize.width) / 2)
    val aboveY = anchor.top - popupContentSize.height - LONG_PRESS_POPUP_MARGIN_PX
    val belowY = anchor.bottom + LONG_PRESS_POPUP_MARGIN_PX
    val maxX = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
    val canFitBelow = belowY + popupContentSize.height <= windowSize.height
    val resolvedY = if (aboveY >= 0 || !canFitBelow) aboveY else belowY

    return IntOffset(
        x = centeredX.coerceIn(0, maxX),
        y = resolvedY
    )
}

private fun keyPalette(keySpec: KeySpec): Pair<Color, Color> {
    return when (keySpec.action) {
        KeyAction.Enter -> EnterKeyBackground to EnterTextColor
        KeyAction.Backspace,
        KeyAction.Shift,
        is KeyAction.ToggleLayer -> SpecialKeyBackground to KeyTextColor
        else -> KeyBackground to KeyTextColor
    }
}

private fun popupRowsForKey(keySpec: KeySpec, options: List<KeySpec>): List<List<KeySpec>> {
    if (options.isEmpty()) return emptyList()
    val baseCommit = (keySpec.action as? KeyAction.CommitText)?.text?.lowercase()
    val useTwoRows = baseCommit == "e" || baseCommit == "u" || baseCommit == "o"
    if (!useTwoRows || options.size < 2) {
        return listOf(options)
    }
    val splitIndex = (options.size + 1) / 2
    return listOf(
        options.take(splitIndex),
        options.drop(splitIndex)
    )
}

private class KeyPopupPositionProvider(
    private val keyBounds: IntRect?
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val anchor = keyBounds ?: anchorBounds
        return resolvePopupOrigin(
            anchor = anchor,
            windowSize = windowSize,
            popupContentSize = popupContentSize
        )
    }
}
