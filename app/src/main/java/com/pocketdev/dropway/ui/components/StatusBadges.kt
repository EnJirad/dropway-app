package com.pocketdev.dropway.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketdev.dropway.R
import com.pocketdev.dropway.core.model.DeliveryStatus
import com.pocketdev.dropway.core.model.PaymentStatus
import com.pocketdev.dropway.ui.theme.*

@Composable
fun DeliveryStatusBadge(status: DeliveryStatus) {
    val (bgColor, textColor, iconRes) = when (status) {
        DeliveryStatus.DELIVERED -> Triple(StatusDeliveredBg, StatusDelivered, R.drawable.ic_check)
        DeliveryStatus.PENDING -> Triple(StatusPendingBg, StatusPending, R.drawable.ic_pending)
        DeliveryStatus.FAILED -> Triple(StatusFailedBg, StatusFailed, R.drawable.ic_close)
        DeliveryStatus.RETURNED -> Triple(Color(0xFFF1F5F9), Color(0xFF475569), R.drawable.ic_export)
        DeliveryStatus.CANCELLED -> Triple(Color(0xFFF1F5F9), Color(0xFF64748B), R.drawable.ic_close)
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(11.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = status.label,
                color = textColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun PaymentStatusBadge(status: PaymentStatus) {
    val (bgColor, textColor) = when (status) {
        PaymentStatus.CASH_RECEIVED, PaymentStatus.TRANSFER_RECEIVED -> Pair(StatusDeliveredBg, StatusDelivered)
        PaymentStatus.AWAITING_TRANSFER -> Pair(StatusAwaitingTransferBg, StatusAwaitingTransfer)
        PaymentStatus.PARTIALLY_PAID -> Pair(StatusPartialBg, StatusPartial)
        PaymentStatus.UNPAID -> Pair(StatusPendingBg, StatusPending)
        PaymentStatus.VERIFICATION_REQUIRED -> Pair(Color(0xFFFEF08A), Color(0xFF854D0E))
        PaymentStatus.CANCELLED -> Pair(Color(0xFFF1F5F9), Color(0xFF64748B))
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = status.label,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
