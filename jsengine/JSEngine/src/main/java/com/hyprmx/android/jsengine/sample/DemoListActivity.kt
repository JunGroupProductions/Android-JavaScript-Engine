package com.hyprmx.android.jsengine.sample

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.TextView
import com.hyprmx.android.jsengine.sample.demos.AsyncPatternsActivity
import com.hyprmx.android.jsengine.sample.demos.BasicUsageActivity
import com.hyprmx.android.jsengine.sample.demos.ErrorHandlingActivity
import com.hyprmx.android.jsengine.sample.demos.MemoryManagementActivity
import com.hyprmx.android.jsengine.sample.demos.PerformanceActivity
import com.hyprmx.android.jsengine.sample.demos.TypeCoercionActivity

/**
 * Launcher screen: a list of the six teaching demos plus the internal R8 regression probe.
 * The probe ([MainActivity]) is intentionally listed last and labeled "Internal" — it's not a
 * demo, but keeping it reachable here lets QA verify it on a minified build without it being the
 * app's entry point.
 */
class DemoListActivity : Activity() {

    private val entries = listOf(
        DemoEntry(R.string.basic_usage_title, R.string.basic_usage_subtitle, BasicUsageActivity::class.java),
        DemoEntry(R.string.async_patterns_title, R.string.async_patterns_subtitle, AsyncPatternsActivity::class.java),
        DemoEntry(R.string.type_coercion_title, R.string.type_coercion_subtitle, TypeCoercionActivity::class.java),
        DemoEntry(
            R.string.memory_management_title,
            R.string.memory_management_subtitle,
            MemoryManagementActivity::class.java
        ),
        DemoEntry(R.string.performance_title, R.string.performance_subtitle, PerformanceActivity::class.java),
        DemoEntry(R.string.error_handling_title, R.string.error_handling_subtitle, ErrorHandlingActivity::class.java),
        DemoEntry(R.string.r8_probe_title, R.string.r8_probe_subtitle, MainActivity::class.java),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTitle(R.string.demo_list_title)
        setContentView(R.layout.activity_demo_list)

        val list = findViewById<ListView>(R.id.demo_list)
        list.adapter = DemoAdapter()
        list.setOnItemClickListener { _, _, position, _ ->
            startActivity(Intent(this, entries[position].target))
        }
    }

    private inner class DemoAdapter : BaseAdapter() {
        override fun getCount(): Int = entries.size
        override fun getItem(position: Int): Any = entries[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(parent.context)
                .inflate(R.layout.list_item_demo, parent, false)
            val entry = entries[position]
            view.findViewById<TextView>(R.id.item_title).setText(entry.titleRes)
            view.findViewById<TextView>(R.id.item_subtitle).setText(entry.subtitleRes)
            return view
        }
    }
}
