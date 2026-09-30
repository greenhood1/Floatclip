package com.floatclip

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray

object Store {
    const val FREE_LIMIT = 20 // later: raise or remove for paid users

    private fun prefs(c: Context) = c.getSharedPreferences("clips", Context.MODE_PRIVATE)

    fun all(c: Context): MutableList<String> {
        val a = JSONArray(prefs(c).getString("list", "[]"))
        return MutableList(a.length()) { a.getString(it) }
    }

    private fun write(c: Context, l: List<String>) {
        prefs(c).edit().putString("list", JSONArray(l).toString()).apply()
    }

    fun save(c: Context, text: String): Boolean {
        val s = text.trim()
        if (s.isEmpty()) return false
        val l = all(c)
        l.remove(s)
        l.add(0, s)
        while (l.size > FREE_LIMIT) l.removeAt(l.size - 1)
        write(c, l)
        return true
    }

    fun remove(c: Context, index: Int) {
        val l = all(c)
        if (index in l.indices) { l.removeAt(index); write(c, l) }
    }
}

class MainActivity : Activity() {
    private val items = mutableListOf<String>()
    private lateinit var adapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 2, pad, pad)
        }
        root.addView(TextView(this).apply { text = "Float Clip"; textSize = 26f })
        root.addView(TextView(this).apply {
            text = "1. Allow the bubble  2. Start it  3. Copy text in any app, then tap the bubble to save it."
        })
        root.addView(Button(this).apply {
            text = "1. Allow floating bubble"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")))
            }
        })
        root.addView(Button(this).apply {
            text = "2. Start bubble"
            setOnClickListener {
                if (Settings.canDrawOverlays(this@MainActivity)) {
                    startForegroundService(Intent(this@MainActivity, BubbleService::class.java))
                    Toast.makeText(this@MainActivity, "Bubble started", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MainActivity, "Allow the bubble first", Toast.LENGTH_LONG).show()
                }
            }
        })
        root.addView(Button(this).apply {
            text = "Stop bubble"
            setOnClickListener { stopService(Intent(this@MainActivity, BubbleService::class.java)) }
        })
        root.addView(TextView(this).apply {
            text = "Saved clips (tap to copy, hold to delete)"
            setPadding(0, pad, 0, pad / 2)
        })
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, items)
        val list = ListView(this)
        list.adapter = adapter
        list.setOnItemClickListener { _, _, i, _ ->
            val cm = getSystemService(ClipboardManager::class.java)
            cm.setPrimaryClip(ClipData.newPlainText("clip", items[i]))
            Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
        }
        list.setOnItemLongClickListener { _, _, i, _ ->
            Store.remove(this, i); refresh(); true
        }
        root.addView(list)
        setContentView(root)
    }

    override fun onResume() { super.onResume(); refresh() }

    private fun refresh() {
        items.clear(); items.addAll(Store.all(this)); adapter.notifyDataSetChanged()
    }
}

// Invisible screen: reads the clipboard while in front, saves it, then returns to the previous app.
class CaptureActivity : Activity() {
    private var done = false

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !done) {
            done = true
            val cm = getSystemService(ClipboardManager::class.java)
            val text = cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()
            val ok = text != null && Store.save(this, text)
            Toast.makeText(this, if (ok) "Saved" else "Nothing to save", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
