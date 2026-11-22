package com.pocketdev.dropway.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketdev.dropway.ui.theme.DropwayPrimary
import com.pocketdev.dropway.ui.theme.DropwayPrimaryDark

// Bold, High-Contrast Text Color Palette for all Input Fields
val DropwayInputTextColor = Color(0xFF0F172A)      // Deepest Slate 900 - absolute high contrast
val DropwayInputLabelColor = Color(0xFF0F172A)     // Dark bold label
val DropwayInputBorderColor = Color(0xFF475569)    // Crisp Slate 600 border (visible in direct sunlight)
val DropwayInputPlaceholderColor = Color(0xFF64748B) // Slate 500 placeholder

@Composable
fun dropwayTextFieldColors() = TextFieldDefaults.outlinedTextFieldColors(
    textColor = DropwayInputTextColor,
    disabledTextColor = Color(0xFF334155),
    containerColor = Color.White,
    cursorColor = DropwayPrimary,
    focusedBorderColor = DropwayPrimary,
    unfocusedBorderColor = DropwayInputBorderColor,
    focusedLabelColor = DropwayPrimaryDark,
    unfocusedLabelColor = DropwayInputLabelColor,
    placeholderColor = DropwayInputPlaceholderColor
)

val dropwayInputTextStyle = TextStyle(
    color = DropwayInputTextColor,
    fontWeight = FontWeight.Bold,
    fontSize = 15.sp
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DropwayOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = dropwayInputTextStyle,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    shape: Shape = RoundedCornerShape(12.dp),
    colors: TextFieldColors = dropwayTextFieldColors()
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        readOnly = readOnly,
        textStyle = textStyle,
        label = label,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        supportingText = supportingText,
        isError = isError,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        maxLines = maxLines,
        interactionSource = interactionSource,
        shape = shape,
        colors = colors
    )
}
