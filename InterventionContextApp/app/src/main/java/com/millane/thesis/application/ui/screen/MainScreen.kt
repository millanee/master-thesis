package com.millane.thesis.application.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.millane.thesis.application.R

import com.millane.thesis.application.ui.theme.*

private enum class TargetApp { Instagram, TikTok }

private fun <T> Set<T>.toggle(item: T): Set<T> =
    if (contains(item)) this - item else this + item

@Composable
fun MainScreen() {
    val pageBackground = PageBackground
    val cardBackground = CardBackground

    val scrollState = rememberScrollState()
    var selectedApps by remember { mutableStateOf(setOf<TargetApp>()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBackground)
            .verticalScroll(scrollState)
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(20.dp))
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Top
            ) {
                AppChoice(
                    label = "Instagram",
                    iconRes = R.drawable.instagram_icon,
                    selected = selectedApps.contains(TargetApp.Instagram),
                    onToggle = { selectedApps = selectedApps.toggle(TargetApp.Instagram) }
                )

                AppChoice(
                    label = "TikTok",
                    iconRes = R.drawable.tiktok_icon,
                    selected = selectedApps.contains(TargetApp.TikTok),
                    onToggle = { selectedApps = selectedApps.toggle(TargetApp.TikTok) }
                )
            }
            // TODO: implement submit button
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

@Composable
private fun AppChoice(
    label: String,
    iconRes: Int,
    selected: Boolean,
    onToggle: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.clickable { onToggle() }
    ) {
        Image(
            painter = painterResource(id = iconRes),
            contentDescription = label,
            modifier = Modifier.size(84.dp)
        )

        val ringColor = PrimaryText
        val fillColor = if (selected) SelectionBlue else Color.Transparent

        Spacer(
            modifier = Modifier
                .size(22.dp)
                .border(2.dp, ringColor, CircleShape)
                .clip(CircleShape)
                .background(fillColor)
        )
    }
}


@Preview(name = "Small phone", widthDp = 320, heightDp = 800, showBackground = true)
@Composable
private fun PreviewSmallPhone() {
    InterventionContextAppTheme { MainScreen() }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun MainScreenPreview() {
    MaterialTheme {
        MainScreen()
    }
}