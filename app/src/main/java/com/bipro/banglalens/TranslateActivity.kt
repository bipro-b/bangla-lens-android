package com.bipro.banglalens

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.Executors

class TranslateActivity : Activity() {

    private lateinit var input: EditText
    private lateinit var output: TextView
    private lateinit var direction: TextView
    private lateinit var progress: ProgressBar
    private lateinit var btnCopy: Button
    private lateinit var btnReplace: Button

    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private val clipboard by lazy { getSystemService(ClipboardManager::class.java) }
    private val router by lazy { TranslationRouter.get(this) }

    private var result = ""
    private var canReplace = false
    private var readClipboardOnFocus = false
    private var requestId = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_translate)

        input = findViewById(R.id.input)
        output = findViewById(R.id.output)
        direction = findViewById(R.id.direction)
        progress = findViewById(R.id.progress)
        btnCopy = findViewById(R.id.btn_copy)
        btnReplace = findViewById(R.id.btn_replace)

        findViewById<Button>(R.id.btn_translate).setOnClickListener { translate() }
        findViewById<Button>(R.id.btn_paste).setOnClickListener { pasteAndTranslate() }
        btnCopy.setOnClickListener { copyResult() }
        btnReplace.setOnClickListener { replaceSelection() }

        OfflineTranslator.prewarm()
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val incoming: CharSequence? = when (intent.action) {
            Intent.ACTION_PROCESS_TEXT -> {
                canReplace = !intent.getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false)
                intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)
            }
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            else -> {
                // Android 10+ only allows clipboard reads while we hold window focus.
                readClipboardOnFocus = true
                null
            }
        }
        setResultState(visible = false)
        if (!incoming.isNullOrBlank()) {
            input.setText(incoming)
            translate()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && readClipboardOnFocus) {
            readClipboardOnFocus = false
            if (input.text.isNullOrBlank()) pasteAndTranslate(silent = true)
        }
    }

    private fun pasteAndTranslate(silent: Boolean = false) {
        val text = clipboard.primaryClip?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)?.coerceToText(this)?.toString()
        if (text.isNullOrBlank()) {
            if (!silent) toast(getString(R.string.clipboard_empty))
            return
        }
        input.setText(text)
        translate()
    }

    private fun translate() {
        val text = TextTools.clean(input.text.toString())
        if (text.isBlank()) {
            toast(getString(R.string.nothing_to_translate))
            return
        }
        val target = TextTools.targetFor(text)
        val dirLabel = getString(if (target == "bn") R.string.en_to_bn else R.string.bn_to_en)
        direction.text = dirLabel
        progress.visibility = View.VISIBLE
        setResultState(visible = false)

        val id = ++requestId
        io.execute {
            val r = runCatching {
                router.translate(text, target) {
                    main.post { if (id == requestId) direction.text = getString(R.string.downloading_model) }
                }
            }
            main.post {
                if (id != requestId || isFinishing || isDestroyed) return@post
                progress.visibility = View.GONE
                r.onSuccess {
                    result = it.text
                    output.text = it.text
                    val engine = getString(if (it.engine == Engine.ONLINE) R.string.engine_online else R.string.engine_offline)
                    direction.text = getString(R.string.direction_engine, dirLabel, engine)
                    setResultState(visible = true)
                }.onFailure {
                    direction.text = dirLabel
                    output.text = getString(R.string.translate_failed)
                    output.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun setResultState(visible: Boolean) {
        output.visibility = if (visible) View.VISIBLE else View.GONE
        btnCopy.visibility = if (visible) View.VISIBLE else View.GONE
        btnReplace.visibility = if (visible && canReplace) View.VISIBLE else View.GONE
    }

    private fun copyResult() {
        clipboard.setPrimaryClip(ClipData.newPlainText("translation", result))
        toast(getString(R.string.copied))
    }

    /** Swaps the selected text in the source app's editable field with the translation. */
    private fun replaceSelection() {
        setResult(RESULT_OK, Intent().putExtra(Intent.EXTRA_PROCESS_TEXT, result))
        finish()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        io.shutdownNow()
        super.onDestroy()
    }
}
