package com.massagepro.ui.statistics

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import com.google.android.material.chip.ChipGroup
import com.massagepro.R
import com.massagepro.data.repository.AppointmentRepository
import com.massagepro.data.repository.ClientRepository
import com.massagepro.data.repository.ServiceRepository
import com.massagepro.databinding.FragmentStatisticsBinding
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*

class StatisticsFragment : Fragment() {

    private var _binding: FragmentStatisticsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: StatisticsViewModel by viewModels {
        StatisticsViewModelFactory(
            AppointmentRepository(),
            ClientRepository(),
            ServiceRepository()
        )
    }

    private var startDate: Calendar = Calendar.getInstance()
    private var endDate: Calendar = Calendar.getInstance()

    private lateinit var barChartAppointments: BarChart
    private lateinit var pieChartRevenueByCategory: PieChart
    private lateinit var chipGroupPeriod: ChipGroup

    private var currentGrouping: GroupingInterval = GroupingInterval.MONTH
    private var currentPeriodType: PeriodType = PeriodType.THREE_MONTHS
    private var lastDrillDown: Pair<PeriodType, GroupingInterval>? = null
    private var lastDrillDownDates: Pair<Calendar, Calendar>? = null
    private var barChartLabels: List<String> = emptyList()
    private val currencyFormat = DecimalFormat("#,##0")

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

