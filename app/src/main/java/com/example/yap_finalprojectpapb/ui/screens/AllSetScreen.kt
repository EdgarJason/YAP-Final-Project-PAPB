package com.example.yap_finalprojectpapb.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yap_finalprojectpapb.ui.components.AppButton
import com.example.yap_finalprojectpapb.ui.components.AppButtonColorStyle
import com.example.yap_finalprojectpapb.ui.components.AppButtonVariant
import com.example.yap_finalprojectpapb.ui.components.MascotMood
import com.example.yap_finalprojectpapb.ui.components.YapMascot
import com.example.yap_finalprojectpapb.ui.theme.BlueDarker
import com.example.yap_finalprojectpapb.ui.theme.NeutralWhite
import com.example.yap_finalprojectpapb.ui.theme.PlusJakartaSansFamily
import com.example.yap_finalprojectpapb.ui.theme.YAPFinalProjectPAPBTheme

@Composable
fun AllSetScreen(
    onStartClick: () -> Unit = {}
) {
    AllSetContent(onStartClick = onStartClick)
}

@Composable
fun AllSetContent(
    onStartClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = NeutralWhite
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                Spacer(modifier = Modifier.height(24.dp))

                // Judul "All set!" di pinggir kiri
                Text(
                    text = "All set!",
                    fontFamily = PlusJakartaSansFamily,
                    fontSize = 24.sp, // Judul 24px
                    fontWeight = FontWeight.Bold,
                    color = BlueDarker
                )

                Spacer(modifier = Modifier.height(60.dp))

                // Mascot & Text di Tengah
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    YapMascot(
                        size = 140.dp,
                        mood = MascotMood.Happy,
                        showSpeechBubble = false
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "Make your apps\nwith YAP!",
                        fontFamily = PlusJakartaSansFamily,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = BlueDarker,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Just YAP to make your APP",
                        fontFamily = PlusJakartaSansFamily,
                        fontSize = 14.sp,
                        color = BlueDarker,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Tombol di bagian paling bawah dengan jarak 48px dari bottom
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 48.dp)
            ) {
                AppButton(
                    text = "Start chatting with YAP!",
                    onClick = onStartClick,
                    variant = AppButtonVariant.Primary,
                    colorStyle = AppButtonColorStyle.Blue
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AllSetScreenPreview() {
    YAPFinalProjectPAPBTheme {
        AllSetScreen()
    }
}
