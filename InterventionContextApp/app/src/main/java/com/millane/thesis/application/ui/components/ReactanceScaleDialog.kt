package com.millane.thesis.application.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.millane.thesis.application.ui.theme.PrimaryText
import com.millane.thesis.application.ui.theme.SecondaryText

private val REACTANCE_ITEMS = listOf(
    "I want to be in control, not my phone.",
    "I like to act independently from my phone.",
    "I don't want my phone to tell me what to do.",
    "I don't let my phone impose its will on me.",
    "I alone determine what to do, not my phone."
)

private val LIKERT_LABELS = listOf(
    1 to "Strongly disagree",
    2 to "Disagree",
    3 to "Neutral",
    4 to "Agree",
    5 to "Strongly agree"
)

/**
 * Modal dialog with 5 reactance items on a 5-point Likert scale (1–5).
 * Submit is enabled only when all 5 items are answered.
 * Invokes [onSubmit] with a list of 5 Ints (1–5) when the user submits.
 */
@Composable
fun ReactanceScaleDialog(
    selectedPerItem: List<Int?>,
    onSelectionChange: (Int, Int) -> Unit,
    onSubmit: (List<Int>) -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = Color(0xFFF6EDED),
    borderColor: Color = Color(0xFFE6A7A7),
    submitButtonColor: Color = Color(0xFFBFEA8B)
) {
    val allAnswered = selectedPerItem.size == 5 && selectedPerItem.all { it != null }

    Dialog(
        onDismissRequest = { },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = containerColor,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            modifier = modifier
                .fillMaxWidth()
                .border(3.dp, borderColor, RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Please rate your agreement",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = PrimaryText
                )

                Text(
                    text = "For each statement, choose from strongly disagree to strongly agree.",
                    fontSize = 14.sp,
                    color = SecondaryText,
                    lineHeight = 18.sp
                )

                REACTANCE_ITEMS.forEachIndexed { index, statement ->
                    LikertRow(
                        statement = statement,
                        selectedValue = selectedPerItem.getOrNull(index),
                        onSelect = { value -> onSelectionChange(index, value) }
                    )
                }

                Spacer(Modifier.height(8.dp))

                Surface(
                    onClick = { if (allAnswered) onSubmit(selectedPerItem.mapNotNull { it }) },
                    enabled = allAnswered,
                    shape = RoundedCornerShape(12.dp),
                    color = if (allAnswered) submitButtonColor else submitButtonColor.copy(alpha = 0.5f),
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Submit",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = PrimaryText
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LikertRow(
    statement: String,
    selectedValue: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = statement,
            fontSize = 15.sp,
            color = PrimaryText,
            lineHeight = 20.sp
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
                LIKERT_LABELS.forEach { (value, _) ->
                val selected = selectedValue == value
                Surface(
                    onClick = { onSelect(value) },
                    shape = RoundedCornerShape(8.dp),
                    color = if (selected) Color(0xFF2DB6CC).copy(alpha = 0.3f) else Color.Transparent,
                    border = if (selected) null else BorderStroke(1.dp, SecondaryText.copy(alpha = 0.5f)),
                    modifier = Modifier.padding(2.dp)
                ) {
                    Text(
                        text = value.toString(),
                        fontSize = 14.sp,
                        color = PrimaryText,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}
