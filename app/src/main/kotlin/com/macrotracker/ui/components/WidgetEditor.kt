package com.macrotracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Toc
import androidx.compose.material.icons.filled.ViewDay
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.theme.Primary
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary

data class WidgetConfig(val id: String, val label: String, val isVisible: Boolean, val icon: ImageVector)

fun parseWidgetConfig(configStr: String, defaultOrder: List<Triple<String, String, ImageVector>>): List<WidgetConfig> {
    if (configStr.isBlank()) {
        return defaultOrder.map { WidgetConfig(it.first, it.second, true, it.third) }
    }
    val parts = configStr.split(",").mapNotNull { part ->
        val p = part.split(":")
        if (p.size == 2) p[0] to p[1].toBoolean() else null
    }.toMap()

    val configs = mutableListOf<WidgetConfig>()
    val configStrList = configStr.split(",").map { it.split(":")[0] }
    
    // First add saved ones in their order
    for (id in configStrList) {
        val defaultInfo = defaultOrder.find { it.first == id }
        if (defaultInfo != null) {
            configs.add(WidgetConfig(id, defaultInfo.second, parts[id] ?: true, defaultInfo.third))
        }
    }
    
    // Then add any missing ones (e.g. if new features added)
    for (def in defaultOrder) {
        if (!configs.any { it.id == def.first }) {
            configs.add(WidgetConfig(def.first, def.second, true, def.third))
        }
    }
    return configs
}

fun encodeWidgetConfig(configs: List<WidgetConfig>): String {
    return configs.joinToString(",") { "${it.id}:${it.isVisible}" }
}

@Composable
fun WidgetEditor(
    configs: List<WidgetConfig>,
    onConfigsChanged: (List<WidgetConfig>) -> Unit,
    onClose: () -> Unit,
) {
    MacroCard(delayMs = 0) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Edit Layout",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Check, contentDescription = "Done", tint = Primary)
            }
        }
        Text(
            "Toggle visibility and reorder items.",
            fontSize = 14.sp,
            color = TextSecondary,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            configs.forEachIndexed { index, config ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Background)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Icon inside a circle
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Primary.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = config.icon,
                            contentDescription = config.label,
                            tint = Primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Text(
                        text = config.label,
                        fontSize = 16.sp,
                        color = TextPrimary,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )

                    // Compact Reorder Buttons
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = "Move Up",
                            tint = if (index > 0) TextSecondary else TextSecondary.copy(alpha = 0.3f),
                            modifier = Modifier
                                .size(24.dp)
                                .clickable(enabled = index > 0) {
                                    val newList = configs.toMutableList()
                                    val temp = newList[index]
                                    newList[index] = newList[index - 1]
                                    newList[index - 1] = temp
                                    onConfigsChanged(newList)
                                }
                        )
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Move Down",
                            tint = if (index < configs.size - 1) TextSecondary else TextSecondary.copy(alpha = 0.3f),
                            modifier = Modifier
                                .size(24.dp)
                                .clickable(enabled = index < configs.size - 1) {
                                    val newList = configs.toMutableList()
                                    val temp = newList[index]
                                    newList[index] = newList[index + 1]
                                    newList[index + 1] = temp
                                    onConfigsChanged(newList)
                                }
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))
                    
                    Switch(
                        checked = config.isVisible,
                        onCheckedChange = { isChecked ->
                            val newList = configs.toMutableList()
                            newList[index] = config.copy(isVisible = isChecked)
                            onConfigsChanged(newList)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Background,
                            checkedTrackColor = Primary,
                            uncheckedThumbColor = TextSecondary,
                            uncheckedTrackColor = Background
                        ),
                        modifier = Modifier.height(24.dp) // Make the switch slightly more compact
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}