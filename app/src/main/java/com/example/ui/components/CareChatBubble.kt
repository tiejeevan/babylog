package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MessageDeliveryStatus
import com.example.engine.BluetoothCareMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CareChatBubble(
    message: BluetoothCareMessage,
    timeFmt: SimpleDateFormat = rememberTimeFmt()
) {
    val isOutgoing = message.isFromMe
    val bgColor = when {
        message.isPing -> MaterialTheme.colorScheme.tertiaryContainer
        isOutgoing -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = when {
        message.isPing -> MaterialTheme.colorScheme.onTertiaryContainer
        isOutgoing -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val metaColor = textColor.copy(alpha = 0.75f)
    val shape = if (isOutgoing) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
    } else {
        RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isOutgoing) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = bgColor,
            shape = shape,
            modifier = Modifier.fillMaxWidth(0.82f)
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(
                    text = if (message.isPing) {
                        "${message.pingIcon ?: "📳"} ${message.senderName}"
                    } else {
                        message.senderName
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = textColor
                )
                Text(
                    text = message.text,
                    fontSize = 14.sp,
                    color = textColor,
                    modifier = Modifier.padding(top = 2.dp)
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = timeFmt.format(Date(message.timestampMillis)),
                        fontSize = 10.sp,
                        color = metaColor
                    )
                    if (isOutgoing) {
                        DeliveryTickIcon(status = message.deliveryStatus, tint = metaColor)
                    }
                }
            }
        }
    }
}

@Composable
private fun DeliveryTickIcon(
    status: MessageDeliveryStatus,
    tint: androidx.compose.ui.graphics.Color
) {
    val (icon, description) = when (status) {
        MessageDeliveryStatus.PENDING -> Icons.Outlined.Schedule to "Pending"
        MessageDeliveryStatus.DELIVERED -> Icons.Outlined.Done to "Delivered"
        MessageDeliveryStatus.READ -> Icons.Outlined.DoneAll to "Read"
    }
    Icon(
        imageVector = icon,
        contentDescription = description,
        tint = tint,
        modifier = Modifier.size(14.dp)
    )
}

@Composable
private fun rememberTimeFmt(): SimpleDateFormat {
    return androidx.compose.runtime.remember {
        SimpleDateFormat("h:mm a", Locale.getDefault())
    }
}
