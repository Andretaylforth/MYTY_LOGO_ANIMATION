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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myty.monitor.data.Target

@Composable
fun ConnectScreen(found: List<Target>, onConnect: (Target) -> Unit) {
    val accent = LocalAccent.current
    var address by rememberSaveable { mutableStateOf("") }
    val manual = Target.parse(address)
    val shape = RoundedCornerShape(Myty.Radius)

    Box(Modifier.fillMaxSize().background(Myty.Bg), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 560.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(Myty.Gap),
        ) {
            Row(
                Modifier.fillMaxWidth().clip(shape).background(accent).padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MytyLogo(Modifier.size(44.dp), color = onColor(accent), holes = accent)
                Column(Modifier.padding(start = 16.dp)) {
                    Text("MYTY MONITOR", color = onColor(accent), fontSize = 20.sp)
                    Text("LIVE PC STATS", color = onColor(accent).copy(alpha = 0.7f), fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp))
                }
            }

            Box(Modifier.fillMaxWidth().clip(shape).background(Myty.Tile).padding(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("PCS FOUND", fontSize = 12.sp)
                    if (found.isEmpty()) {
                        Text(
                            "LOOKING… START MYTY MONITOR ON YOUR PC, THEN JOIN THE SAME WI-FI OR PLUG IN A USB CABLE.",
                            color = Myty.Dim, fontSize = 11.sp,
                        )
                    }
                    found.forEach { pc -> PcRow(pc) { onConnect(pc) } }
                }
            }

            Box(Modifier.fillMaxWidth().clip(shape).background(Myty.Tile).padding(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("OR TYPE THE ADDRESS FROM THE PC APP", fontSize = 12.sp)
                    Text("JUST THE NUMBERS BEFORE THE COLON, E.G. 192.168.1.20", color = Myty.Dim, fontSize = 11.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val fieldShape = RoundedCornerShape(50)
                        Box(
                            Modifier.weight(1f).height(48.dp).clip(fieldShape).border(1.dp, Myty.White, fieldShape)
                                .padding(horizontal = 18.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (address.isEmpty()) Text("192.168.1.20", color = Myty.Dim, fontSize = 14.sp)
                            BasicTextField(
                                value = address,
                                onValueChange = { address = it },
                                singleLine = true,
                                textStyle = TextStyle(fontFamily = Myty.Font, color = Myty.White, fontSize = 14.sp),
                                cursorBrush = SolidColor(accent),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                                keyboardActions = KeyboardActions(onGo = { manual?.let(onConnect) }),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Spacer(Modifier.size(10.dp))
                        Box(
                            Modifier.height(48.dp).clip(RoundedCornerShape(50))
                                .background(if (manual != null) accent else Myty.Track)
                                .clickable(enabled = manual != null) { manual?.let(onConnect) }
                                .padding(horizontal = 20.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text("CONNECT", color = if (manual != null) onColor(accent) else Myty.Dim, fontSize = 13.sp) }
                    }
                }
            }

            Box(Modifier.fillMaxWidth().clip(shape).background(Myty.Tile).padding(16.dp)) {
                Text(
                    "USB CABLE: TURN ON USB DEBUGGING (DEVELOPER OPTIONS) AND PLUG IN. NO DEVELOPER OPTIONS? " +
                        "TURN ON USB TETHERING AND PICK THE PC FROM THE LIST.",
                    color = Myty.Dim, fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun PcRow(pc: Target, onClick: () -> Unit) {
    val accent = LocalAccent.current
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).border(1.dp, Myty.Track, shape).clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(pc.name.uppercase(), fontSize = 15.sp)
            Text(if (pc.isUsb) "USB CABLE" else "${pc.host}:${pc.port}", color = Myty.Dim, fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp))
        }
        Box(Modifier.clip(RoundedCornerShape(50)).background(accent).padding(horizontal = 10.dp, vertical = 5.dp)) {
            Text(if (pc.isUsb) "USB" else "WI-FI", color = onColor(accent), fontSize = 11.sp)
        }
    }
}
