package com.myty.monitor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myty.monitor.data.Target

private val White70 = Color.White.copy(alpha = 0.7f)
private val White12 = Color.White.copy(alpha = 0.12f)

@Composable
fun ConnectScreen(found: List<Target>, onConnect: (Target) -> Unit) {
    var address by rememberSaveable { mutableStateOf("") }
    val manual = Target.parse(address)

    Box(Modifier.fillMaxSize().background(Myty.Blue), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            MytyLogo(Modifier.size(64.dp))
            Text("Myty Monitor", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 16.dp))
            Text("Live CPU & GPU stats from your PC", color = White70, fontSize = 14.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 24.dp))

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("PCs found", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f))
                CircularProgressIndicator(Modifier.size(14.dp), color = White70, strokeWidth = 2.dp)
            }
            Spacer(Modifier.height(10.dp))

            if (found.isEmpty()) {
                Text(
                    "Looking for PCs… Start Myty Monitor on your PC, then connect this phone to the same Wi-Fi " +
                        "or plug it in with a USB cable.",
                    color = White70, fontSize = 13.sp,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(White12).padding(16.dp),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                found.forEach { pc -> PcRow(pc) { onConnect(pc) } }
            }

            Text("Or type the address shown in the PC app", color = Color.White, fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    singleLine = true,
                    placeholder = { Text("192.168.1.20", color = White70) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { manual?.let(onConnect) }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = White70,
                        cursorColor = Color.White,
                    ),
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = { manual?.let(onConnect) },
                    enabled = manual != null,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Myty.Blue),
                    modifier = Modifier.padding(start = 10.dp).height(54.dp),
                ) { Text("Connect") }
            }

            Text(
                "USB cable: turn on USB debugging (Settings → Developer options) and plug the phone into the PC. " +
                    "No developer options? Turn on USB tethering instead and pick the PC from the list above.",
                color = White70, fontSize = 12.sp, modifier = Modifier.padding(top = 24.dp),
            )
        }
    }
}

@Composable
private fun PcRow(pc: Target, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(White12)
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(pc.name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(if (pc.isUsb) "Connected by USB cable" else "${pc.host}:${pc.port}", color = White70, fontSize = 12.sp)
        }
        Text(
            if (pc.isUsb) "USB" else "Wi-Fi",
            color = Myty.Blue, fontSize = 11.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color.White).padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}
