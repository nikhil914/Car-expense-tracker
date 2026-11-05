@file:OptIn(ExperimentalMaterial3Api::class)

package ae.sayyara.app.ui.screens.history

import ae.sayyara.app.R
import ae.sayyara.app.ui.appViewModel
import ae.sayyara.app.ui.common.labelRes
import ae.sayyara.app.ui.components.EmptyState
import ae.sayyara.app.ui.format.UiFormatter
import ae.sayyara.app.ui.format.rememberUiFormatter
import ae.sayyara.core.model.FuelGrade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

@Composable
fun HistoryScreen(
    onEditFill: (String) -> Unit,
    onEditExpense: (String) -> Unit,
    onAddExpense: () -> Unit,
) {
    val vm = appViewModel { HistoryViewModel(it.container) }
    val state by vm.state.collectAsStateWithLifecycle()
    val fmt = rememberUiFormatter()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_history)) }) },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (state.hasVehicle) {
                FloatingActionButton(onClick = onAddExpense) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.expense_add_title))
                }
            }
        },
    ) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.items.isEmpty() -> EmptyState(
                title = stringResource(R.string.history_empty_title),
                body = stringResource(R.string.history_empty_body),
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = 88.dp),
            ) {
                items(state.items, key = { it.key }) { item ->
                    SwipeToDeleteRow(
                        onDelete = {
                            vm.delete(item)
                            scope.launch {
                                snackbar.currentSnackbarData?.dismiss()
                                val result = snackbar.showSnackbar(
                                    message = context.getString(R.string.history_deleted),
                                    actionLabel = context.getString(R.string.action_undo),
                                    duration = SnackbarDuration.Long,
                                )
                                if (result == SnackbarResult.ActionPerformed) vm.undoDelete(item)
                            }
                        },
                    ) {
                        when (item) {
                            is TimelineItem.Fill -> FillRow(item, fmt) { onEditFill(item.event.id) }
                            is TimelineItem.Cost -> ExpenseRow(item, fmt) { onEditExpense(item.expense.id) }
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun SwipeToDeleteRow(onDelete: () -> Unit, content: @Composable () -> Unit) {
    val dismissState = rememberSwipeToDismissBoxState()
    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) onDelete()
    }
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.action_delete),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
    ) {
        Box(Modifier.background(MaterialTheme.colorScheme.surface)) { content() }
    }
}

@Composable
private fun FillRow(item: TimelineItem.Fill, fmt: UiFormatter, onClick: () -> Unit) {
    val e = item.event
    val electric = e.grade == FuelGrade.ELECTRIC
    val unit = stringResource(if (electric) R.string.unit_kwh else R.string.unit_litres)
    val total = e.totalAed ?: e.unitPriceAed?.let { it * e.amount }
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { Icon(Icons.Filled.LocalGasStation, contentDescription = null) },
        headlineContent = {
            Text(stringResource(R.string.history_fill_headline, fmt.number(e.amount, 2), unit, stringResource(e.grade.labelRes())))
        },
        supportingContent = {
            Column {
                Text(
                    listOfNotNull(
                        fmt.date(e.timestamp),
                        stringResource(R.string.history_odometer, fmt.integer(e.odometerKm)),
                        item.kmSincePrevious?.takeIf { it > 0 }?.let { stringResource(R.string.history_km_since, fmt.integer(it)) },
                        e.stationName,
                    ).joinToString(" · "),
                )
                val flags = listOfNotNull(
                    if (!e.isFull) stringResource(R.string.history_partial) else null,
                    if (e.missedPrevious) stringResource(R.string.history_missed) else null,
                )
                if (flags.isNotEmpty()) {
                    Text(flags.joinToString(" · "), color = MaterialTheme.colorScheme.tertiary)
                }
            }
        },
        trailingContent = { if (total != null) Text(fmt.aed(total), style = MaterialTheme.typography.titleMedium) },
    )
}

@Composable
private fun ExpenseRow(item: TimelineItem.Cost, fmt: UiFormatter, onClick: () -> Unit) {
    val x = item.expense
    val title = x.title?.takeIf { it.isNotBlank() }
        ?: x.customCategory?.takeIf { it.isNotBlank() }
        ?: stringResource(x.category.labelRes())
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { Icon(Icons.Filled.Receipt, contentDescription = null) },
        headlineContent = { Text(title) },
        supportingContent = {
            Row {
                Text(stringResource(x.category.labelRes()))
                Spacer(Modifier.width(8.dp))
                Text(fmt.date(x.timestamp))
            }
        },
        trailingContent = {
            Text(
                if (x.isIncome) stringResource(R.string.history_income, fmt.aed(x.amountAed)) else fmt.aed(x.amountAed),
                style = MaterialTheme.typography.titleMedium,
            )
        },
    )
}

