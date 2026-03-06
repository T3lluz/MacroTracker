package com.macrotracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Rocket
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.ui.components.ButtonVariant
import com.macrotracker.ui.components.MacroButton
import com.macrotracker.ui.components.MacroCard
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.HeaderColor
import com.macrotracker.ui.theme.Primary
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary

private data class HelpStep(
    val icon: ImageVector,
    val title: String,
    val body: String,
)

private val STEPS = listOf(
    HelpStep(
        icon = Icons.Outlined.Home,
        title = "Log Tab — Add Food",
        body = "Tap the Log tab to see today's progress. Enter a food name (optional), calories and protein, then tap \"Add Log\". Your totals update instantly.",
    ),
    HelpStep(
        icon = Icons.Outlined.CameraAlt,
        title = "Scan a Nutrition Label",
        body = "On the Log tab tap \"📷 Scan Label\" to open the camera. Point it at a nutrition facts label and the app will auto-fill calories and protein for you.",
    ),
    HelpStep(
        icon = Icons.Outlined.AutoAwesome,
        title = "AI Estimate",
        body = "Go to the AI tab and type something like \"2 scrambled eggs\" or \"bowl of oatmeal\". The AI returns a calorie and protein estimate you can log in one tap.",
    ),
    HelpStep(
        icon = Icons.Outlined.BarChart,
        title = "History Tab",
        body = "The History tab shows a bar chart of your last 7, 14, or 30 days. Tap any bar to see the individual food logs for that day.",
    ),
    HelpStep(
        icon = Icons.Outlined.Flag,
        title = "Setting Daily Goals",
        body = "Go to the Stats tab → Daily Goals. Enter your calorie and protein targets and tap \"Save Goals\". Progress bars on the Log screen will reflect your targets.",
    ),
    HelpStep(
        icon = Icons.Outlined.Delete,
        title = "Deleting a Log",
        body = "On the Log tab, swipe left on any food entry in the \"Recent Logs\" list to reveal a delete button.",
    ),
)

private data class FaqItem(val question: String, val answer: String)

private val FAQ = listOf(
    FaqItem(
        question = "Where is my data stored?",
        answer = "All data is stored locally on your device. Nothing is sent to a server (AI requests go to Google Gemini but do not store food data).",
    ),
    FaqItem(
        question = "Why is my progress bar red?",
        answer = "The calorie bar turns red when you have exceeded your daily calorie goal.",
    ),
    FaqItem(
        question = "How accurate are AI estimates?",
        answer = "AI estimates are approximations. For precise tracking always use scanned labels or known values when available. The confidence level shown tells you how certain the AI is.",
    ),
)

@Composable
fun HelpScreen(
    onNavigateBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .padding(bottom = 120.dp),
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        // Header
        Text("Help & How-To", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = HeaderColor)
        Text(
            "Get started in minutes",
            fontSize = 14.sp,
            color = TextSecondary,
            modifier = Modifier.padding(top = 4.dp, bottom = 20.dp),
        )

        // Quick Start card
        MacroCard(delayMs = 60) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 14.dp),
            ) {
                Icon(Icons.Outlined.Rocket, contentDescription = null, tint = Primary, modifier = Modifier.size(17.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Quick Start", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }

            STEPS.forEachIndexed { index, step ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .size(34.dp)
                            .background(Background, RoundedCornerShape(10.dp)),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                    ) {
                        Icon(step.icon, contentDescription = null, tint = Primary, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(step.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.padding(bottom = 3.dp))
                        Text(step.body, fontSize = 13.sp, color = TextSecondary, lineHeight = 18.sp)
                    }
                }
                if (index < STEPS.lastIndex) {
                    HorizontalDivider(color = Border, thickness = 1.dp)
                }
            }
        }

        // FAQ card
        MacroCard(delayMs = 120) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 14.dp),
            ) {
                Icon(Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = null, tint = Primary, modifier = Modifier.size(17.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("FAQ", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }

            FAQ.forEachIndexed { index, item ->
                Column(modifier = Modifier.padding(vertical = 12.dp)) {
                    Text(item.question, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.padding(bottom = 4.dp))
                    Text(item.answer, fontSize = 13.sp, color = TextSecondary, lineHeight = 18.sp)
                }
                if (index < FAQ.lastIndex) {
                    HorizontalDivider(color = Border, thickness = 1.dp)
                }
            }
        }

        MacroButton(
            text = "← Back",
            onClick = onNavigateBack,
            variant = ButtonVariant.SECONDARY,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}




