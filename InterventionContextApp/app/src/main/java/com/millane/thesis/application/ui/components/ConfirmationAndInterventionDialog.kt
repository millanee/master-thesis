package com.millane.thesis.application.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.millane.thesis.application.ui.theme.PrimaryText
import com.millane.thesis.application.ui.theme.SecondaryText

@Composable
fun ConfirmationAndInterventionDialog(
    title: String,
    message: String,
    bullets: List<String> = emptyList(),
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    // styling knobs for reuse
    containerColor: Color = Color(0xFFF6EDED),
    borderColor: Color = Color(0xFFE6A7A7),
    confirmButtonColor: Color = Color(0xFFBFEA8B),
    dismissButtonColor: Color = Color(0xFFE8B8B8),
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = containerColor,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .border(3.dp, borderColor, RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = PrimaryText
                )

                Text(
                    text = message,
                    fontSize = 16.sp,
                    color = SecondaryText,
                    lineHeight = 20.sp
                )

                if (bullets.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        bullets.forEach { b ->
                            Row(verticalAlignment = Alignment.Top) {
                                Text("•", fontSize = 18.sp, color = PrimaryText)
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = b,
                                    fontSize = 18.sp,
                                    color = PrimaryText
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    DialogButton(
                        label = confirmLabel,
                        background = confirmButtonColor,
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f)
                    )
                    DialogButton(
                        label = dismissLabel,
                        background = dismissButtonColor,
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun DialogButton(
    label: String,
    background: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = background,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = modifier.height(44.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(
                text = label,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = PrimaryText
            )
        }
    }
}