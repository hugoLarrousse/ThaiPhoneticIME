package com.example.thaiphoneticime.keyboard

enum class KeyboardLayer {
    LETTERS,
    SYMBOLS
}

sealed class KeyAction {
    data class CommitText(val text: String) : KeyAction()
    data object Backspace : KeyAction()
    data object Shift : KeyAction()
    data object Space : KeyAction()
    data object Enter : KeyAction()
    data object ShowInputMethodPicker : KeyAction()
    data class ToggleLayer(val layer: KeyboardLayer) : KeyAction()
}

data class KeySpec(
    val label: String,
    val action: KeyAction,
    val widthWeight: Float = 1f,
    val longPress: List<KeySpec>? = null,
    val longPressAction: KeyAction? = null
)

private fun variantKey(text: String): KeySpec = KeySpec(
    label = text,
    action = KeyAction.CommitText(text)
)

private fun symbolKey(symbol: String): KeySpec = KeySpec(
    label = symbol,
    action = KeyAction.CommitText(symbol)
)

private val precomposedAccentsByBase: Map<String, List<String>> = mapOf(
    "a" to listOf("à", "á", "â", "ǎ"),
    "e" to listOf("è", "é", "ê", "ě"),
    "i" to listOf("ì", "í", "î", "ǐ"),
    "o" to listOf("ò", "ó", "ô", "ǒ"),
    "u" to listOf("ù", "ú", "û", "ǔ")
)

private fun accentVariants(base: String): List<String> {
    return precomposedAccentsByBase[base] ?: listOf(
        "$base\u0300",
        "$base\u0301",
        "$base\u0302",
        "$base\u030C"
    )
}

private fun longPressVariants(
    base: String,
    extras: List<String> = emptyList(),
    accentTargets: List<String> = listOf(base)
): List<String> {
    val variants = linkedSetOf<String>()
    variants.add(base)
    extras.forEach(variants::add)
    accentTargets.forEach { target ->
        accentVariants(target).forEach(variants::add)
    }
    return variants.toList()
}

private fun letterKey(
    letter: String,
    longPressVariants: List<String> = emptyList()
): KeySpec = KeySpec(
    label = letter,
    action = KeyAction.CommitText(letter),
    longPress = if (longPressVariants.isEmpty()) {
        null
    } else {
        longPressVariants.map(::variantKey)
    }
)

private fun azertyLetterKey(letter: String): KeySpec {
    return when (letter) {
        "a" -> letterKey(letter, longPressVariants = longPressVariants(base = "a"))
        "e" -> letterKey(
            letter,
            longPressVariants = longPressVariants(
                base = "e",
                extras = listOf("ə", "ɛ"),
                accentTargets = listOf("e", "ə", "ɛ")
            )
        )
        "i" -> letterKey(letter, longPressVariants = longPressVariants(base = "i"))
        "u" -> letterKey(
            letter,
            longPressVariants = longPressVariants(
                base = "u",
                extras = listOf("ʉ"),
                accentTargets = listOf("u", "ʉ")
            )
        )
        "n" -> letterKey(letter, longPressVariants = listOf("n", "ŋ"))
        "o" -> letterKey(
            letter,
            longPressVariants = longPressVariants(
                base = "o",
                extras = listOf("ɔ"),
                accentTargets = listOf("o", "ɔ")
            )
        )
        else -> letterKey(letter)
    }
}

private fun dedicatedPhoneticKey(letter: String): KeySpec = letterKey(
    letter,
    longPressVariants = longPressVariants(base = letter)
)

val azertyLayer: List<List<KeySpec>> = listOf(
    listOf("ʉ", "ə", "ɛ", "ɔ").map(::dedicatedPhoneticKey),
    listOf("a", "z", "e", "r", "t", "y", "u", "i", "o", "p").map(::azertyLetterKey),
    listOf("q", "s", "d", "f", "g", "h", "j", "k", "l", "m").map(::azertyLetterKey),
    listOf(
        KeySpec(label = "↑", action = KeyAction.Shift, widthWeight = 1.4f),
        azertyLetterKey("w"),
        azertyLetterKey("x"),
        azertyLetterKey("c"),
        azertyLetterKey("v"),
        azertyLetterKey("b"),
        azertyLetterKey("n"),
        symbolKey("'"),
        KeySpec(label = "\u232b", action = KeyAction.Backspace, widthWeight = 1.4f)
    ),
    listOf(
        KeySpec(
            label = "?123",
            action = KeyAction.ToggleLayer(KeyboardLayer.SYMBOLS),
            widthWeight = 1.2f
        ),
        symbolKey(","),
        KeySpec(
            label = "Space",
            action = KeyAction.Space,
            widthWeight = 2.8f,
            longPressAction = KeyAction.ShowInputMethodPicker
        ),
        symbolKey("."),
        KeySpec(label = "↵", action = KeyAction.Enter, widthWeight = 1.2f)
    )
)

val symbolsLayer: List<List<KeySpec>> = listOf(
    listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0").map(::symbolKey),
    listOf("@", "#", "€", "_", "&", "-", "+", "(", ")", "/").map(::symbolKey),
    listOf(
        symbolKey("*"),
        symbolKey("\""),
        symbolKey("'"),
        symbolKey(":"),
        symbolKey(";"),
        symbolKey("!"),
        symbolKey("?"),
        KeySpec(label = "\u232b", action = KeyAction.Backspace, widthWeight = 1.4f)
    ),
    listOf(
        KeySpec(
            label = "ABC",
            action = KeyAction.ToggleLayer(KeyboardLayer.LETTERS),
            widthWeight = 1.3f
        ),
        symbolKey(","),
        KeySpec(
            label = "Space",
            action = KeyAction.Space,
            widthWeight = 3.2f,
            longPressAction = KeyAction.ShowInputMethodPicker
        ),
        symbolKey("."),
        KeySpec(label = "↵", action = KeyAction.Enter, widthWeight = 1.3f)
    )
)
