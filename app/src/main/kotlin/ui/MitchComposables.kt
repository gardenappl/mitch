package garden.appl.mitch.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.Button
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import garden.appl.mitch.R

//@Composable
//fun MitchTheme(
//    isDark: Boolean,
//    content: @Composable () -> Unit
//) {
//
//}

@Preview(name = "Test")
@Composable
private fun MitchGameCard_preview() {
    MitchGameCard(ImageBitmap(100, 100))
}

@Composable
fun MitchGameCard(thumbnail: ImageBitmap) {
    Column {
        Image(thumbnail, "hi")
        Text("Game name")
        Text("Author name")
        Text("File name")

        Row {
            Button({ -> /* Noop */ }) {
                Text("Hi")
            }
            Button({ -> /* Noop */ }) {
                Icon(ImageVector.vectorResource(R.drawable.ic_settings_black_24dp), "More options")
            }
        }
    }
}