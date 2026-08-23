package com.moneymanager.data.repository

import com.moneymanager.data.local.entity.AccountEntity
import com.moneymanager.data.local.entity.CategoryEntity
import com.moneymanager.data.local.entity.TransactionEntity
import com.moneymanager.domain.model.Account
import com.moneymanager.domain.model.Category
import com.moneymanager.domain.model.Transaction

/** Entity → domain mappers. Domain → entity is done inline at each call site. */

fun CategoryEntity.toDomain(): Category =
    Category(id = id, name = name, parentId = parentId, type = type)

fun AccountEntity.toDomain(): Account =
    Account(id = id, name = name, parentId = parentId)

fun TransactionEntity.toDomain(): Transaction =
    Transaction(
        id = id,
        type = type,
        date = date,
        amount = amount,
        categoryId = categoryId,
        accountId = accountId,
        note = note,
    )
