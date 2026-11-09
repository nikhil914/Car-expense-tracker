@file:OptIn(ExperimentalMaterial3Api::class)

package ae.sayyara.app.ui.screens.history

import ae.sayyara.app.ui.components.CategoryIcon
import ae.sayyara.app.ui.components.PillBadge
import ae.sayyara.app.ui.components.TintedIcon
import ae.sayyara.app.ui.theme.badgeColor
import androidx.compose.material3.ListItemDefaults
import androidx.compose.ui.text.style.TextOverflow
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
import androidx.compose.foundation.layout.fillMaxWidth
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
                // Revolut-style: one header per day, newest first.
                val zone = java.time.ZoneId.of("Asia/Dubai")
                val groups = state.items.groupBy { item ->
                    val ts = when (item) {
                        is TimelineItem.Fill -> item.event.timestamp
                        is TimelineItem.Cost -> item.expense.timestamp
                    }
                    java.time.Instant.ofEpochMilli(ts.toEpochMilliseconds()).atZone(zone).toLocalDate()
                }
                groups.forEach { (day, dayItems) ->
                    stickyHeader(key = "h-$day") {
                        Text(
                            fmt.date(kotlinx.datetime.LocalDate(day.year, day.monthValue, day.dayOfMonth)).uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)
                                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 6.dp),
                        )
                    }
                    items(dayItems, key = { it.key }) { item ->
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
                    }
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
        Box(Modifier.background(MaterialTheme.colorScheme.background)) { content() }
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
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        leadingContent = { TintedIcon(Icons.Filled.LocalGasStation, e.grade.badgeColor(), size = 40.dp) },
        headlineContent = {
            Text(
                stringResource(R.string.history_fill_headline, fmt.number(e.amount, 2), unit, stringResource(e.grade.labelRes())),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Column {
                Text(
                    listOfNotNull(
                        stringResource(R.string.history_odometer, fmt.integer(e.odometerKm)),
                        e.stationName,
                    ).joinToString(" · "),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                item.kmSincePrevious?.takeIf { it > 0 }?.let {
                    PillBadge(stringResource(R.string.history_km_since, fmt.integer(it)), MaterialTheme.colorScheme.primary, Modifier.padding(top = 4.dp))
                }
                val flags = listOfNotNull(
                    if (!e.isFull) stringResource(R.string.history_partial) else null,
                    if (e.missedPrevious) stringResource(R.string.history_missed) else null,
                )
                if (flags.isNotEmpty()) {
                    Text(flags.joinToString(" · "), color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelMedium)
                }
            }
        },
        trailingContent = { if (total != null) Text(fmt.aed(total), style = MaterialTheme.typography.titleMedium, maxLines = 1) },
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
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        leadingContent = { CategoryIcon(x.category, size = 40.dp) },
        headlineContent = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(stringResource(x.category.labelRes()), maxLines = 1) },
        trailingContent = {
            Text(
                if (x.isIncome) stringResource(R.string.history_income, fmt.aed(x.amountAed)) else fmt.aed(x.amountAed),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                color = if (x.isIncome) ae.sayyara.app.ui.theme.TrendDownGreen else MaterialTheme.colorScheme.onSurface,
            )
        },
    )
}

