package com.example.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.AppMode
import com.example.domain.model.GridSettings
import com.example.domain.model.ProcessedImage
import com.example.domain.usecase.CompressImageUseCase
import com.example.domain.usecase.CropGridImageUseCase
import com.example.domain.usecase.SharpenImageUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class CropViewModel(
    private val cropGridImageUseCase: CropGridImageUseCase = CropGridImageUseCase(),
    private val sharpenImageUseCase: SharpenImageUseCase = SharpenImageUseCase(),
    private val compressImageUseCase: CompressImageUseCase = CompressImageUseCase()
) : ViewModel() {

    private val _appMode = MutableStateFlow(AppMode.GRID)
    val appMode: StateFlow<AppMode> = _appMode.asStateFlow()

    private val _welcomeDaroodCount = MutableStateFlow(0)
    val welcomeDaroodCount: StateFlow<Int> = _welcomeDaroodCount.asStateFlow()

    private val _welcomeCompleted = MutableStateFlow(false)
    val welcomeCompleted: StateFlow<Boolean> = _welcomeCompleted.asStateFlow()

    private val _downloadDaroodCount = MutableStateFlow(0)
    val downloadDaroodCount: StateFlow<Int> = _downloadDaroodCount.asStateFlow()

    private val _downloadCompleted = MutableStateFlow(false)
    val downloadCompleted: StateFlow<Boolean> = _downloadCompleted.asStateFlow()

    private val _gridSettings = MutableStateFlow(GridSettings())
    val gridSettings: StateFlow<GridSettings> = _gridSettings.asStateFlow()

    private val _targetWidth = MutableStateFlow("600")
    val targetWidth: StateFlow<String> = _targetWidth.asStateFlow()

    private val _targetHeight = MutableStateFlow("800")
    val targetHeight: StateFlow<String> = _targetHeight.asStateFlow()

    private val _targetKB = MutableStateFlow("23")
    val targetKB: StateFlow<String> = _targetKB.asStateFlow()

    private val _gridSourceImage = MutableStateFlow<Bitmap?>(null)
    val gridSourceImage: StateFlow<Bitmap?> = _gridSourceImage.asStateFlow()

    private val _multipleSourceImages = MutableStateFlow<List<Pair<String, Bitmap>>>(emptyList())
    val multipleSourceImages: StateFlow<List<Pair<String, Bitmap>>> = _multipleSourceImages.asStateFlow()

    private val _processedImages = MutableStateFlow<List<ProcessedImage>>(emptyList())
    val processedImages: StateFlow<List<ProcessedImage>> = _processedImages.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _processingProgress = MutableStateFlow(0f)
    val processingProgress: StateFlow<Float> = _processingProgress.asStateFlow()

    private val _processingStatusText = MutableStateFlow("")
    val processingStatusText: StateFlow<String> = _processingStatusText.asStateFlow()

    private val _processingDurationSec = MutableStateFlow("0.0")
    val processingDurationSec: StateFlow<String> = _processingDurationSec.asStateFlow()

    private val _playSoundTrigger = MutableStateFlow<String?>(null)
    val playSoundTrigger: StateFlow<String?> = _playSoundTrigger.asStateFlow()

    private var lastWelcomeClickTime = 0L
    private var lastDownloadClickTime = 0L

    fun setAppMode(mode: AppMode) {
        _appMode.value = mode
        clearProcessed()
    }

    fun setSoundPlayed() {
        _playSoundTrigger.value = null
    }

    fun triggerSound(soundName: String) {
        _playSoundTrigger.value = soundName
    }

    fun onWelcomeDaroodClicked(onCheatingDetected: () -> Unit, onCompleted: () -> Unit) {
        val currentTime = System.currentTimeMillis()
        if (lastWelcomeClickTime != 0L && (currentTime - lastWelcomeClickTime) < 700L) {
            onCheatingDetected()
            return
        }
        lastWelcomeClickTime = currentTime
        _welcomeDaroodCount.update { count ->
            val next = count + 1
            if (next >= 10) {
                viewModelScope.launch {
                    delay(500)
                    _welcomeCompleted.value = true
                    onCompleted()
                }
            }
            next
        }
    }

    fun onDownloadDaroodClicked(onCheatingDetected: () -> Unit, onCompleted: () -> Unit) {
        val currentTime = System.currentTimeMillis()
        if (lastDownloadClickTime != 0L && (currentTime - lastDownloadClickTime) < 700L) {
            onCheatingDetected()
            return
        }
        lastDownloadClickTime = currentTime
        _downloadDaroodCount.update { count ->
            val next = count + 1
            if (next >= 2) {
                viewModelScope.launch {
                    delay(500)
                    _downloadCompleted.value = true
                    onCompleted()
                }
            }
            next
        }
    }

    fun resetDownloadDarood() {
        _downloadDaroodCount.value = 0
        _downloadCompleted.value = false
        lastDownloadClickTime = 0L
    }

    fun updateGridSettings(update: (GridSettings) -> GridSettings) {
        _gridSettings.update(update)
    }

    fun updateTargetWidth(width: String) {
        _targetWidth.value = width
    }

    fun updateTargetHeight(height: String) {
        _targetHeight.value = height
    }

    fun updateTargetKB(kb: String) {
        _targetKB.value = kb
    }

    fun setGridSourceImage(bitmap: Bitmap?) {
        _gridSourceImage.value = bitmap
        clearProcessed()
    }

    fun setMultipleSourceImages(images: List<Pair<String, Bitmap>>) {
        _multipleSourceImages.value = images
        clearProcessed()
    }

    fun addMultipleSourceImages(images: List<Pair<String, Bitmap>>) {
        _multipleSourceImages.update { current ->
            current + images
        }
        clearProcessed()
    }

    fun removeMultipleSourceImage(index: Int) {
        _multipleSourceImages.update { current ->
            current.filterIndexed { idx, _ -> idx != index }
        }
        clearProcessed()
    }

    fun updateMultipleSourceImage(index: Int, bitmap: Bitmap) {
        _multipleSourceImages.update { current ->
            current.mapIndexed { idx, pair ->
                if (idx == index) pair.copy(second = bitmap) else pair
            }
        }
        clearProcessed()
    }

    fun updateGridSourceImage(bitmap: Bitmap) {
        _gridSourceImage.value = bitmap
        clearProcessed()
    }

    fun clearMultipleSourceImages() {
        _multipleSourceImages.value = emptyList()
        clearProcessed()
    }

    fun renameProcessedImage(index: Int, newName: String) {
        _processedImages.update { list ->
            list.mapIndexed { idx, item ->
                if (idx == index) item.copy(customName = newName) else item
            }
        }
    }

    private fun clearProcessed() {
        _processedImages.value = emptyList()
        _processingDurationSec.value = "0.0"
    }

    fun processImages(context: Context, onComplete: () -> Unit) {
        val currentSourceGrid = _gridSourceImage.value
        val currentSourceMultiple = _multipleSourceImages.value
        val mode = _appMode.value

        if (mode == AppMode.GRID && currentSourceGrid == null) return
        if (mode == AppMode.MULTIPLE && currentSourceMultiple.isEmpty()) return

        val width = _targetWidth.value.toIntOrNull() ?: 600
        val height = _targetHeight.value.toIntOrNull() ?: 800
        val targetKBVal = _targetKB.value.toDoubleOrNull() ?: 23.0

        _isProcessing.value = true
        _processingProgress.value = 0f
        _processingStatusText.value = "پروسیسنگ شروع ہو رہی ہے..."
        _processedImages.value = emptyList()

        viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            
            val timerJob = launch {
                while (true) {
                    delay(100)
                    val elapsed = (System.currentTimeMillis() - startTime) / 1000.0
                    _processingDurationSec.value = String.format("%.1f", elapsed)
                }
            }

            try {
                val processedList = withContext(Dispatchers.Default) {
                    val result = mutableListOf<ProcessedImage>()
                    if (mode == AppMode.GRID && currentSourceGrid != null) {
                        _processingStatusText.value = "تصویر سے گرڈ کاٹا جا رہا ہے..."
                        val croppedBitmaps = cropGridImageUseCase(
                            currentSourceGrid,
                            _gridSettings.value,
                            width,
                            height
                        )
                        croppedBitmaps.forEachIndexed { index, bitmap ->
                            val progress = (index.toFloat() / croppedBitmaps.size)
                            _processingProgress.value = progress
                            _processingStatusText.value = "تصویر ${index + 1} کوالٹی بہتر اور سائز کم کیا جا رہا ہے..."
                            
                            val sharpened = sharpenImageUseCase(bitmap)
                            val (compressedBytes, sizeKB) = compressImageUseCase(sharpened, targetKBVal)
                            val finalSizeText = String.format("%.1f KB", sizeKB)
                            val name = "image_${index + 1}"
                            
                            val compressedBitmap = BitmapFactory.decodeByteArray(compressedBytes, 0, compressedBytes.size)

                            result.add(
                                ProcessedImage(
                                    id = UUID.randomUUID().toString(),
                                    defaultName = name,
                                    customName = name,
                                    sizeText = finalSizeText,
                                    bitmap = compressedBitmap ?: sharpened,
                                    jpegBytes = compressedBytes
                                )
                            )
                        }
                    } else if (mode == AppMode.MULTIPLE) {
                        currentSourceMultiple.forEachIndexed { index, (originalName, bitmap) ->
                            val progress = (index.toFloat() / currentSourceMultiple.size)
                            _processingProgress.value = progress
                            val cleanName = originalName.substringBeforeLast(".")
                            _processingStatusText.value = "تصویر '$cleanName' کا سائز تبدیل کیا جا رہا ہے..."
                            
                            val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
                            val sharpened = sharpenImageUseCase(scaled)
                            val (compressedBytes, sizeKB) = compressImageUseCase(sharpened, targetKBVal)
                            val finalSizeText = String.format("%.1f KB", sizeKB)
                            val name = "resized_$cleanName"
                            
                            val compressedBitmap = BitmapFactory.decodeByteArray(compressedBytes, 0, compressedBytes.size)

                            result.add(
                                ProcessedImage(
                                    id = UUID.randomUUID().toString(),
                                    defaultName = name,
                                    customName = name,
                                    sizeText = finalSizeText,
                                    bitmap = compressedBitmap ?: sharpened,
                                    jpegBytes = compressedBytes
                                )
                            )
                        }
                    }
                    _processingProgress.value = 1f
                    _processingStatusText.value = "پروسیسنگ مکمل ہو گئی ہے!"
                    result
                }

                _processedImages.value = processedList
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                timerJob.cancel()
                val totalDuration = (System.currentTimeMillis() - startTime) / 1000.0
                _processingDurationSec.value = String.format("%.2f", totalDuration)
                _isProcessing.value = false
                onComplete()
            }
        }
    }
}
