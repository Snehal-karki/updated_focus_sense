package com.example.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.ActivityLogEntity
import com.example.data.model.InstalledAppEntity
import com.example.data.model.RestrictedAppInfo
import com.example.data.model.ScheduleRuleEntity
import com.example.ui.components.ThreatCategoryBadge
import com.example.ui.theme.CoralDanger
import com.example.ui.theme.CoralDangerBg
import com.example.ui.theme.EmeraldSafe
import com.example.ui.theme.EmeraldSafeBg
import com.example.ui.theme.IndigoPrimary
import com.example.util.InstalledAppScanner
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ParentPinDialog(
    onDismiss: () -> Unit,
    onPinVerified: () -> Unit,
    expectedPin: String = "1234"
) {
    var enteredPin by remember { mutableStateOf("") }
    var hasError by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(IndigoPrimary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Parent Lock",
                        tint = IndigoPrimary,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Parent PIN",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "Enter your 4-digit PIN to continue.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 6.dp),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = enteredPin,
                    onValueChange = {
                        if (it.length <= 6) {
                            enteredPin = it
                            hasError = false
                        }
                    },
                    label = { Text("PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    isError = hasError,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("parent_pin_input")
                )

                if (hasError) {
                    Text(
                        text = "Incorrect PIN. Try again.",
                        color = CoralDanger,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("pin_cancel_button")
                    ) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (enteredPin == expectedPin) onPinVerified() else hasError = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("pin_confirm_button")
                    ) {
                        Text("Unlock")
                    }
                }
            }
        }
    }
}

