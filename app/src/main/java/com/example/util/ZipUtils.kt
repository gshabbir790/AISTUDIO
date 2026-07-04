package com.example.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.domain.model.AppMode
import com.example.domain.model.ProcessedImage
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ZipUtils {
    fun saveAndShareZip(context: Context, processedImages: List<ProcessedImage>, mode: AppMode) {
        if (processedImages.isEmpty()) return

        val zipFileName = if (mode == AppMode.GRID) "cropped_photos.zip" else "resized_photos.zip"
        
        try {
            val tempFile = File(context.cacheDir, zipFileName)
            if (tempFile.exists()) tempFile.delete()
            
            val fos = FileOutputStream(tempFile)
            val zos = ZipOutputStream(fos)
            
            processedImages.forEach { item ->
                val name = item.customName.trim().ifEmpty { item.defaultName }
                val entryName = if (name.endsWith(".jpg", ignoreCase = true)) name else "$name.jpg"
                zos.putNextEntry(ZipEntry(entryName))
                zos.write(item.jpegBytes)
                zos.closeEntry()
            }
            zos.close()
            fos.close()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, zipFileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { outStream ->
                        tempFile.inputStream().use { inStream ->
                            inStream.copyTo(outStream)
                        }
                    }
                }
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val targetFile = File(downloadsDir, zipFileName)
                tempFile.inputStream().use { inStream ->
                    targetFile.outputStream().use { outStream ->
                        inStream.copyTo(outStream)
                    }
                }
            }

            val fileUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                tempFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, fileUri)
                putExtra(Intent.EXTRA_SUBJECT, "Processed Photos ZIP")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            val chooser = Intent.createChooser(shareIntent, "تصاویر ڈاؤن لوڈ/شیئر کریں")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
            
            Toast.makeText(context, "ZIP فائل ڈاؤن لوڈ فولڈر میں محفوظ ہو گئی۔", Toast.LENGTH_LONG).show()

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "ZIP بنانے میں غلطی: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
