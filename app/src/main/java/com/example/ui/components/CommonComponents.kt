package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.UserEntity
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.AmberWarningBg
import com.example.ui.theme.CoralDanger
import com.example.ui.theme.CoralDangerBg
import com.example.ui.theme.EmeraldSafe
import com.example.ui.theme.EmeraldSafeBg
import com.example.ui.theme.IndigoDark
import com.example.ui.theme.IndigoPrimary
import com.example.ui.theme.Navy800
import com.example.ui.theme.Navy900
import com.example.ui.theme.PurpleAccent

@Composable
fun FocusSenseTopBar(
    currentUser: UserEntity?,
    children: List<UserEntity>,
    selectedChildId: String,
    onSelectChild: (String) -> Unit,
    onSwitchRoleRequested: () -> Unit,
    onCreateChildRequested: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Surface(
        color = Navy900,
        tonalElevation = 4.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .background(Brush.horizontalGradient(listOf(Navy900, Navy800)))
                .statusBarsPadding()
                .padding(top = 8.dp, bottom = 10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.testTag("app_identity_row")
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(Brush.linearGradient(listOf(IndigoPrimary, IndigoDark))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "FocusSense",
                            tint = Color.White,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "FocusSense",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = Color.White
                        )
                        val isParent = currentUser?.role == "parent"
                        Text(
                            text = if (isParent) "Parent" else "Child",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isParent) PurpleAccent else EmeraldSafe
                        )
                    }
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .clickable { onSwitchRoleRequested() }
                        .testTag("top_bar_profile_chip")
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.White.copy(alpha = 0.14f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Icon(
                                imageVector = if (currentUser?.role == "child") Icons.Default.Lock else Icons.Default.Person,
                                contentDescription = "Profile",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = currentUser?.name?.take(12) ?: "Profile",
                                fontSize = 12.sp,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            if (currentUser?.role == "parent") {
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(children, key = { it.userId }) { child ->
                        val isSelected = child.userId == selectedChildId
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = if (isSelected) IndigoPrimary else Color.White.copy(alpha = 0.1f),
                            modifier = Modifier
                                .heightIn(min = 34.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .then(
                                    if (isSelected) Modifier else Modifier.border(
                                        1.dp,
                                        Color.White.copy(alpha = 0.14f),
                                        RoundedCornerShape(18.dp)
                                    )
                                )
                                .clickable { onSelectChild(child.userId) }
                                .testTag("child_selector_${child.userId}")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) Color.White else EmeraldSafe)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = child.name,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    item {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = Color.White.copy(alpha = 0.18f),
                            modifier = Modifier
                                .heightIn(min = 34.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .clickable { onCreateChildRequested() }
                                .testTag("add_child_account_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add child",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "Add",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ThreatCategoryBadge(
    category: String?,
    confidence: Float,
    modifier: Modifier = Modifier
) {
    val cat = category.orEmpty()
    val isUnsafe = cat.contains("Grooming", ignoreCase = true) ||
        cat.contains("Stranger", ignoreCase = true) ||
        cat.contains("Bullying", ignoreCase = true) ||
        cat.contains("Harassment", ignoreCase = true) ||
        cat.contains("Self-Harm", ignoreCase = true) ||
        cat.contains("Crisis", ignoreCase = true) ||
        cat.contains("Violence", ignoreCase = true) ||
        cat.contains("Explicit", ignoreCase = true) ||
        cat.contains("Adult", ignoreCase = true) ||
        cat.contains("Scam", ignoreCase = true) ||
        cat.contains("Phishing", ignoreCase = true) ||
        cat.contains("Suspicious", ignoreCase = true) ||
        cat.contains("Threat", ignoreCase = true)

    val (bg, textCol, label) = when {
        cat.contains("Self-Harm", ignoreCase = true) || cat.contains("Crisis", ignoreCase = true) ->
            Triple(CoralDangerBg, CoralDanger, "Self-Harm Risk")
        cat.contains("Grooming", ignoreCase = true) || cat.contains("Stranger", ignoreCase = true) ->
            Triple(CoralDangerBg, CoralDanger, "Stranger Risk")
        cat.contains("Bullying", ignoreCase = true) || cat.contains("Harassment", ignoreCase = true) ->
            Triple(CoralDangerBg, CoralDanger, "Bullying")
        cat.contains("Explicit", ignoreCase = true) || cat.contains("Adult", ignoreCase = true) ->
            Triple(CoralDangerBg, CoralDanger, "Adult Content")
        cat.contains("Scam", ignoreCase = true) || cat.contains("Phishing", ignoreCase = true) ->
            Triple(CoralDangerBg, CoralDanger, "Scam Risk")
        cat.contains("Violence", ignoreCase = true) || cat.contains("Threat", ignoreCase = true) ->
            Triple(CoralDangerBg, CoralDanger, "Violence")
        isUnsafe ->
            Triple(CoralDangerBg, CoralDanger, cat)
        cat.contains("Academic", ignoreCase = true) ->
            Triple(AmberWarningBg, AmberWarning, "Distraction")
        else ->
            Triple(EmeraldSafeBg, EmeraldSafe, "Safe")
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = bg,
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(textCol)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = if (isUnsafe) "$label · ${(confidence * 100).toInt()}%" else label,
                color = textCol,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
