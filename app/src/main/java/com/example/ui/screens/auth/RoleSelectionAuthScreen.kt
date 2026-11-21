package com.example.ui.screens.auth

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.CoralDanger
import com.example.ui.theme.CoralDangerBg
import com.example.ui.theme.EmeraldSafe
import com.example.ui.theme.IndigoDark
import com.example.ui.theme.IndigoPrimary
import com.example.ui.theme.Navy800
import com.example.ui.theme.Navy900
import com.example.ui.viewmodel.FocusSenseViewModel

enum class SelectedRoleView {
    ROLE_PICKER,
    PARENT_FLOW,
    CHILD_FLOW
}

@Composable
fun RoleSelectionAuthScreen(
    viewModel: FocusSenseViewModel,
    modifier: Modifier = Modifier
) {
    val isAuthLoading by viewModel.isAuthLoading.collectAsStateWithLifecycle()
    val authError by viewModel.authError.collectAsStateWithLifecycle()

    var currentView by remember { mutableStateOf(SelectedRoleView.ROLE_PICKER) }

    BackHandler(enabled = currentView != SelectedRoleView.ROLE_PICKER) {
        viewModel.clearAuthError()
        currentView = SelectedRoleView.ROLE_PICKER
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            AuthBrandHeader(
                currentView = currentView,
                onBack = {
                    viewModel.clearAuthError()
                    currentView = SelectedRoleView.ROLE_PICKER
                }
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 20.dp)
            ) {
                AnimatedContent(
                    targetState = currentView,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "RoleViewTransition"
                ) { targetView ->
                    when (targetView) {
                        SelectedRoleView.ROLE_PICKER -> {
                            RolePickerView(
                                onSelectParent = {
                                    viewModel.clearAuthError()
                                    currentView = SelectedRoleView.PARENT_FLOW
                                },
                                onSelectChild = {
                                    viewModel.clearAuthError()
                                    currentView = SelectedRoleView.CHILD_FLOW
                                }
                            )
                        }
                        SelectedRoleView.PARENT_FLOW -> {
                            ParentAuthView(
                                isAuthLoading = isAuthLoading,
                                authError = authError,
                                onSignIn = { email, pass ->
                                    viewModel.signInParent(email, pass)
                                },
                                onSignUp = { name, family, email, pass, pin ->
                                    viewModel.signUpParent(name, family, email, pass, pin)
                                },
                                onClearError = { viewModel.clearAuthError() }
                            )
                        }
                        SelectedRoleView.CHILD_FLOW -> {
                            ChildPairingView(
                                isAuthLoading = isAuthLoading,
                                authError = authError,
                                onPairWithCode = { code, childName, devName ->
                                    viewModel.pairChildWithCode(code, childName, devName)
                                },
                                onPairChild = { parentEmail, parentPass, childName, deviceName ->
                                    viewModel.pairChildDevice(parentEmail, parentPass, childName, deviceName)
                                },
                                onClearError = { viewModel.clearAuthError() }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AuthBrandHeader(
    currentView: SelectedRoleView,
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Navy900, Navy800, IndigoDark)
                )
            )
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentView != SelectedRoleView.ROLE_PICKER) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("auth_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to role selection",
                            tint = Color.White
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(48.dp))
                }
                Spacer(modifier = Modifier.weight(1f))
            }

            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(listOf(IndigoPrimary, IndigoDark))),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = "FocusSense",
                    tint = Color.White,
                    modifier = Modifier.size(30.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "FocusSense",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Text(
                text = when (currentView) {
                    SelectedRoleView.ROLE_PICKER -> "Family safety & mindful focus"
                    SelectedRoleView.PARENT_FLOW -> "Parent Account"
                    SelectedRoleView.CHILD_FLOW -> "Pair Child's Phone"
                },
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.78f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun RolePickerView(
    onSelectParent: () -> Unit,
    onSelectChild: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Choose Device Role",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Set up this phone as a parent or child device.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(22.dp))

        RoleOptionCard(
            title = "I'm a Parent",
            subtitle = "Monitor safety, screen time & schedules",
            badgeText = "PARENT",
            accentColor = IndigoPrimary,
            icon = Icons.Default.Security,
            onClick = onSelectParent,
            testTag = "select_role_parent_card"
        )

        Spacer(modifier = Modifier.height(14.dp))

        RoleOptionCard(
            title = "Child's Phone",
            subtitle = "Focus timer, study mode & protection",
            badgeText = "CHILD",
            accentColor = EmeraldSafe,
            icon = Icons.Default.PhoneAndroid,
            onClick = onSelectChild,
            testTag = "select_role_child_card"
        )
    }
}

@Composable
private fun RoleOptionCard(
    title: String,
    subtitle: String,
    badgeText: String,
    accentColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    testTag: String
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.28f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(accentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accentColor,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = accentColor.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = badgeText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun AuthSegmentedTabs(
    tabs: List<String>,
    selectedIndex: Int,
    accentColor: Color,
    tabTags: List<String>,
    onSelectTab: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        tabs.forEachIndexed { index, title ->
            val selected = selectedIndex == index
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (selected) accentColor else Color.Transparent,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onSelectTab(index) }
                    .testTag(tabTags[index])
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 10.dp)) {
                    Text(
                        text = title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ErrorBanner(message: String?) {
    if (message.isNullOrBlank()) return
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = CoralDangerBg,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 14.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Error",
                tint = CoralDanger,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = message,
                fontSize = 12.sp,
                color = CoralDanger,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ParentAuthView(
    isAuthLoading: Boolean,
    authError: String?,
    onSignIn: (email: String, pass: String) -> Unit,
    onSignUp: (name: String, family: String, email: String, pass: String, pin: String) -> Unit,
    onClearError: () -> Unit
) {
    var selectedAuthTab by remember { mutableIntStateOf(0) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    var fullName by remember { mutableStateOf("") }
    var familyName by remember { mutableStateOf("") }
    var securityPin by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxWidth()) {
        AuthSegmentedTabs(
            tabs = listOf("Sign In", "Create Account"),
            selectedIndex = selectedAuthTab,
            accentColor = IndigoPrimary,
            tabTags = listOf("tab_parent_signin", "tab_parent_signup"),
            onSelectTab = {
                onClearError()
                selectedAuthTab = it
            }
        )

        Spacer(modifier = Modifier.height(18.dp))
        ErrorBanner(authError)

        val fieldColors = OutlinedTextFieldDefaults.colors(focusedBorderColor = IndigoPrimary)
        val fieldShape = RoundedCornerShape(14.dp)

        if (selectedAuthTab == 0) {
            OutlinedTextField(
                value = email,
                onValueChange = { email = it; onClearError() },
                label = { Text("Email") },
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                singleLine = true,
                shape = fieldShape,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("parent_signin_email_field"),
                colors = fieldColors
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it; onClearError() },
                label = { Text("Password") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password"
                        )
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true,
                shape = fieldShape,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("parent_signin_password_field"),
                colors = fieldColors
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    if (email.isNotBlank() && password.isNotBlank()) {
                        onSignIn(email.trim(), password)
                    }
                },
                enabled = !isAuthLoading && email.isNotBlank() && password.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("parent_signin_submit_btn"),
                colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (isAuthLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Signing In...")
                } else {
                    Text("Sign In", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            OutlinedTextField(
                value = fullName,
                onValueChange = { fullName = it; onClearError() },
                label = { Text("Your Name") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                singleLine = true,
                shape = fieldShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("parent_signup_name_field"),
                colors = fieldColors
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = familyName,
                onValueChange = { familyName = it },
                label = { Text("Family Name (Optional)") },
                leadingIcon = { Icon(Icons.Default.Home, contentDescription = null) },
                singleLine = true,
                shape = fieldShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("parent_signup_family_field"),
                colors = fieldColors
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it; onClearError() },
                label = { Text("Email") },
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                singleLine = true,
                shape = fieldShape,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("parent_signup_email_field"),
                colors = fieldColors
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it; onClearError() },
                label = { Text("Password") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password"
                        )
                    }
                },
                singleLine = true,
                shape = fieldShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("parent_signup_password_field"),
                colors = fieldColors
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = securityPin,
                onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) securityPin = it },
                label = { Text("4-Digit Parent PIN") },
                supportingText = { Text("Protects child settings") },
                leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                singleLine = true,
                shape = fieldShape,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("parent_signup_pin_field"),
                colors = fieldColors
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (fullName.isNotBlank() && email.isNotBlank() && password.isNotBlank() && securityPin.length >= 4) {
                        onSignUp(fullName.trim(), familyName.trim(), email.trim(), password, securityPin)
                    }
                },
                enabled = !isAuthLoading && fullName.isNotBlank() && email.isNotBlank() && password.isNotBlank() && securityPin.length >= 4,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("parent_signup_submit_btn"),
                colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (isAuthLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Creating Account...")
                } else {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Create Account", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ChildPairingView(
    isAuthLoading: Boolean,
    authError: String?,
    onPairWithCode: ((pairingCode: String, childName: String, deviceName: String) -> Unit)? = null,
    onPairChild: (parentEmail: String, parentPass: String, childName: String, deviceName: String) -> Unit,
    onClearError: () -> Unit
) {
    var pairingMethod by remember { mutableIntStateOf(0) }
    var pairingCode by remember { mutableStateOf("") }
    var parentEmail by remember { mutableStateOf("") }
    var parentPassword by remember { mutableStateOf("") }
    var parentPassVisible by remember { mutableStateOf(false) }
    var childName by remember { mutableStateOf("") }
    var deviceName by remember { mutableStateOf("") }

    val fieldColors = OutlinedTextFieldDefaults.colors(focusedBorderColor = EmeraldSafe)
    val fieldShape = RoundedCornerShape(14.dp)

    Column(modifier = Modifier.fillMaxWidth()) {
        AuthSegmentedTabs(
            tabs = listOf("6-Digit Code", "Parent Login"),
            selectedIndex = pairingMethod,
            accentColor = EmeraldSafe,
            tabTags = listOf("child_pair_code_tab", "child_pair_login_tab"),
            onSelectTab = {
                onClearError()
                pairingMethod = it
            }
        )

        Spacer(modifier = Modifier.height(16.dp))
        ErrorBanner(authError)

        if (pairingMethod == 0) {
            Text(
                text = "Enter the 6-digit code from Parent -> Family",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = pairingCode,
                onValueChange = {
                    if (it.length <= 6) {
                        pairingCode = it.uppercase()
                        onClearError()
                    }
                },
                label = { Text("6-Digit Code") },
                placeholder = { Text("482195") },
                leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                singleLine = true,
                shape = fieldShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("child_pairing_code_field"),
                colors = fieldColors
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = childName,
                onValueChange = { childName = it; onClearError() },
                label = { Text("Child's Name") },
                leadingIcon = { Icon(Icons.Default.Face, contentDescription = null) },
                singleLine = true,
                shape = fieldShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("child_pairing_name_code_field"),
                colors = fieldColors
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = deviceName,
                onValueChange = { deviceName = it },
                label = { Text("Phone Name (Optional)") },
                leadingIcon = { Icon(Icons.Default.PhoneAndroid, contentDescription = null) },
                singleLine = true,
                shape = fieldShape,
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    if (pairingCode.length == 6 && childName.isNotBlank()) {
                        onPairWithCode?.invoke(pairingCode.trim(), childName.trim(), deviceName.trim())
                    }
                },
                enabled = !isAuthLoading && pairingCode.length == 6 && childName.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("child_pairing_code_submit_btn"),
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldSafe),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (isAuthLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Pairing...")
                } else {
                    Icon(Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Pair This Phone", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            OutlinedTextField(
                value = parentEmail,
                onValueChange = { parentEmail = it; onClearError() },
                label = { Text("Parent Email") },
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                singleLine = true,
                shape = fieldShape,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("child_pairing_parent_email_field"),
                colors = fieldColors
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = parentPassword,
                onValueChange = { parentPassword = it; onClearError() },
                label = { Text("Parent Password") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                visualTransformation = if (parentPassVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { parentPassVisible = !parentPassVisible }) {
                        Icon(
                            imageVector = if (parentPassVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (parentPassVisible) "Hide password" else "Show password"
                        )
                    }
                },
                singleLine = true,
                shape = fieldShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("child_pairing_parent_password_field"),
                colors = fieldColors
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = childName,
                onValueChange = { childName = it; onClearError() },
                label = { Text("Child's Name") },
                leadingIcon = { Icon(Icons.Default.Face, contentDescription = null) },
                singleLine = true,
                shape = fieldShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("child_pairing_name_field"),
                colors = fieldColors
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = deviceName,
                onValueChange = { deviceName = it },
                label = { Text("Phone Name (Optional)") },
                leadingIcon = { Icon(Icons.Default.PhoneAndroid, contentDescription = null) },
                singleLine = true,
                shape = fieldShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("child_pairing_device_field"),
                colors = fieldColors
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    if (parentEmail.isNotBlank() && parentPassword.isNotBlank() && childName.isNotBlank()) {
                        onPairChild(parentEmail.trim(), parentPassword, childName.trim(), deviceName.trim())
                    }
                },
                enabled = !isAuthLoading && parentEmail.isNotBlank() && parentPassword.isNotBlank() && childName.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("child_pairing_submit_btn"),
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldSafe),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (isAuthLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Pairing...")
                } else {
                    Icon(Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Pair This Phone", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
