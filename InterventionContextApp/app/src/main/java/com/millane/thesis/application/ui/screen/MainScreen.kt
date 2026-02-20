package com.millane.thesis.application.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.millane.thesis.application.ui.theme.*

@Composable
fun MainScreen() {
    val pageBackground = PageBackground
    val cardBackground = CardBackground

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBackground)
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Daily Goals - Goal Advancement
        RoundedCard(
            background = cardBackground,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 340.dp)
        ) {
            Text(
                text = "Daily Goals",
                fontSize = 34.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Set or remove your goals for today",
                fontSize = 16.sp,
                color = SecondaryText
            )

            Spacer(Modifier.height(24.dp))

            // TODO: add goals list and add input field to input daily goals
        }

        // Select apps
        RoundedCard(
            background = cardBackground,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 340.dp)
        ) {
            Text(
                text = "Select Apps",
                fontSize = 34.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Select the app(s) you want to intervene.\nYou can only select and submit them once.",
                fontSize = 14.sp,
                color = SecondaryText
            )
            Spacer(Modifier.height(24.dp))

            // TODO: add app icons and radio buttons and submit button for app selection
            // TODO: implement confirmation Dialog to confirm choice
        }
    }
}

@Composable
private fun RoundedCard(
    background: Color,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        color = background,
        shape = RoundedCornerShape(26.dp),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(Modifier.padding(20.dp), content = content)
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun MainScreenPreview() {
    MaterialTheme {
        MainScreen()
    }
}