package com.massagepro.ui.statistics

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
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
import com.massagepro.ui.statistics.StatisticsViewModelFactory // ЯВНЫЙ ИМПОРТ StatisticsViewModelFactory
import android.util.Log


// Импорты для MPAndroidChart
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.LegendEntry

// Импорты для ChipGroup
import com.google.android.material.chip.ChipGroup


class StatisticsFragment : Fragment() {

    private var _binding: FragmentStatisticsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: StatisticsViewModel by viewModels {
        val database = (requireActivity().application as App).database
        val clientRepository = ClientRepository(database.clientDao())
        val serviceRepository = ServiceRepository(database.serviceDao())
        StatisticsViewModelFactory(
            AppointmentRepository(database.appointmentDao(), serviceRepository, clientRepository),
            clientRepository,
            serviceRepository
        )
    }

    private var startDate: Calendar = Calendar.getInstance().apply { add(Calendar.MONTH, -1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
    private var endDate: Calendar = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999) }

    private lateinit var backupLauncher: ActivityResultLauncher<String>
    private lateinit var restoreLauncher: ActivityResultLauncher<Array<String>>

    // Объявляем переменные для графиков
    private lateinit var barChartAppointments: BarChart
    private lateinit var pieChartRevenueByCategory: PieChart

    // Объявляем ChipGroup
    private lateinit var chipGroupPeriod: ChipGroup
    private lateinit var currentGrouping: GroupingInterval

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

        // Инициализация графиков
        barChartAppointments = binding.barChartAppointments
        pieChartRevenueByCategory = binding.pieChartRevenueByCategory

        // Инициализация ChipGroup
        chipGroupPeriod = binding.chipGroupPeriod
        currentGrouping = GroupingInterval.DAY // Устанавливаем группировку по умолчанию

