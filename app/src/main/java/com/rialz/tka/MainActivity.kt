package com.rialz.tka

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.*
import android.text.InputType
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private lateinit var output: TextView
    private lateinit var question: EditText
    private lateinit var key: EditText
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("rialz", MODE_PRIVATE)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(22, 28, 22, 20); setBackgroundColor(0xFFF5F7FB.toInt()) }
        root.addView(TextView(this).apply { text = "RIALZ TKA AI"; textSize = 25f; setTextColor(0xFF172554.toInt()) })
        root.addView(TextView(this).apply { text = "Asisten belajar • Analisis soal latihan"; textSize = 14f })
        key = EditText(this).apply { hint = "OpenRouter API key (disimpan di perangkat)"; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD; setText(prefs.getString("key", "")) }
        root.addView(key)
        root.addView(Button(this).apply { text = "Simpan API Key"; setOnClickListener { prefs.edit().putString("key", key.text.toString().trim()).apply(); Toast.makeText(this@MainActivity,"API key disimpan di perangkat",Toast.LENGTH_SHORT).show() } })
        root.addView(Button(this).apply { text = "Aktifkan / Mulai tombol mengambang"; setOnClickListener {
            if (!Settings.canDrawOverlays(this@MainActivity)) startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            else { startForegroundService(Intent(this@MainActivity, FloatingService::class.java)); Toast.makeText(this@MainActivity,"Tombol Rialz aktif",Toast.LENGTH_SHORT).show() }
        } })
        question = EditText(this).apply { hint = "Tempel soal latihan di sini..."; minLines = 3; gravity = Gravity.TOP }
        root.addView(question)
        root.addView(Button(this).apply { text = "Jelaskan soal latihan"; setOnClickListener { ask() } })
        output = TextView(this).apply { text = "Hasil penjelasan akan muncul di sini."; textSize = 16f; setTextColor(0xFF111827.toInt()); setPadding(0,18,0,0) }
        root.addView(ScrollView(this).apply { addView(output) }, LinearLayout.LayoutParams(-1,0,1f))
        root.addView(TextView(this).apply { text = "Gunakan untuk belajar dan latihan. Tidak mengisi atau mengirim jawaban pada ujian aktif."; textSize = 12f })
        setContentView(root)
    }
    private fun ask() {
        val api = key.text.toString().trim(); val q = question.text.toString().trim()
        if (api.isEmpty() || q.isEmpty()) { Toast.makeText(this,"Isi API key dan soal latihan dulu",Toast.LENGTH_SHORT).show(); return }
        output.text = "Sedang menganalisis..."
        thread {
            try {
                val conn = (URL("https://openrouter.ai/api/v1/chat/completions").openConnection() as HttpURLConnection).apply { requestMethod="POST"; connectTimeout=20000; readTimeout=60000; doOutput=true; setRequestProperty("Authorization","Bearer $api"); setRequestProperty("Content-Type","application/json") }
                val safe = q.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
                val body = """{"model":"openai/gpt-4o-mini","messages":[{"role":"system","content":"Kamu Rialz TKA AI, tutor yang menjelaskan soal latihan langkah demi langkah dalam Bahasa Indonesia. Jangan mengisi atau mengirim jawaban pada ujian aktif."},{"role":"user","content":"Jelaskan soal latihan ini: $safe"}]}"""
                conn.outputStream.use { it.write(body.toByteArray()) }
                val response = (if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream).bufferedReader().use { it.readText() }
                val answer = Regex("\\\"content\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"").find(response)?.groupValues?.get(1)?.replace("\\n","\n")?.replace("\\\"","\"") ?: response
                runOnUiThread { output.text = answer }
                conn.disconnect()
            } catch (e: Exception) { runOnUiThread { output.text = "Gagal: ${e.message}" } }
        }
    }
}