        setupCharts()
        setupChipGroup()
        setupCalendarButton()
        updateDateDisplay()
        observeViewModel()
        setPeriodAndGrouping(PeriodType.THREE_MONTHS)
    }

    private fun setupCalendarButton() {
        binding.buttonCalendar.setOnClickListener { showCustomDateRangeDialog() }
    }

    @Suppress("DEPRECATION")
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
            PeriodType.CUSTOM -> {}
        }
        updateDateDisplay()
        generateStatistics()
    }

    private fun showCustomDateRangeDialog() {
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

    private fun setupCharts() {
        val themeTextColor = getThemeTextColor()

        barChartAppointments.apply {
            description.isEnabled = false
            setDrawGridBackground(false)
            setDrawBarShadow(false)
            setPinchZoom(true)
            isDoubleTapToZoomEnabled = false
            legend.isEnabled = false
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                setDrawLabels(true)
                granularity = 1f
                setCenterAxisLabels(false)
                setAvoidFirstLastClipping(true)
                textSize = 10f
                labelRotationAngle = -45f
                textColor = themeTextColor
            }
            axisRight.isEnabled = false
            axisLeft.apply {
                setDrawGridLines(true)
                granularity = 1f
                axisMinimum = 0f
                textColor = themeTextColor
            }
        }

        pieChartRevenueByCategory.apply {
            description.isEnabled = false
            setUsePercentValues(true)
            setEntryLabelColor(themeTextColor)
            setEntryLabelTextSize(12f)
            setDrawEntryLabels(false)
            isHighlightPerTapEnabled = true
            animateY(1400)
            legend.apply {
                verticalAlignment = Legend.LegendVerticalAlignment.TOP
                horizontalAlignment = Legend.LegendHorizontalAlignment.LEFT
                orientation = Legend.LegendOrientation.VERTICAL
                setDrawInside(false)
                xEntrySpace = 7f
                yEntrySpace = 5f
                yOffset = 0f
                textSize = 12f
                textColor = themeTextColor
            }
        }
    }

    private fun getThemeTextColor(): Int {
        val typedValue = android.util.TypedValue()
        requireContext().theme.resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)
        return ContextCompat.getColor(requireContext(), typedValue.resourceId)
    }

    private fun updateDateDisplay() {
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
            binding.textViewTotalRevenueStats.text =
                getString(R.string.total_revenue_prefix_stats, currencyFormat.format(revenue))
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
            return
        }

        barChartAppointments.visibility = View.VISIBLE
        binding.textViewNoBarChartData.visibility = View.GONE

        val entries = ArrayList<BarEntry>()
        val labels = ArrayList<String>()
        var index = 0f

        val sortedData = data.entries.sortedBy {
            when (currentGrouping) {
                GroupingInterval.ALL_TIME -> 0L
                GroupingInterval.YEAR -> SimpleDateFormat("yyyy", Locale.getDefault()).parse(it.key)?.time ?: 0L
                GroupingInterval.MONTH -> SimpleDateFormat("yyyy-MM", Locale.getDefault()).parse(it.key)?.time ?: 0L
                else -> SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(it.key)?.time ?: 0L
            }
        }

        sortedData.forEach { (date, count) ->
            entries.add(BarEntry(index, count.toFloat()))
            val formattedLabel = when (currentGrouping) {
                GroupingInterval.DAY -> SimpleDateFormat("dd.MM", Locale.getDefault()).format(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(date)!!)
                GroupingInterval.WEEK -> SimpleDateFormat("dd.MM", Locale.getDefault()).format(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(date)!!)
                GroupingInterval.MONTH -> SimpleDateFormat("MM.yyyy", Locale.getDefault()).format(SimpleDateFormat("yyyy-MM", Locale.getDefault()).parse(date)!!)
                GroupingInterval.YEAR -> date
                GroupingInterval.ALL_TIME -> "Всього"
            }
            labels.add(formattedLabel)
            index++
        }

        barChartLabels = labels

        val dataSet = BarDataSet(entries, "Кількість записів").apply {
            color = ContextCompat.getColor(requireContext(), R.color.blue_500)
            valueTextColor = themeTextColor
            valueTextSize = 10f
        }

        val barData = BarData(dataSet).apply {
            barWidth = 0.9f
        }

        barChartAppointments.apply {
            setData(barData)
            xAxis.valueFormatter = IndexAxisValueFormatter(labels)
            xAxis.labelCount = labels.size.coerceAtMost(10)
            invalidate()
            animateY(1000)
            setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
                override fun onValueSelected(e: Entry?, h: Highlight?) {
                    val i = e?.x?.toInt() ?: return
                    val label = barChartLabels.getOrNull(i) ?: return
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
            return
        }

        pieChartRevenueByCategory.visibility = View.VISIBLE
        binding.textViewNoPieChartData.visibility = View.GONE

        val entries = data.map { (category, revenue) ->
            PieEntry(revenue.toFloat(), category)
        }

        val colors = listOf(
            ContextCompat.getColor(requireContext(), R.color.blue_500),
            ContextCompat.getColor(requireContext(), R.color.teal_700),
            ContextCompat.getColor(requireContext(), R.color.purple_500),
            ContextCompat.getColor(requireContext(), R.color.colorBookedSlot),
            ContextCompat.getColor(requireContext(), R.color.purple_200),
            ContextCompat.getColor(requireContext(), R.color.teal_200),
            ContextCompat.getColor(requireContext(), R.color.purple_700)
        )

        val dataSet = PieDataSet(entries, "").apply {
            sliceSpace = 2f
            selectionShift = 5f
            this.colors = colors
            valueTextColor = themeTextColor
            valueTextSize = 12f
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String = "%.1f%%".format(value)
            }
        }

        pieChartRevenueByCategory.apply {
            setData(PieData(dataSet))
            legend.isEnabled = true
            invalidate()
            animateY(1400)
        }
    }

    private fun openDrillDownForPeriod(label: String) {
        lastDrillDown = currentPeriodType to currentGrouping
        lastDrillDownDates = startDate.clone() as Calendar to endDate.clone() as Calendar

        try {
            if (currentGrouping == GroupingInterval.MONTH) {
                val sdf = SimpleDateFormat("MM.yyyy", Locale.getDefault())
                val cal = Calendar.getInstance().apply {
                    time = sdf.parse(label)!!
                    set(Calendar.DAY_OF_MONTH, 1)
                }
                startDate = cal.clone() as Calendar
                endDate = (cal.clone() as Calendar).apply {
                    set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
                    setToEndOfDay()
                }
                currentGrouping = GroupingInterval.DAY
                updateDateDisplay()
                generateStatistics()
                binding.buttonBackDrilldown.visibility = View.VISIBLE
                binding.buttonBackDrilldown.setOnClickListener { restoreDrillDown() }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
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
        binding.buttonBackDrilldown.visibility = View.GONE
    }

    private fun generateStatistics() {
        val from = if (currentGrouping == GroupingInterval.ALL_TIME) Date(0) else startDate.time
        val to = endDate.time
        viewModel.generateStatistics(from, to, currentGrouping)
    }

    private fun Calendar.setToStartOfDay(): Calendar = apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    private fun Calendar.setToEndOfDay(): Calendar = apply {
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59)
        set(Calendar.MILLISECOND, 999)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

enum class PeriodType {
    WEEK, MONTH, THREE_MONTHS, SIX_MONTHS, YEAR, ALL_TIME, CUSTOM
}