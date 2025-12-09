package com.example.eventmanagement

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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

// Data Class untuk Event
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

// Data Class untuk Statistics
data class Statistics(
    val total: Int,
    val upcoming: Int,
    val ongoing: Int,
    val completed: Int,
    val cancelled: Int
)

class MainActivity : AppCompatActivity() {

    private val API_URL = "http://104.248.153.158/event-api/api.php"
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: EventAdapter
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyView: TextView
    private lateinit var tabLayout: TabLayout
    private lateinit var fabAdd: FloatingActionButton
    private lateinit var statsLayout: LinearLayout
    private lateinit var tvTotal: TextView
    private lateinit var tvUpcoming: TextView
    private lateinit var tvOngoing: TextView
    private lateinit var tvCompleted: TextView

    private var eventsList = mutableListOf<Event>()
    private var currentFilter = "all"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupRecyclerView()
        setupTabLayout()
        setupFab()

        loadEvents()
        loadStatistics()
    }

    private fun initViews() {
        recyclerView = findViewById(R.id.recyclerView)
        progressBar = findViewById(R.id.progressBar)
        emptyView = findViewById(R.id.emptyView)
        tabLayout = findViewById(R.id.tabLayout)
        fabAdd = findViewById(R.id.fabAdd)
        statsLayout = findViewById(R.id.statsLayout)
        tvTotal = findViewById(R.id.tvTotal)
        tvUpcoming = findViewById(R.id.tvUpcoming)
        tvOngoing = findViewById(R.id.tvOngoing)
        tvCompleted = findViewById(R.id.tvCompleted)
    }

    private fun setupRecyclerView() {
        adapter = EventAdapter(eventsList) { event, action ->
            when (action) {
                "view" -> showEventDetail(event)
                "edit" -> showEditDialog(event)
                "delete" -> confirmDelete(event)
            }
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun setupTabLayout() {
        tabLayout.addTab(tabLayout.newTab().setText("Semua"))
        tabLayout.addTab(tabLayout.newTab().setText("Akan Datang"))
        tabLayout.addTab(tabLayout.newTab().setText("Berlangsung"))
        tabLayout.addTab(tabLayout.newTab().setText("Selesai"))

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentFilter = when (tab?.position) {
                    0 -> "all"
                    1 -> "upcoming"
                    2 -> "ongoing"
                    3 -> "completed"
                    else -> "all"
                }
                loadEvents()
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupFab() {
        fabAdd.setOnClickListener {
            showCreateDialog()
        }
    }

    private fun loadEvents() {
        showLoading(true)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = if (currentFilter == "all") {
                    API_URL
                } else {
                    "$API_URL?status=$currentFilter"
                }

                val response = makeGetRequest(url)
                val jsonResponse = JSONObject(response)

                if (jsonResponse.getInt("status") == 200) {
                    val dataArray = jsonResponse.getJSONArray("data")
                    val events = parseEventsFromJson(dataArray)

                    withContext(Dispatchers.Main) {
                        eventsList.clear()
                        eventsList.addAll(events)
                        adapter.notifyDataSetChanged()
                        showLoading(false)
                        updateEmptyView()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        showLoading(false)
                        Toast.makeText(
                            this@MainActivity,
                            "Gagal memuat data",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showLoading(false)
                    Toast.makeText(
                        this@MainActivity,
                        "Error: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun loadStatistics() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = makeGetRequest("$API_URL?stats=1")
                val jsonResponse = JSONObject(response)

                if (jsonResponse.getInt("status") == 200) {
                    val data = jsonResponse.getJSONObject("data")
                    val stats = Statistics(
                        total = data.getString("total").toInt(),
                        upcoming = data.getString("upcoming").toInt(),
                        ongoing = data.getString("ongoing").toInt(),
                        completed = data.getString("completed").toInt(),
                        cancelled = data.getString("cancelled").toInt()
                    )

                    withContext(Dispatchers.Main) {
                        updateStatistics(stats)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun updateStatistics(stats: Statistics) {
        tvTotal.text = stats.total.toString()
        tvUpcoming.text = stats.upcoming.toString()
        tvOngoing.text = stats.ongoing.toString()
        tvCompleted.text = stats.completed.toString()
    }

    private fun showCreateDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_event_form, null)
        val dialog = AlertDialog.Builder(this)
            .setTitle("Buat Event Baru")
            .setView(dialogView)
            .setPositiveButton("Simpan", null)
            .setNegativeButton("Batal", null)
            .create()

        dialog.setOnShowListener {
            val btnSave = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            btnSave.setOnClickListener {
                val title = dialogView.findViewById<EditText>(R.id.etTitle).text.toString()
                val date = dialogView.findViewById<EditText>(R.id.etDate).text.toString()
                val time = dialogView.findViewById<EditText>(R.id.etTime).text.toString()
                val location = dialogView.findViewById<EditText>(R.id.etLocation).text.toString()
                val description = dialogView.findViewById<EditText>(R.id.etDescription).text.toString()
                val capacity = dialogView.findViewById<EditText>(R.id.etCapacity).text.toString()
                val status = dialogView.findViewById<Spinner>(R.id.spinnerStatus).selectedItem.toString()

                if (validateInput(title, date, time, location)) {
                    createEvent(title, date, time, location, description, capacity, status)
                    dialog.dismiss()
                } else {
                    Toast.makeText(this, "Lengkapi semua field!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Setup status spinner
        val spinner = dialogView.findViewById<Spinner>(R.id.spinnerStatus)
        val statuses = arrayOf("upcoming", "ongoing", "completed", "cancelled")
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, statuses)

        dialog.show()
    }

    private fun showEditDialog(event: Event) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_event_form, null)

        // Pre-fill form
        dialogView.findViewById<EditText>(R.id.etTitle).setText(event.title)
        dialogView.findViewById<EditText>(R.id.etDate).setText(event.date)
        dialogView.findViewById<EditText>(R.id.etTime).setText(event.time)
        dialogView.findViewById<EditText>(R.id.etLocation).setText(event.location)
        dialogView.findViewById<EditText>(R.id.etDescription).setText(event.description)
        dialogView.findViewById<EditText>(R.id.etCapacity).setText(event.capacity)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Edit Event")
            .setView(dialogView)
            .setPositiveButton("Update", null)
            .setNegativeButton("Batal", null)
            .create()

        dialog.setOnShowListener {
            val btnUpdate = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            btnUpdate.setOnClickListener {
                val title = dialogView.findViewById<EditText>(R.id.etTitle).text.toString()
                val date = dialogView.findViewById<EditText>(R.id.etDate).text.toString()
                val time = dialogView.findViewById<EditText>(R.id.etTime).text.toString()
                val location = dialogView.findViewById<EditText>(R.id.etLocation).text.toString()
                val description = dialogView.findViewById<EditText>(R.id.etDescription).text.toString()
                val capacity = dialogView.findViewById<EditText>(R.id.etCapacity).text.toString()
                val status = dialogView.findViewById<Spinner>(R.id.spinnerStatus).selectedItem.toString()

                if (validateInput(title, date, time, location)) {
                    updateEvent(event.id, title, date, time, location, description, capacity, status)
                    dialog.dismiss()
                } else {
                    Toast.makeText(this, "Lengkapi semua field!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Setup spinner
        val spinner = dialogView.findViewById<Spinner>(R.id.spinnerStatus)
        val statuses = arrayOf("upcoming", "ongoing", "completed", "cancelled")
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, statuses)
        spinner.setSelection(statuses.indexOf(event.status))

        dialog.show()
    }

    private fun showEventDetail(event: Event) {
        val message = """
            ID: ${event.id}
            Tanggal: ${event.date}
            Waktu: ${event.time}
            Lokasi: ${event.location}
            Deskripsi: ${event.description}
            Kapasitas: ${event.capacity} orang
            Status: ${getStatusLabel(event.status)}
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle(event.title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun confirmDelete(event: Event) {
        AlertDialog.Builder(this)
            .setTitle("Hapus Event")
            .setMessage("Yakin ingin menghapus '${event.title}'?")
            .setPositiveButton("Hapus") { _, _ ->
                deleteEvent(event.id)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun createEvent(
        title: String,
        date: String,
        time: String,
        location: String,
        description: String,
        capacity: String,
        status: String
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val jsonData = JSONObject().apply {
                    put("title", title)
                    put("date", date)
                    put("time", time)
                    put("location", location)
                    put("description", description)
                    put("capacity", capacity.toIntOrNull() ?: 0)
                    put("status", status)
                }

                val response = makePostRequest(API_URL, jsonData.toString())
                val jsonResponse = JSONObject(response)

                withContext(Dispatchers.Main) {
                    if (jsonResponse.getInt("status") == 201) {
                        Toast.makeText(
                            this@MainActivity,
                            "Event berhasil dibuat!",
                            Toast.LENGTH_SHORT
                        ).show()
                        loadEvents()
                        loadStatistics()
                    } else {
                        Toast.makeText(
                            this@MainActivity,
                            "Gagal membuat event",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        "Error: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun updateEvent(
        id: String,
        title: String,
        date: String,
        time: String,
        location: String,
        description: String,
        capacity: String,
        status: String
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val jsonData = JSONObject().apply {
                    put("title", title)
                    put("date", date)
                    put("time", time)
                    put("location", location)
                    put("description", description)
                    put("capacity", capacity.toIntOrNull() ?: 0)
                    put("status", status)
                }

                val response = makePutRequest("$API_URL?id=$id", jsonData.toString())
                val jsonResponse = JSONObject(response)

                withContext(Dispatchers.Main) {
                    if (jsonResponse.getInt("status") == 200) {
                        Toast.makeText(
                            this@MainActivity,
                            "Event berhasil diupdate!",
                            Toast.LENGTH_SHORT
                        ).show()
                        loadEvents()
                        loadStatistics()
                    } else {
                        Toast.makeText(
                            this@MainActivity,
                            "Gagal mengupdate event",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        "Error: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun deleteEvent(id: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = makeDeleteRequest("$API_URL?id=$id")
                val jsonResponse = JSONObject(response)

                withContext(Dispatchers.Main) {
                    if (jsonResponse.getInt("status") == 200) {
                        Toast.makeText(
                            this@MainActivity,
                            "Event berhasil dihapus!",
                            Toast.LENGTH_SHORT
                        ).show()
                        loadEvents()
                        loadStatistics()
                    } else {
                        Toast.makeText(
                            this@MainActivity,
                            "Gagal menghapus event",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        "Error: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun makeGetRequest(urlString: String): String {
        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 10000
        conn.readTimeout = 10000

        return BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
    }

    private fun makePostRequest(urlString: String, jsonData: String): String {
        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true
        conn.connectTimeout = 10000
        conn.readTimeout = 10000

        OutputStreamWriter(conn.outputStream).use { it.write(jsonData) }
        return BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
    }

    private fun makePutRequest(urlString: String, jsonData: String): String {
        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "PUT"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true
        conn.connectTimeout = 10000
        conn.readTimeout = 10000

        OutputStreamWriter(conn.outputStream).use { it.write(jsonData) }
        return BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
    }

    private fun makeDeleteRequest(urlString: String): String {
        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "DELETE"
        conn.connectTimeout = 10000
        conn.readTimeout = 10000

        return BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
    }

    private fun parseEventsFromJson(jsonArray: JSONArray): List<Event> {
        val events = mutableListOf<Event>()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            events.add(
                Event(
                    id = obj.getString("id"),
                    title = obj.getString("title"),
                    date = obj.getString("date"),
                    time = obj.getString("time"),
                    location = obj.getString("location"),
                    description = obj.optString("description", ""),
                    capacity = obj.optString("capacity", "0"),
                    status = obj.getString("status"),
                    createdAt = obj.getString("created_at"),
                    updatedAt = obj.getString("updated_at")
                )
            )
        }
        return events
    }

    private fun validateInput(title: String, date: String, time: String, location: String): Boolean {
        return title.isNotEmpty() && date.isNotEmpty() && time.isNotEmpty() && location.isNotEmpty()
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        recyclerView.visibility = if (show) View.GONE else View.VISIBLE
    }

    private fun updateEmptyView() {
        emptyView.visibility = if (eventsList.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun getStatusLabel(status: String): String {
        return when (status) {
            "upcoming" -> "Akan Datang"
            "ongoing" -> "Berlangsung"
            "completed" -> "Selesai"
            "cancelled" -> "Dibatalkan"
            else -> status
        }
    }
}

// RecyclerView Adapter
class EventAdapter(
    private val events: List<Event>,
    private val onItemAction: (Event, String) -> Unit
) : RecyclerView.Adapter<EventAdapter.EventViewHolder>() {

    class EventViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvId: TextView = view.findViewById(R.id.tvEventId)          // <-- ID
        val tvTitle: TextView = view.findViewById(R.id.tvEventTitle)
        val tvDate: TextView = view.findViewById(R.id.tvEventDate)
        val tvLocation: TextView = view.findViewById(R.id.tvEventLocation)
        val tvStatus: TextView = view.findViewById(R.id.tvEventStatus)
        val btnEdit: ImageButton = view.findViewById(R.id.btnEdit)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_event, parent, false)
        return EventViewHolder(view)
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        val event = events[position]

        holder.tvId.text = "ID: ${event.id}"   // <-- tampilkan ID
        holder.tvTitle.text = event.title
        holder.tvDate.text = "${event.date} | ${event.time}"
        holder.tvLocation.text = event.location
        holder.tvStatus.text = getStatusLabel(event.status)

        // Set status color
        holder.tvStatus.setBackgroundResource(getStatusColor(event.status))

        holder.itemView.setOnClickListener {
            onItemAction(event, "view")
        }

        holder.btnEdit.setOnClickListener {
            onItemAction(event, "edit")
        }

        holder.btnDelete.setOnClickListener {
            onItemAction(event, "delete")
        }
    }

    override fun getItemCount() = events.size

    private fun getStatusLabel(status: String): String {
        return when (status) {
            "upcoming" -> "Akan Datang"
            "ongoing" -> "Berlangsung"
            "completed" -> "Selesai"
            "cancelled" -> "Dibatalkan"
            else -> status
        }
    }

    private fun getStatusColor(status: String): Int {
        return when (status) {
            "upcoming" -> R.drawable.bg_status_upcoming
            "ongoing" -> R.drawable.bg_status_ongoing
            "completed" -> R.drawable.bg_status_completed
            "cancelled" -> R.drawable.bg_status_cancelled
            else -> R.drawable.bg_status_upcoming
        }
    }
}
