package com.isacjx.escuchachina

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contracts.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.isacjx.escuchachina.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: SegmentAdapter

    private var target = Translator.TARGET_ES   // idioma de traducción visible

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* el usuario decide; comprobamos al grabar */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = SegmentAdapter(target)
        binding.recycler.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        binding.recycler.adapter = adapter

        askPermissions()

        // Botón de idioma ES / EN
        binding.toggleLang.check(R.id.btnEs)
        binding.toggleLang.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            target = if (checkedId == R.id.btnEn) Translator.TARGET_EN else Translator.TARGET_ES
            adapter.setTarget(target)
        }

        binding.btnRecord.setOnClickListener { onRecordTapped() }
        binding.btnHistory.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
        binding.btnDownload.setOnClickListener { downloadModels() }

        observeService()
        refreshModelState()
    }

    override fun onResume() {
        super.onResume()
        refreshModelState()
    }

    private fun askPermissions() {
        val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())
    }

    private fun hasMic(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun refreshModelState() {
        val ready = ModelManager.isReady(this)
        binding.setupCard.visibility = if (ready) android.view.View.GONE else android.view.View.VISIBLE
        binding.btnRecord.isEnabled = ready
    }

    private fun onRecordTapped() {
        if (!hasMic()) {
            Toast.makeText(this, R.string.need_mic, Toast.LENGTH_LONG).show()
            askPermissions()
            return
        }
        if (!ModelManager.isReady(this)) {
            Toast.makeText(this, R.string.need_models, Toast.LENGTH_LONG).show()
            return
        }
        if (RecordingService.running.value) {
            val i = Intent(this, RecordingService::class.java).apply { action = RecordingService.ACTION_STOP }
            startService(i)
        } else {
            adapter.submit(emptyList())
            val i = Intent(this, RecordingService::class.java).apply { action = RecordingService.ACTION_START }
            ContextCompat.startForegroundService(this, i)
        }
    }

    private fun observeService() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    RecordingService.running.collect { running ->
                        binding.btnRecord.text =
                            getString(if (running) R.string.stop else R.string.record)
                        binding.btnRecord.setBackgroundColor(
                            ContextCompat.getColor(
                                this@MainActivity,
                                if (running) R.color.stop_red else R.color.record_green
                            )
                        )
                    }
                }
                launch {
                    RecordingService.liveSegments.collect { segs ->
                        adapter.submit(segs)
                        if (segs.isNotEmpty()) binding.recycler.scrollToPosition(segs.size - 1)
                    }
                }
                launch {
                    RecordingService.elapsedSec.collect { sec ->
                        val m = sec / 60
                        val s = sec % 60
                        binding.status.text = getString(R.string.recording_time, m, s)
                    }
                }
                launch {
                    RecordingService.speaking.collect { sp ->
                        binding.dot.visibility =
                            if (sp && RecordingService.running.value) android.view.View.VISIBLE
                            else android.view.View.INVISIBLE
                    }
                }
                launch {
                    RecordingService.statusMsg.collect { msg ->
                        if (msg.isNotBlank()) {
                            Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    private fun downloadModels() {
        binding.btnDownload.isEnabled = false
        binding.downloadProgress.visibility = android.view.View.VISIBLE
        binding.downloadLabel.text = getString(R.string.preparing)

        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    // 1) Modelos de voz
                    ModelManager.downloadMissing(this@MainActivity) { label, frac, idx, total ->
                        runOnUiThread {
                            binding.downloadProgress.progress = (frac * 100).toInt()
                            binding.downloadLabel.text =
                                getString(R.string.downloading_item, idx, total, label, (frac * 100).toInt())
                        }
                    }
                    // 2) Modelos de traducción (español e inglés)
                    runOnUiThread { binding.downloadLabel.text = getString(R.string.downloading_translation) }
                    val t = Translator()
                    t.ensureModel(Translator.TARGET_ES)
                    t.ensureModel(Translator.TARGET_EN)
                    t.close()
                    true
                } catch (e: Throwable) {
                    runOnUiThread {
                        binding.downloadLabel.text = getString(R.string.download_error, e.message ?: "")
                    }
                    false
                }
            }
            binding.btnDownload.isEnabled = true
            binding.downloadProgress.visibility = android.view.View.GONE
            if (ok) {
                Toast.makeText(this@MainActivity, R.string.ready_offline, Toast.LENGTH_LONG).show()
                refreshModelState()
            }
        }
    }
}
