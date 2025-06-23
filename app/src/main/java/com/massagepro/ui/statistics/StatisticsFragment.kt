package com.massagepro.ui.statistics

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.massagepro.App
import com.massagepro.databinding.FragmentStatisticsBinding
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class StatisticsFragment : Fragment() {

    private var _binding: FragmentStatisticsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: StatisticsViewModel by viewModels { StatisticsViewModelFactory((requireActivity().application as App).database.appointmentDao(), (requireActivity().application as App).database.clientDao(), (requireActivity().application as App).database.serviceDao()) }

    private var startDate: Calendar = Calendar.getInstance().apply { add(Calendar.MONTH, -1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
    private var endDate: Calendar = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999) }

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

        setupDatePickers()
        setupExportButton()
        observeViewModel()
        generateStatistics()
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

    private fun setupExportButton() {
        binding.buttonExportData.setOnClickListener { exportDatabase() }
    }

    private fun observeViewModel() {
        viewModel.totalAppointments.observe(viewLifecycleOwner) {
            binding.textViewTotalAppointments.text = "Всего записей: $it"
        }
        viewModel.totalRevenue.observe(viewLifecycleOwner) {
            binding.textViewTotalRevenueStats.text = "Общая выручка: %.2f грн".format(it)
        }
        viewModel.mostPopularService.observe(viewLifecycleOwner) {
            binding.textViewMostPopularService.text = "Самая популярная услуга: $it"
        }
        viewModel.mostActiveClient.observe(viewLifecycleOwner) {
            binding.textViewMostActiveClient.text = "Самый активный клиент: $it"
        }
    }

    private fun generateStatistics() {
        viewModel.generateStatistics(startDate.time, endDate.time)
    }

    private fun exportDatabase() {
        lifecycleScope.launch {
            try {
                val dbPath = requireContext().getDatabasePath("massagepro_database").absolutePath
                val dbFile = File(dbPath)

                if (dbFile.exists()) {
                    val exportDir = File(requireContext().getExternalFilesDir(null), "MassagePRO_Export")
                    if (!exportDir.exists()) {
                        exportDir.mkdirs()
                    }
                    val exportFile = File(exportDir, "massagepro_database_backup_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Calendar.getInstance().time)}.db")

                    FileInputStream(dbFile).use { fis ->
                        FileOutputStream(exportFile).use { fos ->
                            fis.channel.transferTo(0, fis.channel.size(), fos.channel)
                        }
                    }
                    Toast.makeText(requireContext(), "База данных экспортирована в ${exportFile.absolutePath}", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(requireContext(), "Файл базы данных не найден", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Ошибка экспорта: ${e.message}", Toast.LENGTH_LONG).show()
                e.printStackTrace()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}