@Composable
fun ChangeParentPinDialog(
    onDismiss: () -> Unit,
    onSavePin: (String) -> Unit
) {
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Update Parent PIN", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = newPin,
                    onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) { newPin = it; errorMsg = null } },
                    label = { Text("New 4–6 digit PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) { confirmPin = it; errorMsg = null } },
                    label = { Text("Confirm PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                errorMsg?.let {
                    Text(it, color = CoralDanger, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when {
                        newPin.length < 4 -> errorMsg = "PIN must be at least 4 digits."
                        newPin != confirmPin -> errorMsg = "PINs do not match."
                        else -> onSavePin(newPin)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary)
            ) {
                Text("Save PIN")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddScheduleRuleDialog(
    existingRule: ScheduleRuleEntity? = null,
    installedApps: List<InstalledAppEntity> = emptyList(),
    onDismiss: () -> Unit,
    onSaveRule: (name: String, category: String, start: String, end: String, days: String, apps: String) -> Unit
) {
    val context = LocalContext.current
    val allWeekdays = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    var ruleName by remember { mutableStateOf(existingRule?.ruleName ?: "Study Focus") }
    var category by remember { mutableStateOf(existingRule?.category ?: "Homework") }
    var startTime by remember { mutableStateOf(existingRule?.startTime ?: "16:00") }
    var endTime by remember { mutableStateOf(existingRule?.endTime ?: "18:00") }
    var selectedDaysSet by remember {
        mutableStateOf(
            existingRule?.dayOfWeek
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it in allWeekdays }
                ?.toSet()
                ?.ifEmpty { allWeekdays.toSet() }
                ?: allWeekdays.toSet()
        )
    }
    var appSearchQuery by remember { mutableStateOf("") }

    val availableApps: List<RestrictedAppInfo> = remember(installedApps) {
        val raw = installedApps.ifEmpty {
            InstalledAppScanner.getInstalledLauncherApps(context, "child-default")
        }
        raw.map { app ->
            RestrictedAppInfo(
                packageName = app.packageName,
                appName = app.appName,
                iconName = "app",
                category = app.category,
                isInstalledOnDevice = true
            )
        }
    }

    val selectedPackages = remember {
        mutableStateListOf<String>().apply {
            val initial = existingRule?.restrictedPackages?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() && it != "*" }
                ?: emptyList()
            addAll(initial)
        }
    }

    val filteredAvailableApps = remember(availableApps, appSearchQuery) {
        if (appSearchQuery.isBlank()) availableApps
        else availableApps.filter {
            it.appName.contains(appSearchQuery, ignoreCase = true) ||
                it.category.contains(appSearchQuery, ignoreCase = true)
        }
    }

    val categories = listOf("Homework", "Study", "Bedtime", "Free Time")

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .padding(vertical = 14.dp)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (existingRule == null) "New Schedule" else "Edit Schedule",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = ruleName,
                    onValueChange = { ruleName = it },
                    label = { Text("Name") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("rule_title_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = IndigoPrimary,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Quick Time Presets
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        "Study 4–6 PM" to ("16:00" to "18:00"),
                        "Bedtime" to ("21:00" to "07:00"),
                        "All Day" to ("00:00" to "23:59")
                    ).forEach { (label, times) ->
                        val active = startTime == times.first && endTime == times.second
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (active) IndigoPrimary else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    startTime = times.first
                                    endTime = times.second
                                }
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (active) Color.White else MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = startTime,
                        onValueChange = { startTime = it },
                        label = { Text("Start") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("rule_start_time_input")
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedTextField(
                        value = endTime,
                        onValueChange = { endTime = it },
                        label = { Text("End") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("rule_end_time_input")
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Interactive Weekday Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Days",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Weekdays",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = IndigoPrimary,
                            modifier = Modifier.clickable {
                                selectedDaysSet = setOf("Mon", "Tue", "Wed", "Thu", "Fri")
                            }
                        )
                        Text(
                            text = "Daily",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = IndigoPrimary,
                            modifier = Modifier.clickable {
                                selectedDaysSet = allWeekdays.toSet()
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    allWeekdays.forEach { day ->
                        val isOn = selectedDaysSet.contains(day)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isOn) IndigoPrimary else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable {
                                    selectedDaysSet = if (isOn && selectedDaysSet.size > 1) {
                                        selectedDaysSet - day
                                    } else {
                                        selectedDaysSet + day
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = day.take(1),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isOn) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Apps (${selectedPackages.size} selected)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = {
                                selectedPackages.clear()
                                selectedPackages.addAll(availableApps.map { it.packageName })
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp)
                        ) {
                            Text("All", fontSize = 11.sp)
                        }
                        TextButton(
                            onClick = { selectedPackages.clear() },
                            contentPadding = PaddingValues(horizontal = 6.dp)
                        ) {
                            Text("Clear", fontSize = 11.sp)
                        }
                    }
                }

                OutlinedTextField(
                    value = appSearchQuery,
                    onValueChange = { appSearchQuery = it },
                    placeholder = { Text("Search apps...", fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (availableApps.isEmpty()) {
                    Text(
                        text = "No apps synced yet — leave empty to block all distracting apps.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 10.dp)
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        for (app in filteredAvailableApps) {
                            val isChecked = selectedPackages.contains(app.packageName)
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isChecked) CoralDangerBg.copy(alpha = 0.5f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (isChecked) selectedPackages.remove(app.packageName)
                                        else selectedPackages.add(app.packageName)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = { checked ->
                                            if (checked) selectedPackages.add(app.packageName)
                                            else selectedPackages.remove(app.packageName)
                                        }
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = app.appName,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp,
                                            color = if (isChecked) CoralDanger else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = app.category,
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (ruleName.isNotBlank()) {
                                val daysCsv = allWeekdays.filter { selectedDaysSet.contains(it) }.joinToString(",")
                                val pkgs = if (selectedPackages.isNotEmpty()) selectedPackages.joinToString(",") else "*"
                                onSaveRule(ruleName.trim(), category, startTime.trim(), endTime.trim(), daysCsv, pkgs)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("save_rule_button")
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}

@Composable
fun LogDetailDialog(
    log: ActivityLogEntity,
    onDismiss: () -> Unit,
    onAcknowledge: () -> Unit,
    onRemove: () -> Unit,
    isParent: Boolean
) {
    val dateStr = SimpleDateFormat("MMM dd • hh:mm a", Locale.getDefault()).format(Date(log.recordedAt))

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = log.appName,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = dateStr,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (log.isFlagged) {
                    ThreatCategoryBadge(
                        category = log.threatCategory,
                        confidence = log.confidenceScore
                    )
                } else {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = EmeraldSafeBg
                    ) {
                        Text(
                            text = "Safe Activity",
                            color = EmeraldSafe,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }

                if (log.contentTitle.isNotBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = log.contentTitle,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (log.extractedText.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = log.extractedText,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                if (log.aiAnalysisSummary.isNotBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = log.aiAnalysisSummary,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 17.sp
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                if (isParent) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(
                            onClick = onRemove,
                            colors = ButtonDefaults.textButtonColors(contentColor = CoralDanger),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("remove_vulnerable_log_button")
                        ) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Delete")
                        }

                        if (!log.isAcknowledged && log.isFlagged) {
                            Button(
                                onClick = onAcknowledge,
                                colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("acknowledge_log_button")
                            ) {
                                Icon(imageVector = Icons.Default.Done, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Acknowledge")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CreateChildAccountDialog(
    onDismiss: () -> Unit,
    onCreateChild: (username: String, password: String, displayName: String) -> Unit,
    isLoading: Boolean = false,
    errorMessage: String? = null
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Add Child",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Child Name") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("child_display_name_input")
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it.lowercase().trim() },
                    label = { Text("Username") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("child_username_input")
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("child_password_input")
                )

                if (!errorMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (username.isNotBlank() && password.isNotBlank() && displayName.isNotBlank()) {
                        onCreateChild(username, password, displayName)
                    }
                },
                enabled = !isLoading && username.isNotBlank() && password.isNotBlank() && displayName.isNotBlank(),
                modifier = Modifier.testTag("submit_create_child_button")
            ) {
                Text(if (isLoading) "Adding..." else "Add Child")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_create_child_button")
            ) {
                Text("Cancel")
            }
        }
    )
}
