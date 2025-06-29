package com.massagepro.ui.statistics

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.massagepro.App
import com.massagepro.R
import com.massagepro.databinding.FragmentStatisticsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess
import com.massagepro.data.repository.AppointmentRepository
import com.massagepro.data.repository.ClientRepository
import com.massagepro.data.repository.ServiceRepository

class StatisticsFragment : Fragment() {

    private var _binding: FragmentStatisticsBinding? = null
    private val binding get() = _binding!!
    // ИСПРАВЛЕНО: Теперь ViewModelFactory инициализируется репозиториями, а не DAO
    private val viewModel: StatisticsViewModel by viewModels {
        val database = (requireActivity().application as App).database
        StatisticsViewModelFactory(
            AppointmentRepository(database.appointmentDao(), ServiceRepository(database.serviceDao())), // AppointmentRepo теперь нужен ServiceRepo
            ClientRepository(database.clientDao()),
            ServiceRepository(database.serviceDao())
        )
    }

    private var startDate: Calendar = Calendar.getInstance().apply { add(Calendar.MONTH, -1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
    private var endDate: Calendar = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999) }

    private lateinit var backupLauncher: ActivityResultLauncher<String>
    private lateinit var restoreLauncher: ActivityResultLauncher<Array<String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        backupLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/x-sqlite3")) { uri: Uri? ->
            uri?.let {
                lifecycleScope.launch(Dispatchers.IO) {
                    backupDatabase(it)
                }
            }
        }

        restoreLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            uri?.let {
                showRestoreConfirmationDialog(it)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStatisticsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, systemBars.top, v.paddingRight, v.paddingBottom)
            insets
        }

        setupDatePickers()
        setupActionButtons()
        observeViewModel()
        generateStatistics()
    }

    private fun setupActionButtons() {
        binding.backupButton.setOnClickListener {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "MassagePRO_backup_$timeStamp.db"
            backupLauncher.launch(fileName)
        }
        binding.restoreButton.setOnClickListener {
            restoreLauncher.launch(arrayOf("*/*"))
        }
    }

    private suspend fun backupDatabase(destinationUri: Uri) {
        val app = requireActivity().application as App
        val database = app.database
        val dbFile = requireContext().getDatabasePath(database.openHelper.databaseName)

        try {
            if (database.isOpen) {
                database.query("PRAGMA wal_checkpoint(FULL);", emptyArray()).use {
                    it.moveToFirst()
                }
            }

            requireContext().contentResolver.openOutputStream(destinationUri)?.use { outputStream ->
                FileInputStream(dbFile).use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(requireContext(), getString(R.string.backup_success), Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                val errorMessage = e.message ?: "Невідома помилка"
                Toast.makeText(requireContext(), getString(R.string.backup_error, errorMessage), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showRestoreConfirmationDialog(backupUri: Uri) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.restore_confirmation_title))
            .setMessage(getString(R.string.restore_confirmation_message))
            .setPositiveButton(getString(R.string.restore_action_confirm)) { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    restoreDatabase(backupUri)
                }
            }
            .setNegativeButton(getString(R.string.cancel_button_text), null)
            .show()
    }

    private suspend fun restoreDatabase(backupUri: Uri) {
        val app = requireActivity().application as App
        val dbPath = requireContext().getDatabasePath(app.database.openHelper.databaseName).absolutePath
        val dbFile = File(dbPath)

        val walFile = File("$dbPath-wal")
        val shmFile = File("$dbPath-shm")

        if (app.database.isOpen) {
            app.database.close()
        }

        if (walFile.exists()) {
            walFile.delete()
        }
        if (shmFile.exists()) {
            shmFile.delete()
        }

        try {
            requireContext().contentResolver.openInputStream(backupUri)?.use { inputStream ->
                FileOutputStream(dbFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(requireContext(), getString(R.string.restore_success), Toast.LENGTH_LONG).show()
                restartApp()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                Toast.makeText(requireContext(), getString(R.string.restore_error, e.message), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun restartApp() {
        val context = requireActivity().applicationContext
        val packageManager = context.packageManager
        val intent = packageManager.getLaunchIntentForPackage(context.packageName)
        val componentName = intent!!.component
        val mainIntent = Intent.makeRestartActivityTask(componentName)
        context.startActivity(mainIntent)
        exitProcess(0)
    }

    private fun setupDatePickers() {
        updateDateDisplay()
        binding.buttonSelectStartDateStats.setOnClickListener { showDatePicker(true) }
        binding.buttonSelectEndDateStats.setOnClickListener { showDatePicker(false) }
    }

    private fun showDatePicker(isStartDate: Boolean) {
        val calendar = if (isStartDate) startDate else endDate
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        DatePickerDialog(requireContext(), { _, selectedYear, selectedMonth, selectedDay ->
            if (isStartDate) {
                startDate.set(selectedYear, selectedMonth, selectedDay, 0, 0, 0)
            } else {
                endDate.set(selectedYear, selectedMonth, selectedDay, 23, 59, 59)
            }
            updateDateDisplay()
            generateStatistics()
        }, year, month, day).show()
    }

    private fun updateDateDisplay() {
        val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        binding.buttonSelectStartDateStats.text = dateFormat.format(startDate.time)
        binding.buttonSelectEndDateStats.text = dateFormat.format(endDate.time)
    }

    private fun observeViewModel() {
        viewModel.totalAppointments.observe(viewLifecycleOwner) {
            binding.textViewTotalAppointments.text = "Всього записів: $it"
        }
        viewModel.totalRevenue.observe(viewLifecycleOwner) {
            binding.textViewTotalRevenueStats.text = "Загальна виручка: %.2f грн".format(it)
        }
        viewModel.mostPopularService.observe(viewLifecycleOwner) {
            binding.textViewMostPopularService.text = "Найпопулярніша послуга: $it"
        }
        viewModel.mostActiveClient.observe(viewLifecycleOwner) {
            binding.textViewMostActiveClient.text = "Найактивніший клієнт: $it"
        }
    }

    private fun generateStatistics() {
        viewModel.generateStatistics(startDate.time, endDate.time)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}