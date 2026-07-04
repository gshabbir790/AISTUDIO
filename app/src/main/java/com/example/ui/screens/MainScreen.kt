package com.example.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.domain.model.AppMode
import com.example.domain.model.GridSettings
import com.example.domain.model.ProcessedImage
import com.example.ui.CropViewModel
import com.example.ui.theme.getUrduFontFamily
import com.example.util.ZipUtils
import kotlinx.coroutines.delay
import java.io.InputStream

import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: CropViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    // ViewModel state bindings
    val appMode by viewModel.appMode.collectAsState()
    val welcomeCompleted by viewModel.welcomeCompleted.collectAsState()
    val welcomeCount by viewModel.welcomeDaroodCount.collectAsState()
    val downloadCompleted by viewModel.downloadCompleted.collectAsState()
    val downloadCount by viewModel.downloadDaroodCount.collectAsState()
    val gridSettings by viewModel.gridSettings.collectAsState()
    
    val targetWidth by viewModel.targetWidth.collectAsState()
    val targetHeight by viewModel.targetHeight.collectAsState()
    val targetKB by viewModel.targetKB.collectAsState()
    
    val gridSourceImage by viewModel.gridSourceImage.collectAsState()
    val multipleSourceImages by viewModel.multipleSourceImages.collectAsState()
    val processedImages by viewModel.processedImages.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val processingProgress by viewModel.processingProgress.collectAsState()
    val processingStatusText by viewModel.processingStatusText.collectAsState()
    val processingDuration by viewModel.processingDurationSec.collectAsState()
    
    // UI Local Dialog States
    var showCheatingAlert by remember { mutableStateOf(false) }
    var cheatingAlertMessage by remember { mutableStateOf("") }
    var showDownloadDialog by remember { mutableStateOf(false) }
    
    var croppingBitmapIndex by remember { mutableStateOf<Int?>(null) }
    var croppingGridSource by remember { mutableStateOf(false) }
    
    // File Pickers
    val singleImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val bitmap = loadBitmapFromUri(context, it)
            viewModel.setGridSourceImage(bitmap)
        }
    }
    
    val multipleImagesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            val loaded = uris.mapNotNull { uri ->
                val bitmap = loadBitmapFromUri(context, uri)
                val name = getFileNameFromUri(context, uri) ?: "photo_${System.currentTimeMillis()}"
                if (bitmap != null) Pair(name, bitmap) else null
            }
            viewModel.setMultipleSourceImages(loaded)
        }
    }

    val addMoreImagesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            val loaded = uris.mapNotNull { uri ->
                val bitmap = loadBitmapFromUri(context, uri)
                val name = getFileNameFromUri(context, uri) ?: "photo_${System.currentTimeMillis()}"
                if (bitmap != null) Pair(name, bitmap) else null
            }
            viewModel.addMultipleSourceImages(loaded)
        }
    }

    // Edge-to-edge scaffold
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color(0xFFF4F4F9)
    ) { innerPadding ->
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. MAIN GRADIENT HEADING BANNER
                HeaderBanner()

                // 2. NEON DEVELOPER CREDITS BOX
                DeveloperCreditsBox()

                // 3. TARGET WIDTH, HEIGHT & KB PANEL
                DimensionSettingsCard(
                    widthValue = targetWidth,
                    heightValue = targetHeight,
                    kbValue = targetKB,
                    onWidthChange = { viewModel.updateTargetWidth(it) },
                    onHeightChange = { viewModel.updateTargetHeight(it) },
                    onKBChange = { viewModel.updateTargetKB(it) }
                )

                // 4. APP WORK MODE (Grid vs Multiple)
                AppModeContainer(
                    currentMode = appMode,
                    onModeChange = { viewModel.setAppMode(it) }
                )

                // 5. FILE UPLOAD BOXES
                if (appMode == AppMode.GRID) {
                    UploadZone(
                        title = "تصاویر والا پیج اپلوڈ کریں (Upload Grid Sheet)",
                        subtitle = if (gridSourceImage != null) "گرڈ شیٹ کامیابی سے لوڈ ہو گئی ہے۔" else "گیلری سے تصویر منتخب کریں...",
                        hasFile = gridSourceImage != null,
                        onClick = { singleImageLauncher.launch("image/*") }
                    )
                    if (gridSourceImage != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { singleImageLauncher.launch("image/*") },
                                border = BorderStroke(1.dp, Color(0xFF90CAF9)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF1976D2)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f).height(40.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Refresh, contentDescription = "Change", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = "تبدیل کریں (Change)", fontFamily = remember(context) { getUrduFontFamily(context) }, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = { croppingGridSource = true },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0288D1)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f).height(40.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Crop, contentDescription = "Crop", modifier = Modifier.size(16.dp), tint = Color.White)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = "تراشیں (Crop Sheet)", fontFamily = remember(context) { getUrduFontFamily(context) }, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                } else {
                    UploadZone(
                        title = "ایک یا زیادہ تصاویر اپلوڈ کریں (Upload Photos)",
                        subtitle = if (multipleSourceImages.isNotEmpty()) "کل منتخب کردہ فائلیں: ${multipleSourceImages.size}" else "ایک یا زائد فائلز منتخب کریں...",
                        hasFile = multipleSourceImages.isNotEmpty(),
                        onClick = { multipleImagesLauncher.launch("image/*") }
                    )
                    if (multipleSourceImages.isNotEmpty()) {
                        SelectedImagesSection(
                            selectedImages = multipleSourceImages,
                            onAddMoreClick = { addMoreImagesLauncher.launch("image/*") },
                            onRemoveClick = { index -> viewModel.removeMultipleSourceImage(index) },
                            onClearAllClick = { viewModel.clearMultipleSourceImages() },
                            onCropClick = { index -> croppingBitmapIndex = index }
                        )
                    }
                }

                // 6. GRID EXTRA ROW/COL SETTINGS (ONLY IN GRID MODE)
                if (appMode == AppMode.GRID && gridSourceImage != null) {
                    GridDimensionsCard(
                        rowsValue = gridSettings.rows,
                        colsValue = gridSettings.cols,
                        onRowsChange = { rows ->
                            viewModel.updateGridSettings { it.copy(rows = rows) }
                        },
                        onColsChange = { cols ->
                            viewModel.updateGridSettings { it.copy(cols = cols) }
                        }
                    )
                    
                    // Row for side-by-side view of live preview and sliders
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        // Left side: Live Alignment Canvas Guide
                        LiveAlignmentGuideCard(
                            sourceImage = gridSourceImage!!,
                            settings = gridSettings,
                            modifier = Modifier.weight(1f)
                        )
                        
                        // Right side: Sliders for Fine Tuning Margins and Spacings
                        GridAdjustmentsCard(
                            settings = gridSettings,
                            sourceWidth = gridSourceImage!!.width,
                            sourceHeight = gridSourceImage!!.height,
                            onSettingsChange = { updated -> viewModel.updateGridSettings { updated } },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // 7. CROP & RESIZE TRIGGER BUTTON
                Button(
                    onClick = {
                        viewModel.processImages(context) {
                            // Completed callback
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("process_button"),
                    enabled = if (appMode == AppMode.GRID) gridSourceImage != null else multipleSourceImages.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    contentPadding = PaddingValues(0.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(Color(0xFF00B0FF), Color(0xFF0288D1))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "CROP & RESIZE",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }

                // 8. RESULTS BOX SECTION
                if (processedImages.isNotEmpty()) {
                    ResultsSection(
                        images = processedImages,
                        duration = processingDuration,
                        onRenameChange = { idx, name -> viewModel.renameProcessedImage(idx, name) },
                        onDownloadClick = {
                            viewModel.resetDownloadDarood()
                            showDownloadDialog = true
                        }
                    )
                }
            }

            // WELCOME MODAL (MANDATORY DAROOD VERIFICATION)
            if (!welcomeCompleted) {
                WelcomeModal(
                    count = welcomeCount,
                    onCountClick = {
                        viewModel.onWelcomeDaroodClicked(
                            onCheatingDetected = {
                                cheatingAlertMessage = "⚠️ زیادہ ہوشیاری نہیں! پہلے درود شریف آرام سے پڑھیں۔"
                                showCheatingAlert = true
                            },
                            onCompleted = {
                                // welcome completed
                            }
                        )
                    }
                )
            }

            // DOWNLOAD MODAL (DAROOD BEFORE DOWNLOAD)
            if (showDownloadDialog) {
                DownloadModal(
                    count = downloadCount,
                    completed = downloadCompleted,
                    onCountClick = {
                        viewModel.onDownloadDaroodClicked(
                            onCheatingDetected = {
                                cheatingAlertMessage = "⚠️ زیادہ ہوشیاری نہیں! پہلے درود شریف آرام سے پڑھیں۔"
                                showCheatingAlert = true
                            },
                            onCompleted = {
                                coroutineScope.launch {
                                    delay(1000)
                                    showDownloadDialog = false
                                    ZipUtils.saveAndShareZip(context, processedImages, appMode)
                                }
                            }
                        )
                    },
                    onDismiss = { showDownloadDialog = false }
                )
            }

            // CHEATING ALERT DIALOG OVERLAY
            if (showCheatingAlert) {
                CheatingAlertDialog(
                    message = cheatingAlertMessage,
                    onConfirm = { showCheatingAlert = false }
                )
            }

            // PROCESSING FULL SCREEN PROGRESS TIMED MODAL
            if (isProcessing) {
                ProcessingOverlay(
                    progress = processingProgress,
                    statusText = processingStatusText,
                    duration = processingDuration
                )
            }

            // Crop Dialogs
            if (croppingBitmapIndex != null) {
                val idx = croppingBitmapIndex!!
                if (idx in multipleSourceImages.indices) {
                    val (name, bitmap) = multipleSourceImages[idx]
                    ImagePreviewCropDialog(
                        bitmap = bitmap,
                        title = name,
                        onDismiss = { croppingBitmapIndex = null },
                        onCropApplied = { cropped ->
                            viewModel.updateMultipleSourceImage(idx, cropped)
                            croppingBitmapIndex = null
                        }
                    )
                }
            }

            if (croppingGridSource && gridSourceImage != null) {
                ImagePreviewCropDialog(
                    bitmap = gridSourceImage!!,
                    title = "گرڈ شیٹ (Grid Sheet)",
                    onDismiss = { croppingGridSource = false },
                    onCropApplied = { cropped ->
                        viewModel.updateGridSourceImage(cropped)
                        croppingGridSource = false
                    }
                )
            }
        }
    }
}

// =========================================================================
// WIDGET & COMPONENTS IMPLEMENTATIONS (HIGH POLISH & CRAFTSMANSHIP)
// =========================================================================

@Composable
fun HeaderBanner() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF0D47A1), Color(0xFF1976D2)) // Professional Deep Blue Slate
                )
            )
            .padding(vertical = 24.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "ملٹی امیج کراپ اینڈ ریسائزر",
                color = Color.White,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Multi-Image Crop & Resizer",
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun DeveloperCreditsBox() {
    val infiniteTransition = rememberInfiniteTransition(label = "CreditsBlink")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "CreditsAlpha"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                shape = RoundedCornerShape(12.dp)
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "ڈیویلپر: غلام شبیر پرنسپل گورنمنٹ ہائر سیکنڈری سکول وریام والا",
                color = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )
        }
    }
}

