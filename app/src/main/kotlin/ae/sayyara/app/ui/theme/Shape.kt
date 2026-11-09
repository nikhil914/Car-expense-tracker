package ae.sayyara.app.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Outer cards (24 dp). */
val CardShape = RoundedCornerShape(24.dp)

/** Nested stat blocks and input containers (14 dp). */
val NestedShape = RoundedCornerShape(14.dp)

/** Context pills and small containers (10 dp). */
val ContextPillShape = RoundedCornerShape(10.dp)

/** Small chips and plate badges (8 dp). */
val ChipShape = RoundedCornerShape(8.dp)

/** Status badges and fuel grade chips (full pill). */
val PillShape = CircleShape

/** M3 Expressive shape tension mapped onto Material's shape scale. */
val SayyaraShapes = Shapes(
    extraSmall = ChipShape,
    small = ContextPillShape,
    medium = NestedShape,
    large = CardShape,
    extraLarge = RoundedCornerShape(28.dp),
)
