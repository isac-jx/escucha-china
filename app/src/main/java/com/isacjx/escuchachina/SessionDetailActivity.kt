package com.isacjx.escuchachina

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.isacjx.escuchachina.databinding.ActivitySessionDetailBinding

/** Ver una conversación guardada, con botón de idioma y para compartir el texto. */
class SessionDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySessionDetailBinding
    private lateinit var adapter: SegmentAdapter
    private var session: Session? = null
    private var target = Translator.TARGET_ES

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySessionDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val id = intent.getLongExtra("id", -1L)
        session = SessionStore.load(this, id)
        if (session == null) {
            Toast.makeText(this, R.string.not_found, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        supportActionBar?.title = session!!.title()

        adapter = SegmentAdapter(target)
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter
        adapter.submit(session!!.segments)

        binding.toggleLang.check(R.id.btnEs)
        binding.toggleLang.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            target = if (checkedId == R.id.btnEn) Translator.TARGET_EN else Translator.TARGET_ES
            adapter.setTarget(target)
        }

        binding.btnShare.setOnClickListener { shareText() }
    }

    private fun shareText() {
        val s = session ?: return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, s.title())
            putExtra(Intent.EXTRA_TEXT, s.fullText())
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share)))
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
