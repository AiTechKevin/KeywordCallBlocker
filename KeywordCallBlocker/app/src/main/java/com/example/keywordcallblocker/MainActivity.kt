package com.example.keywordcallblocker

import android.Manifest
import android.app.TimePickerDialog
import android.app.role.RoleManager
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.provider.ContactsContract
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.switchmaterial.SwitchMaterial
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var config: BlockConfig

    private lateinit var tvSetupStatus: TextView
    private lateinit var switchMaster: SwitchMaterial
    private lateinit var switchSkipCallLog: SwitchMaterial
    private lateinit var etKeyword: EditText
    private lateinit var chipGroupKeywords: ChipGroup
    private lateinit var chipGroupDays: ChipGroup
    private lateinit var btnStartTime: MaterialButton
    private lateinit var btnEndTime: MaterialButton
    private lateinit var tvBlockedLogs: TextView

    private val roleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { updateStatusBanner() }

    private val contactsPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { updateStatusBanner() }

    private val dayEntries = listOf(
        Calendar.SUNDAY to "Sun",
        Calendar.MONDAY to "Mon",
        Calendar.TUESDAY to "Tue",
        Calendar.WEDNESDAY to "Wed",
        Calendar.THURSDAY to "Thu",
        Calendar.FRIDAY to "Fri",
        Calendar.SATURDAY to "Sat"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        config = RuleStorage.load(this)

        tvSetupStatus = findViewById(R.id.tvSetupStatus)
        switchMaster = findViewById(R.id.switchMaster)
        switchSkipCallLog = findViewById(R.id.switchSkipCallLog)
        etKeyword = findViewById(R.id.etKeyword)
        chipGroupKeywords = findViewById(R.id.chipGroupKeywords)
        chipGroupDays = findViewById(R.id.chipGroupDays)
        btnStartTime = findViewById(R.id.btnStartTime)
        btnEndTime = findViewById(R.id.btnEndTime)
        tvBlockedLogs = findViewById(R.id.tvBlockedLogs)

        findViewById<MaterialButton>(R.id.btnGrantRole).setOnClickListener {
            val roleManager = getSystemService(ROLE_SERVICE) as RoleManager
            if (roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
                roleLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
            } else {
                Toast.makeText(this, "Call screening not supported on this device", Toast.LENGTH_LONG).show()
            }
        }

        findViewById<MaterialButton>(R.id.btnGrantContacts).setOnClickListener {
            contactsPermLauncher.launch(Manifest.permission.READ_CONTACTS)
        }

        switchMaster.isChecked = config.enabled
        switchMaster.setOnCheckedChangeListener { _, isChecked ->
            config = config.copy(enabled = isChecked)
            RuleStorage.save(this, config)
        }

        switchSkipCallLog.isChecked = config.skipCallLog
        switchSkipCallLog.setOnCheckedChangeListener { _, isChecked ->
            config = config.copy(skipCallLog = isChecked)
            RuleStorage.save(this, config)
        }

        findViewById<MaterialButton>(R.id.btnAddKeyword).setOnClickListener {
            val word = etKeyword.text.toString().trim()
            if (word.isNotEmpty()) {
                config = config.copy(keywords = config.keywords + word)
                RuleStorage.save(this, config)
                etKeyword.text.clear()
                renderKeywords()
            }
        }

        findViewById<MaterialButton>(R.id.btnPreviewMatches).setOnClickListener {
            previewMatchingContacts()
        }

        btnStartTime.setOnClickListener {
            TimePickerDialog(this, { _, hour, minute ->
                config = config.copy(startHour = hour, startMinute = minute)
                RuleStorage.save(this, config)
                updateTimeButtons()
            }, config.startHour, config.startMinute, false).show()
        }

        btnEndTime.setOnClickListener {
            TimePickerDialog(this, { _, hour, minute ->
                config = config.copy(endHour = hour, endMinute = minute)
                RuleStorage.save(this, config)
                updateTimeButtons()
            }, config.endHour, config.endMinute, false).show()
        }

        findViewById<MaterialButton>(R.id.btnClearLogs).setOnClickListener {
            RuleStorage.clearLogs(this)
            renderLogs()
        }

        renderKeywords()
        renderDays()
        updateTimeButtons()
    }

    override fun onResume() {
        super.onResume()
        updateStatusBanner()
        renderLogs()
    }

    private fun updateStatusBanner() {
        val roleManager = getSystemService(ROLE_SERVICE) as RoleManager
        val hasRole = roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
        val hasContacts = ContextCompat.checkSelfPermission(
            this, Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        if (hasRole && hasContacts) {
            tvSetupStatus.text = "Ready: Call screening active & Contacts permission granted."
            tvSetupStatus.setBackgroundColor(Color.parseColor("#D1E7DD"))
            tvSetupStatus.setTextColor(Color.parseColor("#0F5132"))
        } else {
            val missing = mutableListOf<String>()
            if (!hasRole) missing.add("Default Call Screening Role")
            if (!hasContacts) missing.add("Contacts Permission")
            tvSetupStatus.text = "Action needed: Please grant ${missing.joinToString(" & ")}."
            tvSetupStatus.setBackgroundColor(Color.parseColor("#FFF3CD"))
            tvSetupStatus.setTextColor(Color.parseColor("#664D03"))
        }
    }

    private fun renderKeywords() {
        chipGroupKeywords.removeAllViews()
        config.keywords.sorted().forEach { keyword ->
            val chip = Chip(this).apply {
                text = keyword
                isCloseIconVisible = true
                setOnCloseIconClickListener {
                    config = config.copy(keywords = config.keywords - keyword)
                    RuleStorage.save(this@MainActivity, config)
                    renderKeywords()
                }
            }
            chipGroupKeywords.addView(chip)
        }
    }

    private fun renderDays() {
        chipGroupDays.removeAllViews()
        dayEntries.forEach { (dayConst, label) ->
            val chip = Chip(this).apply {
                text = label
                isCheckable = true
                isChecked = dayConst in config.activeDays
                setOnCheckedChangeListener { _, checked ->
                    val updated = if (checked) config.activeDays + dayConst else config.activeDays - dayConst
                    config = config.copy(activeDays = updated)
                    RuleStorage.save(this@MainActivity, config)
                }
            }
            chipGroupDays.addView(chip)
        }
    }

    private fun updateTimeButtons() {
        btnStartTime.text = String.format(Locale.getDefault(), "Start: %02d:%02d", config.startHour, config.startMinute)
        btnEndTime.text = String.format(Locale.getDefault(), "End: %02d:%02d", config.endHour, config.endMinute)
    }

    private fun renderLogs() {
        val logs = RuleStorage.getBlockedLogs(this)
        tvBlockedLogs.text = if (logs.isEmpty()) "No blocked calls yet." else logs.joinToString("\n\n")
    }

    private fun previewMatchingContacts() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, "Grant Contacts permission first", Toast.LENGTH_SHORT).show()
            return
        }
        if (config.keywords.isEmpty()) {
            Toast.makeText(this, "Add at least one keyword first", Toast.LENGTH_SHORT).show()
            return
        }

        val matches = mutableListOf<String>()
        val projection = arrayOf(ContactsContract.Contacts.DISPLAY_NAME)
        contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            projection,
            null,
            null,
            "${ContactsContract.Contacts.DISPLAY_NAME} ASC"
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(0) ?: continue
                val hit = config.keywords.firstOrNull { name.contains(it, ignoreCase = true) }
                if (hit != null) {
                    matches.add("$name  [matched: \"$hit\"]")
                }
            }
        }

        val message = if (matches.isEmpty()) {
            "No saved contacts match your current keywords."
        } else {
            matches.joinToString("\n")
        }

        AlertDialog.Builder(this)
            .setTitle("Matching Contacts (${matches.size})")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
}