        setupDatePickers()
        setupActionButtons()
        setupCharts()
        setupChipGroup() // Вызов метода для настройки ChipGroup
        updatePeriodInfo() // Инициализируем информацию о периоде
        observeViewModel()
        generateStatistics()
    }

    private fun setupChipGroup() {
        // Устанавливаем слушатель для ChipGroup
        chipGroupPeriod.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                val checkedId = checkedIds[0] // Получаем ID выбранного чипа
                updateGroupingInterval(checkedId)
            } else {
                // Если ни один чип не выбран (что не должно произойти с selectionRequired="true")
                // Можно установить значение по умолчанию или ничего не делать
                updateGroupingInterval(R.id.chip_day) // Устанавливаем по умолчанию "День"
                chipGroupPeriod.check(R.id.chip_day)
            }
        }

        // Устанавливаем начальное состояние (по умолчанию "День")
        chipGroupPeriod.check(R.id.chip_day)
    }

    private fun updateGroupingInterval(checkedId: Int) {
        currentGrouping = when (checkedId) {
            R.id.chip_day -> GroupingInterval.DAY
            R.id.chip_week -> GroupingInterval.WEEK
            R.id.chip_month -> GroupingInterval.MONTH
            R.id.chip_year -> GroupingInterval.YEAR
            R.id.chip_all_time -> GroupingInterval.ALL_TIME
            else -> GroupingInterval.DAY // По умолчанию
        }
        updatePeriodInfo() // Обновляем информацию о периоде
        generateStatistics() // Перегенерируем статистику с новой группировкой
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
            Log.e("StatisticsFragment", "Backup failed", e)
            withContext(Dispatchers.Main) {
                val errorMessage = e.message ?: "Невідома ошибка"
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
            Log.e("StatisticsFragment", "Restore failed", e)
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

    @Suppress("SetterInsteadOfProperty") // Подавляем предупреждения для MPAndroidChart сеттеров
    private fun setupCharts() {
        // Настройка BarChart (Количество записей по дням)
        barChartAppointments.description.isEnabled = false
        barChartAppointments.setDrawGridBackground(false)
        barChartAppointments.setDrawBarShadow(false)
        barChartAppointments.setPinchZoom(true)
        barChartAppointments.isDoubleTapToZoomEnabled = false

        // Отключаем легенду для BarChart, так как у нас только один набор данных
        barChartAppointments.legend.isEnabled = false


        // Настройка оси X для BarChart
        val xAxisAppointments = barChartAppointments.xAxis
        xAxisAppointments.position = XAxis.XAxisPosition.BOTTOM
        xAxisAppointments.setDrawGridLines(false)
        xAxisAppointments.setDrawLabels(true)
        xAxisAppointments.granularity = 1f
        xAxisAppointments.setCenterAxisLabels(false)
        xAxisAppointments.setAvoidFirstLastClipping(true)
        xAxisAppointments.textSize = 10f
        xAxisAppointments.labelRotationAngle = -45f


        // Отключаем правую ось Y для BarChart
        barChartAppointments.axisRight.isEnabled = false
        // Настройка левой оси Y для BarChart
        val yAxisAppointments = barChartAppointments.axisLeft
        yAxisAppointments.setDrawGridLines(true)
        yAxisAppointments.granularity = 1f
        yAxisAppointments.axisMinimum = 0f


        // Настройка PieChart (Выручка по категориям)
        pieChartRevenueByCategory.description.isEnabled = false
        pieChartRevenueByCategory.setUsePercentValues(true)
        pieChartRevenueByCategory.setEntryLabelColor(Color.BLACK)
        pieChartRevenueByCategory.setEntryLabelTextSize(12f)
        pieChartRevenueByCategory.setDrawEntryLabels(false)
        pieChartRevenueByCategory.isHighlightPerTapEnabled = true
        pieChartRevenueByCategory.animateY(1400)

        // Настройка легенды для PieChart
        val pieLegend = pieChartRevenueByCategory.legend
        pieLegend.verticalAlignment = Legend.LegendVerticalAlignment.TOP
        pieLegend.horizontalAlignment = Legend.LegendHorizontalAlignment.LEFT // ИСПРАВЛЕНО: Использовать Legend.LegendHorizontalAlignment
        pieLegend.orientation = Legend.LegendOrientation.VERTICAL
        pieLegend.setDrawInside(false)
        pieLegend.xEntrySpace = 7f
        pieLegend.yEntrySpace = 5f
        pieLegend.yOffset = 0f
        pieLegend.textSize = 12f
        pieLegend.textColor = Color.BLACK
        // pieLegend.wordWrapEnabled = true // Этот атрибут может быть не поддерживаем в v3.1.0 или требует определенной настройки
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
        updatePeriodInfo()
    }

    private fun updatePeriodInfo() {
        val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        val groupingText = when (currentGrouping) {
            GroupingInterval.DAY -> "дням"
            GroupingInterval.WEEK -> "тижням"
            GroupingInterval.MONTH -> "місяцям"
            GroupingInterval.YEAR -> "рокам"
            GroupingInterval.ALL_TIME -> "увесь час"
        }
        
        val periodText = if (currentGrouping == GroupingInterval.ALL_TIME) {
            "Період: Увесь час"
        } else {
            "Період: ${dateFormat.format(startDate.time)} - ${dateFormat.format(endDate.time)} (групування по $groupingText)"
        }
        
        binding.textViewPeriodInfo.text = periodText
    }

    private fun observeViewModel() {
        var currentAppointments = 0
        var currentRevenue = 0
        
        viewModel.totalAppointments.observe(viewLifecycleOwner) { appointments ->
            currentAppointments = appointments
            binding.textViewTotalAppointments.text = getString(R.string.total_appointments_prefix, appointments)
            updateAverageCheck(currentAppointments, currentRevenue)
        }
        viewModel.totalRevenue.observe(viewLifecycleOwner) { revenue ->
            currentRevenue = revenue
            binding.textViewTotalRevenueStats.text = getString(R.string.total_revenue_prefix_stats, revenue.toDouble())
            updateAverageCheck(currentAppointments, currentRevenue)
        }
        viewModel.mostPopularService.observe(viewLifecycleOwner) {
            binding.textViewMostPopularService.text = getString(R.string.most_popular_service_prefix, it)
        }
        viewModel.mostActiveClient.observe(viewLifecycleOwner) {
            binding.textViewMostActiveClient.text = getString(R.string.most_active_client_prefix, it)
        }
        // Наблюдаем за данными для BarChart
        viewModel.appointmentsByDate.observe(viewLifecycleOwner) { data ->
            updateBarChartAppointments(data)
        }
        // Наблюдаем за данными для PieChart
        viewModel.revenueByCategory.observe(viewLifecycleOwner) { data ->
            updatePieChartRevenueByCategory(data)
        }
    }
    
    private fun updateAverageCheck(appointments: Int, revenue: Int) {
        val averageCheck = if (appointments > 0) {
            revenue.toDouble() / appointments
        } else {
            0.0
        }
        
        val averageText = "Середній чек: %.2f грн".format(averageCheck)
        
        // Обновляем информацию о периоде, добавляя средний чек
        val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        val groupingText = when (currentGrouping) {
            GroupingInterval.DAY -> "дням"
            GroupingInterval.WEEK -> "тижням"
            GroupingInterval.MONTH -> "місяцям"
            GroupingInterval.YEAR -> "рокам"
            GroupingInterval.ALL_TIME -> "увесь час"
        }
        
        val periodText = if (currentGrouping == GroupingInterval.ALL_TIME) {
            "Період: Увесь час • $averageText"
        } else {
            "Період: ${dateFormat.format(startDate.time)} - ${dateFormat.format(endDate.time)} (групування по $groupingText) • $averageText"
        }
        
        binding.textViewPeriodInfo.text = periodText
    }

    @Suppress("SetterInsteadOfProperty") // Подавляем предупреждения для MPAndroidChart сеттеров
    private fun updateBarChartAppointments(data: Map<String, Int>) {
        // Управляем видимостью графика и сообщения "нет данных"
        if (data.isEmpty()) {
            barChartAppointments.visibility = View.GONE
            binding.textViewNoBarChartData.visibility = View.VISIBLE
        } else {
            barChartAppointments.visibility = View.VISIBLE
            binding.textViewNoBarChartData.visibility = View.GONE

            val entries = ArrayList<BarEntry>()
            val labels = ArrayList<String>()
            var i = 0f
            
            // Форматируем подписи в зависимости от группировки
            val sortedData = data.entries.sortedBy { 
                when (currentGrouping) {
                    GroupingInterval.ALL_TIME -> 0L
                    GroupingInterval.YEAR -> SimpleDateFormat("yyyy", Locale.getDefault()).parse(it.key)?.time ?: 0L
                    GroupingInterval.MONTH -> SimpleDateFormat("yyyy-MM", Locale.getDefault()).parse(it.key)?.time ?: 0L
                    else -> SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(it.key)?.time ?: 0L
                }
            }
            
            sortedData.forEach { (date, count) ->
                entries.add(BarEntry(i, count.toFloat()))
                
                // Форматируем подписи в зависимости от группировки
                val formattedLabel = when (currentGrouping) {
                    GroupingInterval.DAY -> {
                        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(date)?.let {
                            SimpleDateFormat("dd.MM", Locale.getDefault()).format(it)
                        } ?: date
                    }
                    GroupingInterval.WEEK -> {
                        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(date)?.let {
                            SimpleDateFormat("dd.MM", Locale.getDefault()).format(it)
                        } ?: date
                    }
                    GroupingInterval.MONTH -> {
                        SimpleDateFormat("yyyy-MM", Locale.getDefault()).parse(date)?.let {
                            SimpleDateFormat("MM.yyyy", Locale.getDefault()).format(it)
                        } ?: date
                    }
                    GroupingInterval.YEAR -> date
                    GroupingInterval.ALL_TIME -> "Всього"
                }
                
                labels.add(formattedLabel)
                i++
            }

            val dataSet = BarDataSet(entries, "Кількість записів")
            dataSet.color = ContextCompat.getColor(requireContext(), R.color.blue_500)
            dataSet.valueTextColor = Color.BLACK
            dataSet.valueTextSize = 10f

            val barData = BarData(dataSet)
            barData.barWidth = 0.9f
            barChartAppointments.data = barData

            // Устанавливаем форматтер для оси X
            barChartAppointments.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
            
            // Ограничиваем количество подписей для лучшей читаемости
            val maxLabels = when {
                labels.size <= 5 -> labels.size
                labels.size <= 10 -> 5
                labels.size <= 20 -> 7
                else -> 10
            }
            barChartAppointments.xAxis.labelCount = maxLabels
            barChartAppointments.xAxis.setCenterAxisLabels(false)

            barChartAppointments.invalidate()
            barChartAppointments.animateY(1000)
        }
    }

    @Suppress("SetterInsteadOfProperty") // Подавляем предупреждения для MPAndroidChart сеттеров
    private fun updatePieChartRevenueByCategory(data: Map<String, Int>) {
        // Управляем видимостью графика и сообщения "нет данных"
        if (data.isEmpty()) {
            pieChartRevenueByCategory.visibility = View.GONE
            binding.textViewNoPieChartData.visibility = View.VISIBLE
        } else {
            pieChartRevenueByCategory.visibility = View.VISIBLE
            binding.textViewNoPieChartData.visibility = View.GONE

            val entries = ArrayList<PieEntry>()
            val colors = ArrayList<Int>()

            // Генерация цветов для PieChart
            val presetColors = mutableListOf(
                ContextCompat.getColor(requireContext(), R.color.blue_500),
                ContextCompat.getColor(requireContext(), R.color.teal_700),
                ContextCompat.getColor(requireContext(), R.color.purple_500),
                ContextCompat.getColor(requireContext(), R.color.colorBookedSlot),
                ContextCompat.getColor(requireContext(), R.color.purple_200),
                ContextCompat.getColor(requireContext(), R.color.teal_200),
                ContextCompat.getColor(requireContext(), R.color.purple_700)
            )
            // Если количество категорий больше, чем presetColors, MPAndroidChart будет циклически использовать их.

            data.forEach { (category, revenue) ->
                entries.add(PieEntry(revenue.toFloat(), category))
            }

            val dataSet = PieDataSet(entries, "")
            dataSet.sliceSpace = 2f
            dataSet.selectionShift = 5f
            // Автоматическая установка цветов, если их меньше, чем entries, будут циклически повторяться
            for (color in presetColors) {
                colors.add(color)
            }
            dataSet.colors = colors
            dataSet.valueTextColor = Color.BLACK
            dataSet.valueTextSize = 12f

            // Форматтер для отображения процентов
            dataSet.valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return "%.1f%%".format(value)
                }
            }

            val pieData = PieData(dataSet)
            pieChartRevenueByCategory.data = pieData

            // Обновляем легенду с правильными записями (категориями)
            val legendEntries = entries.mapIndexed { index, entry ->
                LegendEntry().apply {
                    label = entry.label
                    formColor = dataSet.colors[index % dataSet.colors.size]
                    form = Legend.LegendForm.SQUARE
                }
            }
            pieChartRevenueByCategory.legend.setCustom(legendEntries)

            pieChartRevenueByCategory.invalidate()
            pieChartRevenueByCategory.animateY(1400)
        }
    }


    private fun generateStatistics() {
        // Передаем текущую выбранную группировку в ViewModel
        viewModel.generateStatistics(startDate.time, endDate.time, currentGrouping)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
