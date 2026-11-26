package com.example.glasskeyboard

import android.content.ClipboardManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo

class GlassKeyboardService : InputMethodService(), KeyboardView.Listener {

    private var keyboard: KeyboardView? = null

    override fun onCreate() {
        super.onCreate()
        window?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }

    override fun onCreateInputView(): View {
        val v = KeyboardView(this)
        v.listener = this
        keyboard = v
        return v
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        if (!restarting) keyboard?.resetState()
    }

    override fun onText(text: String) {
        currentInputConnection?.commitText(text, 1)
    }

    override fun onBackspace() {
        val ic = currentInputConnection ?: return
        val selected = ic.getSelectedText(0)
        if (!selected.isNullOrEmpty()) {
            ic.commitText("", 1)
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        }
    }

    override fun onEnter() {
        val ic = currentInputConnection ?: return
        val ei = currentInputEditorInfo
        val action = (ei?.imeOptions ?: 0) and EditorInfo.IME_MASK_ACTION
        val noEnterAction = ((ei?.imeOptions ?: 0) and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
        if (!noEnterAction &&
            action != EditorInfo.IME_ACTION_NONE &&
            action != EditorInfo.IME_ACTION_UNSPECIFIED
        ) {
            ic.performEditorAction(action)
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        }
    }

    override fun onPaste() {
        val cm = getSystemService(ClipboardManager::class.java) ?: return
        val clip = cm.primaryClip ?: return
        if (clip.itemCount == 0) return
        val text = clip.getItemAt(0).coerceToText(this)
        if (!text.isNullOrEmpty()) currentInputConnection?.commitText(text, 1)
    }

    override fun onHide() {
        requestHideSelf(0)
    }
} 
