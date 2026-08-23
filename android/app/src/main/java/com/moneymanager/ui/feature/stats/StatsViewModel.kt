package com.moneymanager.ui.feature.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneymanager.domain.model.Category
import com.moneymanager.domain.model.Transaction
import com.moneymanager.domain.repository.CategoryRepository
import com.moneymanager.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

/** One top-level category's share of spending in the selected period. */
data class CategorySlice(
    val categoryId: Long,
    val name: String,
    val amount: BigDecimal,
    /** Share of the period total, 0f..1f. */
    val fraction: Float,
)

/**
 * The period the chart is showing. A [Month] can be stepped with prev/next; a
 * [Custom] range comes from the date-range picker.
 */
sealed interface StatsPeriod {
    data class Month(val yearMonth: YearMonth) : StatsPeriod

    data class Custom(val start: LocalDate, val end: LocalDate) : StatsPeriod

    /** Inclusive first day of the period. */
    val rangeStart: LocalDate
        get() =
            when (this) {
                is Month -> yearMonth.atDay(1)
                is Custom -> start
            }

    /** Inclusive last day of the period. */
    val rangeEnd: LocalDate
        get() =
            when (this) {
                is Month -> yearMonth.atEndOfMonth()
                is Custom -> end
            }
}

data class StatsUiState(
    val loading: Boolean = true,
    val isMonth: Boolean = true,
    val start: LocalDate = LocalDate.now().withDayOfMonth(1),
    val end: LocalDate = LocalDate.now(),
    val total: BigDecimal = BigDecimal.ZERO,
    val slices: List<CategorySlice> = emptyList(),
) {
    val isEmpty: Boolean get() = !loading && slices.isEmpty()
}

/**
 * Aggregates expenses into per-top-level-category spending for a selected period
 * (this month by default). Subcategory spending rolls up into its parent so the
 * chart shows main categories, matching how the picker groups them.
 */
@HiltViewModel
class StatsViewModel
    @Inject
    constructor(
        transactionRepository: TransactionRepository,
        categoryRepository: CategoryRepository,
    ) : ViewModel() {
        private val period = MutableStateFlow<StatsPeriod>(StatsPeriod.Month(YearMonth.now()))

        val uiState: StateFlow<StatsUiState> =
            combine(
                transactionRepository.observeAll(),
                categoryRepository.observeAll(),
                period,
            ) { transactions, categories, selected ->
                buildState(transactions, categories, selected)
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = StatsUiState(),
            )

        /** Step to the previous month; a custom range snaps to its start month first. */
        fun previousMonth() = stepMonth(-1)

        /** Step to the next month; a custom range snaps to its start month first. */
        fun nextMonth() = stepMonth(1)

        private fun stepMonth(delta: Long) {
            val current = period.value
            val base =
                when (current) {
                    is StatsPeriod.Month -> current.yearMonth
                    is StatsPeriod.Custom -> YearMonth.from(current.start)
                }
            period.value = StatsPeriod.Month(base.plusMonths(delta))
        }

        /** Switch the chart to a custom inclusive [start]..[end] range. */
        fun setCustomRange(start: LocalDate, end: LocalDate) {
            val (from, to) = if (start.isAfter(end)) end to start else start to end
            period.value = StatsPeriod.Custom(from, to)
        }

        /** Reset to the current calendar month. */
        fun thisMonth() {
            period.value = StatsPeriod.Month(YearMonth.now())
        }

        private fun buildState(
            transactions: List<Transaction>,
            categories: List<Category>,
            selected: StatsPeriod,
        ): StatsUiState {
            val start = selected.rangeStart
            val end = selected.rangeEnd

            // Map every category to its top-level ancestor (itself when top-level).
            val topLevelOf = categories.associate { it.id to (it.parentId ?: it.id) }
            val nameOf = categories.associate { it.id to it.name }

            val inRange =
                transactions.filter { !it.date.isBefore(start) && !it.date.isAfter(end) }
            val total = inRange.fold(BigDecimal.ZERO) { acc, t -> acc + t.amount }

            val slices =
                if (total.signum() == 0) {
                    emptyList()
                } else {
                    inRange
                        .groupBy { topLevelOf[it.categoryId] ?: it.categoryId }
                        .map { (topId, group) ->
                            val amount = group.fold(BigDecimal.ZERO) { acc, t -> acc + t.amount }
                            CategorySlice(
                                categoryId = topId,
                                name = nameOf[topId] ?: "Unknown",
                                amount = amount,
                                fraction =
                                    amount.divide(total, 6, RoundingMode.HALF_UP).toFloat(),
                            )
                        }
                        .sortedByDescending { it.amount }
                }

            return StatsUiState(
                loading = false,
                isMonth = selected is StatsPeriod.Month,
                start = start,
                end = end,
                total = total,
                slices = slices,
            )
        }
    }
