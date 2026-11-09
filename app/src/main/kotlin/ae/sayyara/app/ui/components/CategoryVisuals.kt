package ae.sayyara.app.ui.components

import ae.sayyara.app.ui.theme.color
import ae.sayyara.core.model.ExpenseCategoryKey
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.LocalCarWash
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.TireRepair
import androidx.compose.material.icons.filled.Toll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun ExpenseCategoryKey.icon(): ImageVector = when (this) {
    ExpenseCategoryKey.FUEL -> Icons.Filled.LocalGasStation
    ExpenseCategoryKey.SALIK, ExpenseCategoryKey.DARB -> Icons.Filled.Toll
    ExpenseCategoryKey.PARKING -> Icons.Filled.LocalParking
    ExpenseCategoryKey.MULKIYA -> Icons.Filled.Badge
    ExpenseCategoryKey.INSPECTION -> Icons.AutoMirrored.Filled.FactCheck
    ExpenseCategoryKey.INSURANCE -> Icons.Filled.Shield
    ExpenseCategoryKey.SERVICE -> Icons.Filled.Build
    ExpenseCategoryKey.TYRES -> Icons.Filled.TireRepair
    ExpenseCategoryKey.FINE -> Icons.Filled.Gavel
    ExpenseCategoryKey.WASH -> Icons.Filled.LocalCarWash
    ExpenseCategoryKey.OTHER -> Icons.Filled.MoreHoriz
}

/** Tinted rounded square with an icon (category rows, history rows). */
@Composable
fun TintedIcon(icon: ImageVector, tint: Color, modifier: Modifier = Modifier, size: Dp = 32.dp) {
    Box(
        modifier
            .size(size)
            .background(tint.copy(alpha = 0.14f), androidx.compose.foundation.shape.RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.56f))
    }
}

@Composable
fun CategoryIcon(category: ExpenseCategoryKey, modifier: Modifier = Modifier, size: Dp = 32.dp) =
    TintedIcon(category.icon(), category.color(), modifier, size)
