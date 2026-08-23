package com.moneymanager.ui.feature.manage

import com.moneymanager.data.repository.AccountRepositoryImpl
import com.moneymanager.data.repository.CategoryRepositoryImpl
import com.moneymanager.data.repository.FakeAccountDao
import com.moneymanager.data.repository.FakeCategoryDao
import com.moneymanager.data.repository.InMemoryDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The two-level / duplicate / guarded-delete invariants live in (and are tested
 * at) the repository layer; these tests verify the ViewModel delegates to the
 * repositories, groups the results into a two-level tree, and surfaces a rejected
 * action as a message.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ManageViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var db: InMemoryDb
    private lateinit var viewModel: ManageViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        db = InMemoryDb()
        viewModel =
            ManageViewModel(
                CategoryRepositoryImpl(FakeCategoryDao(db)),
                AccountRepositoryImpl(FakeAccountDao(db)),
            )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `adds a category and its child as a two-level group`() =
        runTest(dispatcher) {
            backgroundScope.launch { viewModel.uiState.collect {} }

            viewModel.addCategory("Food", parentId = null)
            // FakeCategoryDao assigns sequential ids from 1, so "Food" is id 1.
            viewModel.addCategory("Dining", parentId = 1)

            val state = viewModel.uiState.value
            assertEquals(1, state.categories.size)
            val food = state.categories.first()
            assertEquals("Food", food.name)
            assertEquals(listOf("Dining"), food.children.map { it.name })
            assertNull(state.message)

        }

    @Test
    fun `a duplicate top-level name surfaces a message`() =
        runTest(dispatcher) {
            backgroundScope.launch { viewModel.uiState.collect {} }

            viewModel.addCategory("Food", parentId = null)
            viewModel.addCategory("Food", parentId = null)

            assertNotNull(viewModel.uiState.value.message)
            assertEquals(1, viewModel.uiState.value.categories.size)

        }

    @Test
    fun `deleting a category with children is blocked and surfaces a message`() =
        runTest(dispatcher) {
            backgroundScope.launch { viewModel.uiState.collect {} }

            viewModel.addCategory("Food", parentId = null)
            viewModel.addCategory("Dining", parentId = 1)
            viewModel.deleteCategory(1)

            assertNotNull(viewModel.uiState.value.message)
            assertEquals(1, viewModel.uiState.value.categories.size)

        }

    @Test
    fun `renames a category`() =
        runTest(dispatcher) {
            backgroundScope.launch { viewModel.uiState.collect {} }

            viewModel.addCategory("Food", parentId = null)
            viewModel.renameCategory(1, "Groceries")

            assertEquals("Groceries", viewModel.uiState.value.categories.first().name)

        }

    @Test
    fun `adds an account under the accounts tab independently of categories`() =
        runTest(dispatcher) {
            backgroundScope.launch { viewModel.uiState.collect {} }

            viewModel.addAccount("Cash", parentId = null)

            assertTrue(viewModel.uiState.value.categories.isEmpty())
            assertEquals(listOf("Cash"), viewModel.uiState.value.accounts.map { it.name })

        }

    @Test
    fun `onMessageShown clears the banner`() =
        runTest(dispatcher) {
            backgroundScope.launch { viewModel.uiState.collect {} }

            viewModel.addCategory("Food", parentId = null)
            viewModel.addCategory("Food", parentId = null)
            assertNotNull(viewModel.uiState.value.message)

            viewModel.onMessageShown()
            assertNull(viewModel.uiState.value.message)

        }
}
