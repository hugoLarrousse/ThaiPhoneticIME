package com.example.thaiphoneticime

import android.content.Context
import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.thaiphoneticime.keyboard.KeyAction
import com.example.thaiphoneticime.keyboard.KeyboardLayer
import com.example.thaiphoneticime.keyboard.KeySpec
import com.example.thaiphoneticime.keyboard.KeyboardUi
import com.example.thaiphoneticime.keyboard.azertyLayer
import com.example.thaiphoneticime.keyboard.symbolsLayer

class PhoneticImeService : InputMethodService() {
    private val composeOwner = ImeComposeOwner()
    private var keyboardLayer by mutableStateOf(KeyboardLayer.LETTERS)
    private var isShiftOn by mutableStateOf(false)
    private val inputMethodManager by lazy {
        getSystemService(InputMethodManager::class.java)
    }

    override fun onCreate() {
        super.onCreate()
        composeOwner.onCreate()
    }

    override fun onCreateInputView(): View {
        return ImeComposeHostView(this, composeOwner) {
            MaterialTheme {
                KeyboardUi(
                    rows = currentRows(),
                    onKeyAction = ::handleKeyAction
                )
            }
        }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        composeOwner.onStart()
        keyboardLayer = KeyboardLayer.LETTERS
        isShiftOn = false
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        composeOwner.onStop()
        super.onFinishInputView(finishingInput)
    }

    override fun onDestroy() {
        composeOwner.onDestroy()
        super.onDestroy()
    }

    private fun handleKeyAction(action: KeyAction) {
        when (action) {
            is KeyAction.CommitText -> {
                val committedText = transformCommittedText(action.text)
                commit(committedText)
                if (isShiftOn && shouldConsumeShift(action.text)) {
                    isShiftOn = false
                }
            }
            KeyAction.Backspace -> backspace()
            KeyAction.Space -> commit(" ")
            KeyAction.Enter -> enter()
            KeyAction.ShowInputMethodPicker -> showInputMethodPicker()
            KeyAction.Shift -> {
                if (keyboardLayer == KeyboardLayer.LETTERS) {
                    isShiftOn = !isShiftOn
                }
            }
            is KeyAction.ToggleLayer -> {
                keyboardLayer = action.layer
                if (keyboardLayer != KeyboardLayer.LETTERS) {
                    isShiftOn = false
                }
            }
        }
    }

    private fun currentRows(): List<List<KeySpec>> {
        return when (keyboardLayer) {
            KeyboardLayer.LETTERS -> withShiftDisplay(azertyLayer, isShiftOn)
            KeyboardLayer.SYMBOLS -> symbolsLayer
        }
    }

    private fun withShiftDisplay(
        rows: List<List<KeySpec>>,
        shiftOn: Boolean
    ): List<List<KeySpec>> {
        if (!shiftOn) return rows
        return rows.map { row ->
            row.map { keySpec ->
                when (keySpec.action) {
                    is KeyAction.CommitText -> {
                        val label = keySpec.label
                        if (label.any(Char::isLetter)) {
                            keySpec.copy(
                                label = label.uppercase(),
                                longPress = keySpec.longPress?.map { option ->
                                    if (option.label.any(Char::isLetter)) {
                                        option.copy(label = option.label.uppercase())
                                    } else {
                                        option
                                    }
                                }
                            )
                        } else {
                            keySpec
                        }
                    }
                    KeyAction.Shift -> keySpec.copy(label = "⇧")
                    else -> keySpec
                }
            }
        }
    }

    private fun transformCommittedText(text: String): String {
        if (keyboardLayer != KeyboardLayer.LETTERS) return text
        if (!isShiftOn) return text
        if (text.any(Char::isLetter)) {
            return text.uppercase()
        }
        return text
    }

    private fun shouldConsumeShift(text: String): Boolean {
        return keyboardLayer == KeyboardLayer.LETTERS && text.any(Char::isLetter)
    }

    private fun commit(text: String) {
        currentInputConnection?.commitText(text, 1)
    }

    private fun backspace() {
        val inputConnection = currentInputConnection ?: return
        val selectedText = inputConnection.getSelectedText(0)
        if (!selectedText.isNullOrEmpty()) {
            inputConnection.commitText("", 1)
            return
        }

        val deleted = inputConnection.deleteSurroundingTextInCodePoints(1, 0)
        if (!deleted) {
            inputConnection.deleteSurroundingText(1, 0)
        }
    }

    private fun enter() {
        val inputConnection = currentInputConnection ?: return
        val imeOptions = currentInputEditorInfo?.imeOptions ?: 0
        val action = imeOptions and EditorInfo.IME_MASK_ACTION
        if (
            action != EditorInfo.IME_ACTION_NONE &&
            action != EditorInfo.IME_ACTION_UNSPECIFIED
        ) {
            if (!inputConnection.performEditorAction(action)) {
                inputConnection.commitText("\n", 1)
            }
            return
        }
        inputConnection.commitText("\n", 1)
    }

    private fun showInputMethodPicker() {
        inputMethodManager?.showInputMethodPicker()
    }
}

private class ImeComposeHostView(
    context: Context,
    private val owner: ImeComposeOwner,
    content: @Composable () -> Unit
) : FrameLayout(context) {
    private val composeView = ComposeView(context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        setContent { content() }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        val root = rootView
        root.setViewTreeLifecycleOwner(owner)
        root.setViewTreeViewModelStoreOwner(owner)
        root.setViewTreeSavedStateRegistryOwner(owner)

        setViewTreeLifecycleOwner(owner)
        setViewTreeViewModelStoreOwner(owner)
        setViewTreeSavedStateRegistryOwner(owner)

        if (composeView.parent == null) {
            addView(
                composeView,
                LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            )
        }

        ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
            val bottomInset = insets
                .getInsets(WindowInsetsCompat.Type.navigationBars())
                .bottom
            view.setPadding(
                view.paddingLeft,
                view.paddingTop,
                view.paddingRight,
                bottomInset
            )
            insets
        }
        ViewCompat.requestApplyInsets(this)
    }
}

private class ImeComposeOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val viewModelStore: ViewModelStore
        get() = store

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateController.savedStateRegistry

    fun onCreate() {
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    fun onStart() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun onStop() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
    }

    fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
    }
}
