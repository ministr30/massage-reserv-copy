package com.massagepro.ui.statistics

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.massagepro.App
import com.massagepro.R
import com.massagepro.databinding.FragmentStatisticsBinding
import com.massagepro.data.repository.AppointmentRepository
import com.massagepro.data.repository.ClientRepository
import com.massagepro.data.repository.ServiceRepository
import com.massagepro.ui.statistics.StatisticsViewModelFactory
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.LegendEntry
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.data.Entry
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*

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

    private var startDate: Calendar = Calendar.getInstance()
    private var endDate: Calendar = Calendar.getInstance()
    private lateinit var backupLauncher: ActivityResultLauncher<String>
    private lateinit var restoreLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var barChartAppointments: BarChart
    private lateinit var pieChartRevenueByCategory: PieChart
    private lateinit var chipGroupPeriod: ChipGroup
    private var currentGrouping: GroupingInterval = GroupingInterval.MONTH
    private var currentPeriodType: PeriodType = PeriodType.THREE_MONTHS
    private var lastDrillDown: Pair<PeriodType, GroupingInterval>? = null
    private var lastDrillDownDates: Pair<Calendar, Calendar>? = null
    private var barChartLabels: List<String> = emptyList()
    private val currencyFormat = DecimalFormat("#,##0")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        backupLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/x-sqlite3")) { uri: Uri? ->
            uri?.let {
                lifecycleScope.launch(Dispatchers.IO) { backupDatabase(it) }
            }
        }
        restoreLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            uri?.let { showRestoreConfirmationDialog(it) }
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
        barChartAppointments = binding.barChartAppointments
        pieChartRevenueByCategory = binding.pieChartRevenueByCategory
        chipGroupPeriod = binding.chipGroupPeriod

        setupActionButtons()
        setupCharts()
        setupChipGroup()
        setupCalendarButton()
        updatePeriodInfo()
        observeViewModel()
        setPeriodAndGrouping(PeriodType.THREE_MONTHS)
    }

    private fun setupCalendarButton() {
        binding.buttonCalendar.setOnClickListener {
            showCustomDateRangeDialog()
        }
    }

    private fun setupChipGroup() {
        chipGroupPeriod.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.chip_week -> setPeriodAndGrouping(PeriodType.WEEK)
                R.id.chip_month -> setPeriodAndGrouping(PeriodType.MONTH)
                R.id.chip_3_months -> setPeriodAndGrouping(PeriodType.THREE_MONTHS)
                R.id.chip_6_months -> setPeriodAndGrouping(PeriodType.SIX_MONTHS)
                R.id.chip_year -> setPeriodAndGrouping(PeriodType.YEAR)
                R.id.chip_all_time -> setPeriodAndGrouping(PeriodType.ALL_TIME)
            }
        }
        chipGroupPeriod.check(R.id.chip_3_months)
    }

    private fun setPeriodAndGrouping(periodType: PeriodType) {
        currentPeriodType = periodType
        val now = Calendar.getInstance()
        when (periodType) {
            PeriodType.WEEK -> {
                startDate = Calendar.getInstance().apply {
                    firstDayOfWeek = Calendar.MONDAY
                    set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                    setToStartOfDay()
                }
                endDate = Calendar.getInstance().apply {
                    firstDayOfWeek = Calendar.MONDAY
                    set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
                    setToEndOfDay()
                }
                currentGrouping = GroupingInterval.DAY
            }
            PeriodType.MONTH -> {
                startDate = Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_MONTH, 1)
                    setToStartOfDay()
                }
                endDate = Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
                    setToEndOfDay()
                }
                currentGrouping = GroupingInterval.DAY
            }
            PeriodType.THREE_MONTHS -> {
                val cal = Calendar.getInstance()
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.add(Calendar.MONTH, -2)
                startDate = cal.clone() as Calendar
                startDate.setToStartOfDay()
                endDate = Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
                    setToEndOfDay()
                }
                currentGrouping = GroupingInterval.MONTH
            }
            PeriodType.SIX_MONTHS -> {
                val cal = Calendar.getInstance()
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.add(Calendar.MONTH, -5)
                startDate = cal.clone() as Calendar
                startDate.setToStartOfDay()
                endDate = Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
                    setToEndOfDay()
                }
                currentGrouping = GroupingInterval.MONTH
            }
            PeriodType.YEAR -> {
                startDate = Calendar.getInstance().apply {
                    set(Calendar.MONTH, Calendar.JANUARY)
                    set(Calendar.DAY_OF_MONTH, 1)
                    setToStartOfDay()
                }
                endDate = Calendar.getInstance().apply {
                    set(Calendar.MONTH, Calendar.DECEMBER)
                    set(Calendar.DAY_OF_MONTH, 31)
                    setToEndOfDay()
                }
                currentGrouping = GroupingInterval.MONTH
            }
            PeriodType.ALL_TIME -> {
                startDate = Calendar.getInstance().apply { time = Date(0) }.setToStartOfDay()
                endDate = Calendar.getInstance().setToEndOfDay()
                currentGrouping = GroupingInterval.YEAR
            }
            PeriodType.CUSTOM -> {
                // Для кастомного периода startDate/endDate выставляются вручную
            }
        }
        updateDateDisplay()
        generateStatistics()
    }

    private fun showCustomDateRangeDialog() {
        val now = Calendar.getInstance()
        val start = startDate.clone() as Calendar
        val end = endDate.clone() as Calendar

        DatePickerDialog(requireContext(), { _, year, month, dayOfMonth ->
            start.set(year, month, dayOfMonth, 0, 0, 0)
            DatePickerDialog(requireContext(), { _, year2, month2, dayOfMonth2 ->
                end.set(year2, month2, dayOfMonth2, 23, 59, 59)
                startDate = start
                endDate = end
                currentGrouping = if (daysBetween(start.time, end.time) > 31) GroupingInterval.MONTH else GroupingInterval.DAY
                updateDateDisplay()
                generateStatistics()
                chipGroupPeriod.clearCheck()
            }, end.get(Calendar.YEAR), end.get(Calendar.MONTH), end.get(Calendar.DAY_OF_MONTH)).show()
        }, start.get(Calendar.YEAR), start.get(Calendar.MONTH), start.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun daysBetween(start: Date, end: Date): Int {
        val diff = end.time - start.time
        return (diff / (1000 * 60 * 60 * 24)).toInt()
    }

    private fun setupActionButtons() {
        binding.buttonBackup.setOnClickListener {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "MassagePRO_backup_$timeStamp.db"
            backupLauncher.launch(fileName)
        }
        binding.buttonRestore.setOnClickListener {
            restoreLauncher.launch(arrayOf("*/*"))
        }
    }

    private suspend fun backupDatabase(destinationUri: Uri) {
        val app = requireActivity().application as App
        val database = app.database
        val dbFile = requireContext().getDatabasePath(database.openHelper.databaseName)
        try {
            if (database.isOpen) {
                database.query("PRAGMA wal_checkpoint(FULL);", emptyArray()).use { it.moveToFirst() }
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
        if (app.database.isOpen) app.database.close()
        if (walFile.exists()) walFile.delete()
        if (shmFile.exists()) shmFile.delete()
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
        kotlin.system.exitProcess(0)
    }

    private fun setupCharts() {
        val themeTextColor = getThemeTextColor()
        barChartAppointments.description.isEnabled = false
        barChartAppointments.setDrawGridBackground(false)
        barChartAppointments.setDrawBarShadow(false)
        barChartAppointments.setPinchZoom(true)
        barChartAppointments.isDoubleTapToZoomEnabled = false
        barChartAppointments.legend.isEnabled = false
        val xAxisAppointments = barChartAppointments.xAxis
        xAxisAppointments.position = XAxis.XAxisPosition.BOTTOM
        xAxisAppointments.setDrawGridLines(false)
        xAxisAppointments.setDrawLabels(true)
        xAxisAppointments.granularity = 1f
        xAxisAppointments.setCenterAxisLabels(false)
        xAxisAppointments.setAvoidFirstLastClipping(true)
        xAxisAppointments.textSize = 10f
        xAxisAppointments.labelRotationAngle = -45f
        xAxisAppointments.textColor = themeTextColor
        barChartAppointments.axisRight.isEnabled = false
        val yAxisAppointments = barChartAppointments.axisLeft
        yAxisAppointments.setDrawGridLines(true)
        yAxisAppointments.granularity = 1f
        yAxisAppointments.axisMinimum = 0f
        yAxisAppointments.textColor = themeTextColor
        pieChartRevenueByCategory.description.isEnabled = false
        pieChartRevenueByCategory.setUsePercentValues(true)
        pieChartRevenueByCategory.setEntryLabelColor(themeTextColor)
        pieChartRevenueByCategory.setEntryLabelTextSize(12f)
        pieChartRevenueByCategory.setDrawEntryLabels(false)
        pieChartRevenueByCategory.isHighlightPerTapEnabled = true
        pieChartRevenueByCategory.animateY(1400)
        val pieLegend = pieChartRevenueByCategory.legend
        pieLegend.verticalAlignment = Legend.LegendVerticalAlignment.TOP
        pieLegend.horizontalAlignment = Legend.LegendHorizontalAlignment.LEFT
        pieLegend.orientation = Legend.LegendOrientation.VERTICAL
        pieLegend.setDrawInside(false)
        pieLegend.xEntrySpace = 7f
        pieLegend.yEntrySpace = 5f
        pieLegend.yOffset = 0f
        pieLegend.textSize = 12f
        pieLegend.textColor = themeTextColor
    }

    private fun getThemeTextColor(): Int {
        val typedValue = android.util.TypedValue()
        requireContext().theme.resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)
        return ContextCompat.getColor(requireContext(), typedValue.resourceId)
    }

    private fun updateDateDisplay() {
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
            binding.textViewTotalRevenueStats.text = getString(R.string.total_revenue_prefix_stats, currencyFormat.format(revenue))
            updateAverageCheck(currentAppointments, currentRevenue)
        }
        viewModel.mostPopularService.observe(viewLifecycleOwner) {
            binding.textViewMostPopularService.text = getString(R.string.most_popular_service_prefix, it)
        }
        viewModel.mostActiveClient.observe(viewLifecycleOwner) {
            binding.textViewMostActiveClient.text = getString(R.string.most_active_client_prefix, it)
        }
        viewModel.appointmentsByDate.observe(viewLifecycleOwner) { data ->
            updateBarChartAppointments(data)
        }
        viewModel.revenueByCategory.observe(viewLifecycleOwner) { data ->
            updatePieChartRevenueByCategory(data)
        }
    }

    private fun updateAverageCheck(appointments: Int, revenue: Int) {
        val averageCheck = if (appointments > 0) revenue.toDouble() / appointments else 0.0
        val averageText = "Середній чек: ${currencyFormat.format(averageCheck)} грн"
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

    private fun updateBarChartAppointments(data: Map<String, Int>) {
        val themeTextColor = getThemeTextColor()
        if (data.isEmpty()) {
            barChartAppointments.visibility = View.GONE
            binding.textViewNoBarChartData.visibility = View.VISIBLE
        } else {
            barChartAppointments.visibility = View.VISIBLE
            binding.textViewNoBarChartData.visibility = View.GONE
            val entries = ArrayList<BarEntry>()
            val labels = ArrayList<String>()
            var i = 0f
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
                val formattedLabel = when (currentGrouping) {
                    GroupingInterval.DAY -> SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(date)?.let {
                        SimpleDateFormat("dd.MM", Locale.getDefault()).format(it)
                    } ?: date
                    GroupingInterval.WEEK -> SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(date)?.let {
                        SimpleDateFormat("dd.MM", Locale.getDefault()).format(it)
                    } ?: date
                    GroupingInterval.MONTH -> SimpleDateFormat("yyyy-MM", Locale.getDefault()).parse(date)?.let {
                        SimpleDateFormat("MM.yyyy", Locale.getDefault()).format(it)
                    } ?: date
                    GroupingInterval.YEAR -> date
                    GroupingInterval.ALL_TIME -> "Всього"
                }
                labels.add(formattedLabel)
                i++
            }
            barChartLabels = labels
            val dataSet = BarDataSet(entries, "Кількість записів")
            dataSet.color = ContextCompat.getColor(requireContext(), R.color.blue_500)
            dataSet.valueTextColor = themeTextColor
            dataSet.valueTextSize = 10f
            val barData = BarData(dataSet)
            barData.barWidth = 0.9f
            barChartAppointments.data = barData
            barChartAppointments.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
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
            barChartAppointments.setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
                override fun onValueSelected(e: Entry?, h: Highlight?) {
                    val index = e?.x?.toInt() ?: return
                    val label = barChartLabels.getOrNull(index) ?: return
                    openDrillDownForPeriod(label)
                }
                override fun onNothingSelected() {}
            })
        }
    }

    private fun updatePieChartRevenueByCategory(data: Map<String, Int>) {
        val themeTextColor = getThemeTextColor()
        if (data.isEmpty()) {
            pieChartRevenueByCategory.visibility = View.GONE
            binding.textViewNoPieChartData.visibility = View.VISIBLE
        } else {
            pieChartRevenueByCategory.visibility = View.VISIBLE
            binding.textViewNoPieChartData.visibility = View.GONE
            val entries = ArrayList<PieEntry>()
            val colors = ArrayList<Int>()
            val presetColors = listOf(
                ContextCompat.getColor(requireContext(), R.color.blue_500),
                ContextCompat.getColor(requireContext(), R.color.teal_700),
                ContextCompat.getColor(requireContext(), R.color.purple_500),
                ContextCompat.getColor(requireContext(), R.color.colorBookedSlot),
                ContextCompat.getColor(requireContext(), R.color.purple_200),
                ContextCompat.getColor(requireContext(), R.color.teal_200),
                ContextCompat.getColor(requireContext(), R.color.purple_700)
            )
            data.forEach { (category, revenue) ->
                entries.add(PieEntry(revenue.toFloat(), category))
            }
            colors.addAll(presetColors)
            val dataSet = PieDataSet(entries, "")
            dataSet.sliceSpace = 2f
            dataSet.selectionShift = 5f
            dataSet.colors = colors
            dataSet.valueTextColor = themeTextColor
            dataSet.valueTextSize = 12f
            dataSet.valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return "%.1f%%".format(value)
                }
            }
            val pieData = PieData(dataSet)
            pieChartRevenueByCategory.data = pieData
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

    private fun openDrillDownForPeriod(label: String) {
        lastDrillDown = Pair(currentPeriodType, currentGrouping)
        lastDrillDownDates = Pair(startDate.clone() as Calendar, endDate.clone() as Calendar)
        try {
            when (currentGrouping) {
                GroupingInterval.MONTH -> {
                    val sdf = SimpleDateFormat("MM.yyyy", Locale.getDefault())
                    val parsed = sdf.parse(label)
                    val cal = Calendar.getInstance().apply { time = parsed!! }
                    cal.set(Calendar.DAY_OF_MONTH, 1)
                    startDate = cal.clone() as Calendar
                    endDate = (cal.clone() as Calendar).apply {
                        set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
                        setToEndOfDay()
                    }
                    currentGrouping = GroupingInterval.DAY
                    updateDateDisplay()
                    generateStatistics()
                    showBackButtonForDrillDown()
                }
                else -> {}
            }
        } catch (e: Exception) {}
    }

    private fun showBackButtonForDrillDown() {
        binding.buttonBackDrilldown?.visibility = View.VISIBLE
        binding.buttonBackDrilldown?.setOnClickListener { restoreDrillDown() }
    }

    private fun restoreDrillDown() {
        lastDrillDown?.let { (period, grouping) ->
            currentPeriodType = period
            currentGrouping = grouping
        }
        lastDrillDownDates?.let { (start, end) ->
            startDate = start
            endDate = end
        }
        updateDateDisplay()
        generateStatistics()
        binding.buttonBackDrilldown?.visibility = View.GONE
    }

    private fun generateStatistics() {
        if (currentGrouping == GroupingInterval.ALL_TIME) {
            viewModel.generateStatistics(Date(0), Date(), currentGrouping)
        } else {
            viewModel.generateStatistics(startDate.time, endDate.time, currentGrouping)
        }
    }

    private fun Calendar.setToStartOfDay(): Calendar {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        return this
    }

    private fun Calendar.setToEndOfDay(): Calendar {
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59)
        set(Calendar.MILLISECOND, 999)
        return this
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

enum class PeriodType {
    WEEK, MONTH, THREE_MONTHS, SIX_MONTHS, YEAR, ALL_TIME, CUSTOM
}