@Composable
fun DimensionSettingsCard(
    widthValue: String,
    heightValue: String,
    kbValue: String,
    onWidthChange: (String) -> Unit,
    onHeightChange: (String) -> Unit,
    onKBChange: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = MaterialTheme.colorScheme.outline, shape = RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "تصویر کا کسٹم سائز اور کوالٹی:",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DimensionInputItem(
                    label = "چوڑائی (px)",
                    value = widthValue,
                    onValueChange = onWidthChange,
                    modifier = Modifier.weight(1f)
                )
                DimensionInputItem(
                    label = "لمبائی (px)",
                    value = heightValue,
                    onValueChange = onHeightChange,
                    modifier = Modifier.weight(1f)
                )
                DimensionInputItem(
                    label = "سائز (KB)",
                    value = kbValue,
                    onValueChange = onKBChange,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun DimensionInputItem(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        OutlinedTextField(
            value = value,
            onValueChange = { input ->
                if (input.all { it.isDigit() }) onValueChange(input)
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            ),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold
            )
        )
    }
}

@Composable
fun AppModeContainer(
    currentMode: AppMode,
    onModeChange: (AppMode) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = MaterialTheme.colorScheme.outline, shape = RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "کام کا موڈ سیلیکٹ کریں (Select App Mode):",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AppModeOption(
                    title = "گرڈ والا پیج (تصاویر کاٹیں)",
                    isSelected = currentMode == AppMode.GRID,
                    onClick = { onModeChange(AppMode.GRID) },
                    modifier = Modifier.weight(1f)
                )
                AppModeOption(
                    title = "الگ الگ تصاویر اپلوڈ کریں",
                    isSelected = currentMode == AppMode.MULTIPLE,
                    onClick = { onModeChange(AppMode.MULTIPLE) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun AppModeOption(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .clickable { onClick() }
            .border(
                width = 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = MaterialTheme.colorScheme.primary,
                unselectedColor = MaterialTheme.colorScheme.outline
            )
        )
        Text(
            text = title,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun UploadZone(
    title: String,
    subtitle: String,
    hasFile: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(135.dp)
            .clickable { onClick() }
            .border(
                width = 1.5.dp,
                color = if (hasFile) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(12.dp)
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (hasFile) MaterialTheme.colorScheme.primary.copy(alpha = 0.05f) else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (hasFile) 2.dp else 0.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (hasFile) Icons.Default.CheckCircle else Icons.Default.CloudUpload,
                contentDescription = "Upload Icon",
                tint = if (hasFile) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (hasFile) MaterialTheme.colorScheme.primary else Color.Gray,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun GridDimensionsCard(
    rowsValue: Int,
    colsValue: Int,
    onRowsChange: (Int) -> Unit,
    onColsChange: (Int) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = MaterialTheme.colorScheme.outline, shape = RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "پیج پر تصاویر کی تعداد بتائیں:",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                GridDimensionInput(
                    label = "قطاریں (Rows)",
                    value = rowsValue,
                    onValueChange = onRowsChange,
                    modifier = Modifier.weight(1f)
                )
                GridDimensionInput(
                    label = "کالم (Columns)",
                    value = colsValue,
                    onValueChange = onColsChange,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun GridDimensionInput(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        OutlinedTextField(
            value = value.toString(),
            onValueChange = { input ->
                val num = input.toIntOrNull() ?: 1
                if (num in 1..40) onValueChange(num)
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            ),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold
            )
        )
    }
}

@Composable
fun LiveAlignmentGuideCard(
    sourceImage: Bitmap,
    settings: GridSettings,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "لائیو الائنمنٹ گائیڈ (Live Crop Guide):",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth()
            )

            // Dynamic Live Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(sourceImage.width.toFloat() / sourceImage.height.toFloat())
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black)
            ) {
                // Background loaded Image
                Image(
                    bitmap = sourceImage.asImageBitmap(),
                    contentDescription = "Grid Source Live Preview",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds
                )

                // Overlay Canvas
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val canvasW = size.width
                    val canvasH = size.height

                    val imgW = sourceImage.width.toFloat()
                    val imgH = sourceImage.height.toFloat()

                    val scaleX = canvasW / imgW
                    val scaleY = canvasH / imgH

                    // Calculate bounding box margins
                    val tM = settings.marginTop * scaleY
                    val bM = settings.marginBottom * scaleY
                    val lM = settings.marginLeft * scaleX
                    val rM = settings.marginRight * scaleX
                    
                    val rS = settings.rowSpacing * scaleY
                    val cS = settings.colSpacing * scaleX

                    val activeWidth = canvasW - lM - rM
                    val activeHeight = canvasH - tM - bM

                    val cellW = (activeWidth - (settings.cols - 1) * cS) / settings.cols
                    val cellH = (activeHeight - (settings.rows - 1) * rS) / settings.rows

                    // Draw all grid cells
                    for (r in 0 until settings.rows) {
                        for (c in 0 until settings.cols) {
                            val x = lM + c * (cellW + cS)
                            val y = tM + r * (cellH + rS)

                            drawRect(
                                color = Color(0xFFE53935), // Pure, clear red guide lines
                                topLeft = Offset(x, y),
                                size = Size(cellW, cellH),
                                style = Stroke(width = 1.5.dp.toPx())
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GridAdjustmentsCard(
    settings: GridSettings,
    sourceWidth: Int,
    sourceHeight: Int,
    onSettingsChange: (GridSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "بارڈرز اور دوری ٹھیک کریں (Adjust Borders & Spacing):",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth()
            )

            // Margins limits to 40% of image size
            val maxMarginH = (sourceHeight * 0.4f).coerceAtLeast(100f)
            val maxMarginW = (sourceWidth * 0.4f).coerceAtLeast(100f)

            AdjustmentSliderItem(
                label = "اوپر (Top Margin): ${settings.marginTop.toInt()}px",
                value = settings.marginTop,
                maxValue = maxMarginH,
                onValueChange = { onSettingsChange(settings.copy(marginTop = it)) }
            )

            AdjustmentSliderItem(
                label = "نیچے (Bottom Margin): ${settings.marginBottom.toInt()}px",
                value = settings.marginBottom,
                maxValue = maxMarginH,
                onValueChange = { onSettingsChange(settings.copy(marginBottom = it)) }
            )

            AdjustmentSliderItem(
                label = "بائیں (Left Margin): ${settings.marginLeft.toInt()}px",
                value = settings.marginLeft,
                maxValue = maxMarginW,
                onValueChange = { onSettingsChange(settings.copy(marginLeft = it)) }
            )

            AdjustmentSliderItem(
                label = "دائیں (Right Margin): ${settings.marginRight.toInt()}px",
                value = settings.marginRight,
                maxValue = maxMarginW,
                onValueChange = { onSettingsChange(settings.copy(marginRight = it)) }
            )

            AdjustmentSliderItem(
                label = "لائنوں کی دوری (Row Spacing): ${settings.rowSpacing.toInt()}px",
                value = settings.rowSpacing,
                minValue = -50f,
                maxValue = 100f,
                onValueChange = { onSettingsChange(settings.copy(rowSpacing = it)) }
            )

            AdjustmentSliderItem(
                label = "کالموں کی دوری (Column Spacing): ${settings.colSpacing.toInt()}px",
                value = settings.colSpacing,
                minValue = -50f,
                maxValue = 100f,
                onValueChange = { onSettingsChange(settings.copy(colSpacing = it)) }
            )
        }
    }
}

@Composable
fun AdjustmentSliderItem(
    label: String,
    value: Float,
    minValue: Float = 0f,
    maxValue: Float,
    onValueChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
            .border(width = 1.dp, color = MaterialTheme.colorScheme.outlineVariant, shape = RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Right
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = minValue..maxValue,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun SelectedImagesSection(
    selectedImages: List<Pair<String, Bitmap>>,
    onAddMoreClick: () -> Unit,
    onRemoveClick: (Int) -> Unit,
    onClearAllClick: () -> Unit,
    onCropClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val urduFont = remember(context) { getUrduFontFamily(context) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = MaterialTheme.colorScheme.outline, shape = RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "منتخب کردہ تصاویر (${selectedImages.size})",
                    fontFamily = urduFont,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onClearAllClick,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Clear All",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "سب صاف کریں", fontFamily = urduFont, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                    }

                    OutlinedButton(
                        onClick = onAddMoreClick,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add More",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "مزید شامل کریں", fontFamily = urduFont, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }

            // Grid layout of selected images
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                selectedImages.chunked(2).forEachIndexed { rowIndex, rowList ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowList.forEachIndexed { colIndex, (name, bitmap) ->
                            val actualIndex = rowIndex * 2 + colIndex
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .border(width = 1.dp, color = MaterialTheme.colorScheme.outlineVariant, shape = RoundedCornerShape(10.dp)),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Image(
                                        bitmap = bitmap.asImageBitmap(),
                                        contentDescription = "Selected Image",
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .border(width = 1.dp, color = MaterialTheme.colorScheme.outlineVariant, shape = RoundedCornerShape(6.dp)),
                                        contentScale = ContentScale.Crop
                                    )

                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = name,
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "${bitmap.width}x${bitmap.height}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    IconButton(
                                        onClick = { onCropClick(actualIndex) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Crop,
                                            contentDescription = "Crop Image",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = { onRemoveClick(actualIndex) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Remove Image",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                        if (rowList.size < 2) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ImagePreviewCropDialog(
    bitmap: Bitmap,
    title: String,
    onDismiss: () -> Unit,
    onCropApplied: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    val urduFont = remember(context) { getUrduFontFamily(context) }

    var currentBitmap by remember { mutableStateOf(bitmap) }
    var isSymmetrical by remember { mutableStateOf(false) }

    var cropLeft by remember { mutableStateOf(0f) }
    var cropRight by remember { mutableStateOf(0f) }
    var cropTop by remember { mutableStateOf(0f) }
    var cropBottom by remember { mutableStateOf(0f) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
                .border(width = 2.dp, color = Color(0xFF0288D1), shape = RoundedCornerShape(14.dp)),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "تصویر تراشیں اور معائنہ",
                        fontFamily = urduFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color(0xFF01579B)
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.Gray
                        )
                    }
                }

                Text(
                    text = title,
                    fontSize = 11.sp,
                    color = Color.DarkGray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.Start)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp)
                        .aspectRatio(currentBitmap.width.toFloat() / currentBitmap.height.toFloat())
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF121212))
                ) {
                    Image(
                        bitmap = currentBitmap.asImageBitmap(),
                        contentDescription = "Crop Visual Area",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.FillBounds
                    )

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val canvasW = size.width
                        val canvasH = size.height

                        val leftPx = cropLeft * canvasW
                        val rightPx = cropRight * canvasW
                        val topPx = cropTop * canvasH
                        val bottomPx = cropBottom * canvasH

                        drawRect(
                            color = Color.Black.copy(alpha = 0.65f),
                            topLeft = Offset(0f, 0f),
                            size = Size(canvasW, topPx)
                        )
                        drawRect(
                            color = Color.Black.copy(alpha = 0.65f),
                            topLeft = Offset(0f, canvasH - bottomPx),
                            size = Size(canvasW, bottomPx)
                        )
                        drawRect(
                            color = Color.Black.copy(alpha = 0.65f),
                            topLeft = Offset(0f, topPx),
                            size = Size(leftPx, canvasH - topPx - bottomPx)
                        )
                        drawRect(
                            color = Color.Black.copy(alpha = 0.65f),
                            topLeft = Offset(canvasW - rightPx, topPx),
                            size = Size(rightPx, canvasH - topPx - bottomPx)
                        )

                        drawRect(
                            color = Color(0xFF4FC3F7),
                            topLeft = Offset(leftPx, topPx),
                            size = Size((canvasW - leftPx - rightPx).coerceAtLeast(1f), (canvasH - topPx - bottomPx).coerceAtLeast(1f)),
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            val matrix = Matrix()
                            matrix.postRotate(90f)
                            currentBitmap = Bitmap.createBitmap(
                                currentBitmap,
                                0, 0,
                                currentBitmap.width, currentBitmap.height,
                                matrix, true
                            )
                        },
                        border = BorderStroke(1.dp, Color(0xFF0288D1)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF0288D1)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Default.RotateRight,
                            contentDescription = "Rotate",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "گھمائیں", fontFamily = urduFont, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.clickable { isSymmetrical = !isSymmetrical }
                    ) {
                        Checkbox(
                            checked = isSymmetrical,
                            onCheckedChange = { isSymmetrical = it },
                            colors = CheckboxDefaults.colors(checkedColor = Color(0xFF0288D1)),
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "متوازی تراشیں (Symmetric)",
                            fontFamily = urduFont,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }
                }

                Divider(color = Color(0xFFEEEEEE), thickness = 1.dp)

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    AdjustmentSliderItem(
                        label = "بائیں سے تراشیں (Left Crop): ${(cropLeft * 100).toInt()}%",
                        value = cropLeft,
                        minValue = 0f,
                        maxValue = 0.45f,
                        onValueChange = {
                            cropLeft = it
                            if (isSymmetrical) cropRight = it
                        }
                    )

                    AdjustmentSliderItem(
                        label = "دائیں سے تراشیں (Right Crop): ${(cropRight * 100).toInt()}%",
                        value = cropRight,
                        minValue = 0f,
                        maxValue = 0.45f,
                        onValueChange = {
                            cropRight = it
                            if (isSymmetrical) cropLeft = it
                        }
                    )

                    AdjustmentSliderItem(
                        label = "اوپر سے تراشیں (Top Crop): ${(cropTop * 100).toInt()}%",
                        value = cropTop,
                        minValue = 0f,
                        maxValue = 0.45f,
                        onValueChange = {
                            cropTop = it
                            if (isSymmetrical) cropBottom = it
                        }
                    )

                    AdjustmentSliderItem(
                        label = "نیچے سے تراشیں (Bottom Crop): ${(cropBottom * 100).toInt()}%",
                        value = cropBottom,
                        minValue = 0f,
                        maxValue = 0.45f,
                        onValueChange = {
                            cropBottom = it
                            if (isSymmetrical) cropTop = it
                        }
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(44.dp),
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.Gray)
                    ) {
                        Text(text = "کینسل کریں", fontFamily = urduFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    Button(
                        onClick = {
                            val originalW = currentBitmap.width
                            val originalH = currentBitmap.height

                            val leftPx = (cropLeft * originalW).toInt().coerceAtLeast(0)
                            val rightPx = (cropRight * originalW).toInt().coerceAtLeast(0)
                            val topPx = (cropTop * originalH).toInt().coerceAtLeast(0)
                            val bottomPx = (cropBottom * originalH).toInt().coerceAtLeast(0)

                            val keepW = (originalW - leftPx - rightPx).coerceAtLeast(1)
                            val keepH = (originalH - topPx - bottomPx).coerceAtLeast(1)

                            try {
                                val cropped = Bitmap.createBitmap(currentBitmap, leftPx, topPx, keepW, keepH)
                                onCropApplied(cropped)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        },
                        modifier = Modifier.weight(1.5f).height(44.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Crop,
                            contentDescription = "Crop",
                            modifier = Modifier.size(16.dp),
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "تصویر تراشیں (Crop)", fontFamily = urduFont, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun ResultsSection(
    images: List<ProcessedImage>,
    duration: String,
    onRenameChange: (Int, String) -> Unit,
    onDownloadClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Success Message
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Success",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "تصاویر ${duration} سیکنڈز میں تیار ہوئیں۔ اب نام تبدیل کریں اور زپ فائل ڈاؤن لوڈ کرلیں۔",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Text(
            text = "🎉 تیار شدہ تصاویر (یہاں نام تبدیل کریں):",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Right,
            modifier = Modifier.fillMaxWidth()
        )

        // Processed Images horizontal/vertical Grid layout
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            images.chunked(2).forEach { rowList ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    rowList.forEach { item ->
                        val index = images.indexOf(item)
                        ProcessedImageCard(
                            imageItem = item,
                            onRenameChange = { onRenameChange(index, it) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (rowList.size < 2) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        // Downloader button
        Button(
            onClick = onDownloadClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .height(54.dp)
                .testTag("download_zip_button"),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(27.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
        ) {
            Text(
                text = "👉 Click to Download ZIP 👈",
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
            )
        }
    }
}

@Composable
fun ProcessedImageCard(
    imageItem: ProcessedImage,
    onRenameChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .border(width = 1.dp, color = MaterialTheme.colorScheme.outlineVariant, shape = RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Image(
                bitmap = imageItem.bitmap.asImageBitmap(),
                contentDescription = "Processed Image",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(width = 1.dp, color = MaterialTheme.colorScheme.outlineVariant, shape = RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )

            Text(
                text = imageItem.defaultName,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = imageItem.sizeText,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )

            OutlinedTextField(
                value = imageItem.customName,
                onValueChange = onRenameChange,
                placeholder = {
                    Text(
                        text = "نیا نام درج کریں...",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodySmall.copy(
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Medium
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )
        }
    }
}

// =========================================================================
// DIALOGS & OVERLAYS (HIGH POLISH)
// =========================================================================

@Composable
fun WelcomeModal(
    count: Int,
    onCountClick: () -> Unit
) {
    val context = LocalContext.current
    val urduFont = remember(context) { getUrduFontFamily(context) }
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .border(width = 1.dp, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), shape = RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "⚠️ اہم اور ضروری پیغام!",
                    fontFamily = urduFont,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "ایپ اوپن کرنے کے لئے برائے مہربانی 10 بار درود شریف پڑھیں۔",
                    fontFamily = urduFont,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )

                // Blinking Text Animation
                val infiniteTransition = rememberInfiniteTransition(label = "WelcomeBlink")
                val alpha by infiniteTransition.animateFloat(
                    initialValue = 0.4f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1000, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "DaroodAlpha"
                )

                Text(
                    text = "صلی اللہ علیہ والہ وسلم",
                    fontFamily = urduFont,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center
                )

                // Counter Box
                Box(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
                        .border(BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)), RoundedCornerShape(20.dp))
                        .padding(vertical = 8.dp, horizontal = 20.dp)
                ) {
                    Text(
                        text = "پڑھی گئی تعداد: $count / 10",
                        fontFamily = urduFont,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        textAlign = TextAlign.Center
                    )
                }

                // 3D bounce-looking button
                val bounceTransition = rememberInfiniteTransition(label = "WelcomeButtonBounce")
                val translateY by bounceTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = -4f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1200, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "ButtonY"
                )

                Button(
                    onClick = onCountClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = translateY.dp)
                        .height(52.dp)
                        .testTag("welcome_darood_count_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(26.dp)
                ) {
                    Text(
                        text = "👉 درود پاک پڑھ کر یہاں کلک کریں 👈",
                        fontFamily = urduFont,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun DownloadModal(
    count: Int,
    completed: Boolean,
    onCountClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val urduFont = remember(context) { getUrduFontFamily(context) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .border(width = 1.dp, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), shape = RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (!completed) {
                    Text(
                        text = "📥 ڈاؤن لوڈ کرنے سے پہلے ایک نیکی!",
                        fontFamily = urduFont,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = "اپنی تصاویر ڈاؤن لوڈ کرنے کے لئے 2 بار درود شریف پڑھیں۔",
                        fontFamily = urduFont,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp
                    )

                    // Blinking Text Animation
                    val infiniteTransition = rememberInfiniteTransition(label = "DownloadBlink")
                    val alpha by infiniteTransition.animateFloat(
                        initialValue = 0.4f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1000, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "DaroodAlpha"
                    )

                    Text(
                        text = "صلی اللہ علیہ والہ وسلم",
                        fontFamily = urduFont,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold),
                        textAlign = TextAlign.Center
                    )

                    // Counter Box
                    Box(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
                            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)), RoundedCornerShape(20.dp))
                            .padding(vertical = 8.dp, horizontal = 20.dp)
                    ) {
                        Text(
                            text = "پڑھی گئی تعداد: $count / 2",
                            fontFamily = urduFont,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            textAlign = TextAlign.Center
                        )
                    }

                    // Button
                    Button(
                        onClick = onCountClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("download_darood_count_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(26.dp)
                    ) {
                        Text(
                            text = "👉 درود پاک پڑھ کر یہاں کلک کریں 👈",
                            fontFamily = urduFont,
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    // Success Message
                    Text(
                        text = "✨ جزاک اللہ خیر ✨",
                        fontFamily = urduFont,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun CheatingAlertDialog(
    message: String,
    onConfirm: () -> Unit
) {
    val context = LocalContext.current
    val urduFont = remember(context) { getUrduFontFamily(context) }
    Dialog(
        onDismissRequest = onConfirm,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .border(width = 1.dp, color = MaterialTheme.colorScheme.outline, shape = RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = message,
                    fontFamily = urduFont,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )

                Button(
                    onClick = onConfirm,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("cheating_confirm_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text(
                        text = "جی بہتر، دوبارہ پڑھتا ہوں",
                        fontFamily = urduFont,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

@Composable
fun ProcessingOverlay(
    progress: Float,
    statusText: String,
    duration: String
) {
    val context = LocalContext.current
    val urduFont = remember(context) { getUrduFontFamily(context) }

    val infiniteTransition = rememberInfiniteTransition(label = "loading")
    
    // Outer pulsing scale
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    // Outer pulsing alpha
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    // Rotation angle for loading ring
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotationAngle"
    )

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .border(2.dp, Color(0xFF0288D1), RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Animated Loading Graphic
                Box(
                    modifier = Modifier.size(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Outer pulsing ring
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .scale(pulseScale)
                            .alpha(pulseAlpha)
                            .border(BorderStroke(4.dp, Color(0xFF0288D1).copy(alpha = 0.4f)), CircleShape)
                    )

                    // Rotating arc indicator
                    Canvas(
                        modifier = Modifier
                            .size(72.dp)
                            .rotate(rotationAngle)
                    ) {
                        drawArc(
                            color = Color(0xFF0288D1),
                            startAngle = 0f,
                            sweepAngle = 100f,
                            useCenter = false,
                            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                        )
                        
                        drawArc(
                            color = Color(0xFF4FC3F7),
                            startAngle = 180f,
                            sweepAngle = 100f,
                            useCenter = false,
                            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }

                    // Central stationary icon / progress percentage
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .background(Color(0xFFE1F5FE), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Crop,
                            contentDescription = "Cropping",
                            tint = Color(0xFF0288D1),
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                // Progress Percentage
                val percentage = (progress * 100).toInt().coerceIn(0, 100)
                Text(
                    text = "$percentage%",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF01579B)
                    )
                )

                // Elegant progress bar
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    LinearProgressIndicator(
                        progress = progress.coerceIn(0f, 1f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = Color(0xFF0288D1),
                        trackColor = Color(0xFFE0F7FA)
                    )
                }

                // Status Texts
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (statusText.isNotEmpty()) statusText else "تصاویر پروسیس ہو رہی ہیں...",
                        fontFamily = urduFont,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF333333),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp
                    )

                    Text(
                        text = "گزرتا ہوا وقت: $duration سیکنڈز",
                        fontFamily = urduFont,
                        color = Color.Gray,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

// =========================================================================
// DECODING UTILS
// =========================================================================

fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
    return try {
        val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
        BitmapFactory.decodeStream(inputStream)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

fun getFileNameFromUri(context: Context, uri: Uri): String? {
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        try {
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    result = cursor.getString(index)
                }
            }
        } finally {
            cursor?.close()
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/') ?: -1
        if (cut != -1) {
            result = result?.substring(cut + 1)
        }
    }
    return result
}
