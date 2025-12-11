package com.example.eventmanagement

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import android.view.ViewGroup

// =================== DATA CLASS ===================
data class Event(
    val id: String,
    val title: String,
    val date: String,
    val time: String,
    val location: String,
    val description: String,
    val capacity: String,
    val status: String,
    val createdAt: String,
    val updatedAt: String
)

// =================== MAIN ACTIVITY ===================
class MainActivity : AppCompatActivity() {

    private val API = "http://104.248.153.158/event-api/api.php"

    private lateinit var recycler: RecyclerView
    private lateinit var adapter: EventAdapter
    private lateinit var progress: ProgressBar
    private lateinit var empty: TextView
    private lateinit var tab: TabLayout
    private lateinit var fab: FloatingActionButton

    private lateinit var tvTotal: TextView
    private lateinit var tvUpcoming: TextView
    private lateinit var tvOngoing: TextView
    private lateinit var tvCompleted: TextView

    private var list = mutableListOf<Event>()
    private var filter = "all"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupRecycler()
        setupTabs()
        setupFab()
        setupSearch()

        loadEvents()
        loadStats()
    }

    private fun initViews() {
        recycler = findViewById(R.id.recyclerView)
        progress = findViewById(R.id.progressBar)
        empty = findViewById(R.id.emptyView)
        fab = findViewById(R.id.fabAdd)
        tab = findViewById(R.id.tabLayout)

        tvTotal = findViewById(R.id.tvTotal)
        tvUpcoming = findViewById(R.id.tvUpcoming)
        tvOngoing = findViewById(R.id.tvOngoing)
        tvCompleted = findViewById(R.id.tvCompleted)
    }

    private fun setupRecycler() {
        adapter = EventAdapter(list) { event, action ->
            when (action) {
                "view" -> showDetail(event)
                "edit" -> showEditDialog(event)
                "delete" -> confirmDelete(event)
            }
        }
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter
    }

    private fun setupTabs() {
        val labels = listOf("Semua", "Akan Datang", "Berlangsung", "Selesai")
        labels.forEach { tab.addTab(tab.newTab().setText(it)) }

        tab.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(t: TabLayout.Tab?) {
                filter = when (t?.position) {
                    0 -> "all"
                    1 -> "upcoming"
                    2 -> "ongoing"
                    3 -> "completed"
                    else -> "all"
                }
                loadEvents()
            }

            override fun onTabUnselected(t: TabLayout.Tab?) {}
            override fun onTabReselected(t: TabLayout.Tab?) {}
        })
    }

    private fun setupFab() {
        fab.setOnClickListener { showCreateDialog() }
    }

    private fun setupSearch() {
        val etId = findViewById<EditText>(R.id.etSearchId)
        val btnId = findViewById<Button>(R.id.btnSearchId)

        btnId.setOnClickListener {
            val id = etId.text.toString().trim()
            if (id.isEmpty()) return@setOnClickListener
            getEventById(id)
        }

        val etDate = findViewById<EditText>(R.id.etSearchDate)
        val btnDate = findViewById<Button>(R.id.btnSearchDate)

        btnDate.setOnClickListener {
            val date = etDate.text.toString().trim()
            if (date.isEmpty()) return@setOnClickListener
            getByDate(date)
        }
    }

    // =================== LOAD ALL EVENTS ===================
    private fun loadEvents() {
        show(true)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = if (filter == "all") API else "$API?status=$filter"
                val json = JSONObject(get(url))

                if (json.getInt("status") == 200) {
                    val arr = json.getJSONArray("data")
                    val data = parseList(arr)

                    withContext(Dispatchers.Main) {
                        list.clear()
                        list.addAll(data)
                        adapter.notifyDataSetChanged()
                        updateEmpty()
                    }
                }
            } finally {
                withContext(Dispatchers.Main) { show(false) }
            }
        }
    }

    // =================== LOAD STATISTICS ===================
    private fun loadStats() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val json = JSONObject(get("$API?stats=1"))
                if (json.getInt("status") == 200) {
                    val d = json.getJSONObject("data")

                    withContext(Dispatchers.Main) {
                        tvTotal.text = d.getString("total")
                        tvUpcoming.text = d.getString("upcoming")
                        tvOngoing.text = d.getString("ongoing")
                        tvCompleted.text = d.getString("completed")
                    }
                }
            } catch (_: Exception) {}
        }
    }

    // =================== GET EVENT BY ID ===================
    private fun getEventById(id: String) {
        show(true)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val json = JSONObject(get("$API?id=$id"))
                val event: Event? =
                    if (json.getInt("status") == 200) {
                        val data = json.get("data")
                        when (data) {
                            is JSONObject -> parseObj(data)
                            is JSONArray -> parseList(data).firstOrNull()
                            else -> null
                        }
                    } else null

                withContext(Dispatchers.Main) {
                    list.clear()
                    if (event != null) list.add(event)
                    adapter.notifyDataSetChanged()
                    updateEmpty()
                }

            } finally {
                withContext(Dispatchers.Main) { show(false) }
            }
        }
    }

    // =================== GET EVENTS BY DATE ===================
    private fun getByDate(date: String) {
        show(true)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val json = JSONObject(get("$API?date=$date"))
                val events = if (json.getInt("status") == 200)
                    parseList(json.getJSONArray("data"))
                else emptyList()

                withContext(Dispatchers.Main) {
                    list.clear()
                    list.addAll(events)
                    adapter.notifyDataSetChanged()
                    updateEmpty()
                }

            } finally {
                withContext(Dispatchers.Main) { show(false) }
            }
        }
    }

    // =================== DETAIL POPUP ===================
    private fun showDetail(e: Event) {
        val msg = """
            ID: ${e.id}
            Tanggal: ${e.date}
            Waktu: ${e.time}
            Lokasi: ${e.location}
            Deskripsi: ${e.description}
            Kapasitas: ${e.capacity}
            Status: ${e.status}
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle(e.title)
            .setMessage(msg)
            .setPositiveButton("OK", null)
            .show()
    }

    // =================== CREATE EVENT ===================
    private fun showCreateDialog() {
        val v = LayoutInflater.from(this).inflate(R.layout.dialog_event_form, null)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Buat Event Baru")
            .setView(v)
            .setPositiveButton("Simpan", null)
            .setNegativeButton("Batal", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val t = v.findViewById<EditText>(R.id.etTitle).text.toString()
                val d = v.findViewById<EditText>(R.id.etDate).text.toString()
                val tm = v.findViewById<EditText>(R.id.etTime).text.toString()
                val loc = v.findViewById<EditText>(R.id.etLocation).text.toString()
                val desc = v.findViewById<EditText>(R.id.etDescription).text.toString()
                val cap = v.findViewById<EditText>(R.id.etCapacity).text.toString()
                val st = v.findViewById<Spinner>(R.id.spinnerStatus).selectedItem.toString()

                createEvent(t, d, tm, loc, desc, cap, st)
                dialog.dismiss()
            }
        }

        val spinner = v.findViewById<Spinner>(R.id.spinnerStatus)
        spinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            listOf("upcoming", "ongoing", "completed", "cancelled")
        )

        dialog.show()
    }

    private fun createEvent(t: String, d: String, tm: String, loc: String, desc: String, cap: String, st: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val json = JSONObject().apply {
                    put("title", t)
                    put("date", d)
                    put("time", tm)
                    put("location", loc)
                    put("description", desc)
                    put("capacity", cap.toIntOrNull() ?: 0)
                    put("status", st)
                }

                requestBody(API, "POST", json.toString())
                withContext(Dispatchers.Main) { loadEvents() }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { toast(e.message!!) }
            }
        }
    }

    // =================== EDIT EVENT ===================
    private fun showEditDialog(e: Event) {
        val v = LayoutInflater.from(this).inflate(R.layout.dialog_event_form, null)

        v.findViewById<EditText>(R.id.etTitle).setText(e.title)
        v.findViewById<EditText>(R.id.etDate).setText(e.date)
        v.findViewById<EditText>(R.id.etTime).setText(e.time)
        v.findViewById<EditText>(R.id.etLocation).setText(e.location)
        v.findViewById<EditText>(R.id.etDescription).setText(e.description)
        v.findViewById<EditText>(R.id.etCapacity).setText(e.capacity)

        val statuses = listOf("upcoming", "ongoing", "completed", "cancelled")
        val spinner = v.findViewById<Spinner>(R.id.spinnerStatus)
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, statuses)
        spinner.setSelection(statuses.indexOf(e.status))

        val dialog = AlertDialog.Builder(this)
            .setTitle("Edit Event")
            .setView(v)
            .setPositiveButton("Update", null)
            .setNegativeButton("Batal", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                updateEvent(
                    e.id,
                    v.findViewById<EditText>(R.id.etTitle).text.toString(),
                    v.findViewById<EditText>(R.id.etDate).text.toString(),
                    v.findViewById<EditText>(R.id.etTime).text.toString(),
                    v.findViewById<EditText>(R.id.etLocation).text.toString(),
                    v.findViewById<EditText>(R.id.etDescription).text.toString(),
                    v.findViewById<EditText>(R.id.etCapacity).text.toString(),
                    spinner.selectedItem.toString()
                )
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun updateEvent(id: String, t: String, d: String, tm: String, loc: String, desc: String, cap: String, st: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val json = JSONObject().apply {
                    put("title", t)
                    put("date", d)
                    put("time", tm)
                    put("location", loc)
                    put("description", desc)
                    put("capacity", cap.toIntOrNull() ?: 0)
                    put("status", st)
                }

                requestBody("$API?id=$id", "PUT", json.toString())
                withContext(Dispatchers.Main) { loadEvents() }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { toast(e.message!!) }
            }
        }
    }

    // =================== DELETE ===================
    private fun confirmDelete(e: Event) {
        AlertDialog.Builder(this)
            .setTitle("Hapus Event")
            .setMessage("Yakin ingin menghapus '${e.title}'?")
            .setPositiveButton("Hapus") { _, _ -> deleteEvent(e.id) }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun deleteEvent(id: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                requestBody("$API?id=$id", "DELETE", "")
                withContext(Dispatchers.Main) { loadEvents() }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { toast(e.message!!) }
            }
        }
    }

    // =================== NETWORK HELPERS ===================
    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        return try {
            c.inputStream.bufferedReader().readText()
        } catch (e: Exception) {
            c.errorStream?.bufferedReader()?.readText() ?: ""
        }
    }

    private fun requestBody(url: String, method: String, json: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.setRequestProperty("Content-Type", "application/json")
        c.doOutput = true

        OutputStreamWriter(c.outputStream).use { it.write(json) }
        return c.inputStream.bufferedReader().readText()
    }

    // =================== HELPERS ===================
    private fun parseList(arr: JSONArray): List<Event> =
        List(arr.length()) { parseObj(arr.getJSONObject(it)) }

    private fun parseObj(o: JSONObject): Event =
        Event(
            o.getString("id"),
            o.getString("title"),
            o.getString("date"),
            o.getString("time"),
            o.getString("location"),
            o.optString("description", ""),
            o.optString("capacity", "0"),
            o.getString("status"),
            o.getString("created_at"),
            o.getString("updated_at")
        )

    private fun show(show: Boolean) {
        progress.visibility = if (show) View.VISIBLE else View.GONE
        recycler.visibility = if (show) View.GONE else View.VISIBLE
    }

    private fun updateEmpty() {
        empty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
