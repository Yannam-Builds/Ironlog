package com.ironlog.app.ui

import android.view.KeyEvent
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironlog.app.data.objectbox.ProgressPhotoEntity
import com.ironlog.app.data.photos.ProgressPhotoStorage
import com.ironlog.app.ui.context.ThemeProvider
import com.ironlog.app.ui.screens.body.ProgressPhotoViewerDialog
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import java.io.File
import java.nio.file.Files

@RunWith(AndroidJUnit4::class)
class ProgressPhotoViewerInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun backDismissesViewerWithoutLeavingHost() {
        content()
        compose.onNodeWithText("PHOTO VIEWER").assertIsDisplayed()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.onAllNodesWithText("PHOTO VIEWER").assertCountEquals(0)
        compose.onNodeWithText("Photos host").assertIsDisplayed()
    }

    @Test fun dirtyNoteRequiresDiscardConfirmation() {
        content()
        compose.onNodeWithText("Notes").performTextReplacement("Unsaved synthetic note")
        compose.onNodeWithText("CLOSE").performClick()
        compose.onNodeWithText("Discard unsaved note?").assertIsDisplayed()
        compose.onNodeWithText("Keep editing").performClick()
        compose.onNodeWithText("Unsaved synthetic note").assertIsDisplayed()
        compose.onNodeWithText("CLOSE").performClick()
        compose.onNodeWithText("Discard").performClick()
        compose.onAllNodesWithText("PHOTO VIEWER").assertCountEquals(0)
    }

    @Test fun failedNoteSaveRetainsDraftAndShowsError() {
        content(failSave = true)
        compose.onNodeWithText("Notes").performTextReplacement("Retry this synthetic note")
        compose.onNodeWithText("SAVE NOTE").performClick()
        compose.onNodeWithText("Could not save note. Please try again.").assertIsDisplayed()
        compose.onNodeWithText("Retry this synthetic note").assertIsDisplayed()
    }

    @Test fun constrainedHeightAndLargeTextKeepNoteSaveAndCloseReachable() {
        content(viewerModifier = Modifier.height(260.dp), fontScale = 2f)
        compose.onNodeWithText("Notes").performScrollTo().performTextReplacement("Constrained synthetic draft")
        compose.onNodeWithText("SAVE NOTE").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("CLOSE").performScrollTo().assertIsDisplayed().performClick()
        compose.onAllNodesWithText("Discard unsaved note?").assertCountEquals(0)
        compose.onAllNodesWithText("PHOTO VIEWER").assertCountEquals(0)
        compose.onNodeWithText("Photos host").assertIsDisplayed()
    }

    @Test fun symlinkEscapeCannotDeleteDisposableFileOutsidePhotoDirectory() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val testRoot = Files.createTempDirectory(context.cacheDir.toPath(), "photo-ownership-test-").toFile()
        val dir = File(testRoot, "progress_photos").apply { mkdirs() }
        val outside = File(testRoot, "disposable-backup.txt").apply { writeText("Synthetic test file") }
        val link = File(dir, "photo_123.jpg")
        try {
            Files.createSymbolicLink(link.toPath(), outside.toPath())
            val storage = ProgressPhotoStorage(testRoot, "synthetic.fileprovider")
            val reference = "content://synthetic.fileprovider/progress_photos/photo_123.jpg"
            assertNull(storage.ownedFile(reference))
            storage.deletePhotos(listOf(ProgressPhotoEntity().apply { fileUri = reference })) { true }
            assertTrue(outside.exists())
        } finally {
            // Remove the link itself first; only disposable files created in testRoot are cleaned up.
            Files.deleteIfExists(link.toPath())
            outside.delete()
            dir.delete()
            testRoot.delete()
        }
    }

    private fun content(failSave: Boolean = false, viewerModifier: Modifier = Modifier, fontScale: Float = 1f) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                ThemeProvider(themeName = "dark") {
                    MaterialTheme {
                        var open by remember { mutableStateOf(true) }
                        androidx.compose.material3.Text("Photos host")
                        if (open) ProgressPhotoViewerDialog(
                            modifier = viewerModifier,
                            photos = listOf(ProgressPhotoEntity().apply { uid = "synthetic-C"; notes = "" }),
                            onDismiss = { open = false },
                            onSaveNote = { _, _ -> if (failSave) error("Injected synthetic save failure") },
                        )
                    }
                }
            }
        }
    }
}
