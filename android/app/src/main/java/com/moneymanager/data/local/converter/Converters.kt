package com.moneymanager.data.local.converter

import androidx.room.TypeConverter
import com.moneymanager.domain.model.TransactionType
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Room type converters. Amounts are stored as their plain-string form (TEXT),
 * dates as ISO `yyyy-MM-dd`, and the transaction type as its enum name.
 */
class Converters {
    @TypeConverter
    fun fromBigDecimal(value: BigDecimal?): String? = value?.toPlainString()

    @TypeConverter
    fun toBigDecimal(value: String?): BigDecimal? = value?.let(::BigDecimal)

    @TypeConverter
    fun fromLocalDate(value: LocalDate?): String? = value?.toString()

    @TypeConverter
    fun toLocalDate(value: String?): LocalDate? = value?.let(LocalDate::parse)

    @TypeConverter
    fun fromTransactionType(value: TransactionType?): String? = value?.name

    @TypeConverter
    fun toTransactionType(value: String?): TransactionType? =
        value?.let(TransactionType::valueOf)
}
