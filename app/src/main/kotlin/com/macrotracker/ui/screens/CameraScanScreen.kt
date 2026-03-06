package com.macrotracker.ui.screens

import android.Manifest
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.macrotracker.data.remote.ScanResult
import com.macrotracker.ui.components.ButtonVariant
import com.macrotracker.ui.components.MacroButton
import com.macrotracker.ui.components.MacroTextField
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.Primary
import com.macrotracker.ui.theme.PrimaryVariant
import com.macrotracker.ui.theme.Secondary
import com.macrotracker.ui.theme.Surface
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.viewmodel.CameraScanViewModel
import com.macrotracker.ui.viewmodel.ScanPhase
import com.macrotracker.ui.util.rememberHaptics
import java.io.ByteArrayOutputStream

@Composable
fun CameraScanScreen(
    onNavigateBack: () -> Unit,
    onNavigateHome: () -> Unit,
    viewModel: CameraScanViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val phase by viewModel.phase.collectAsState()
    val scanning by viewModel.scanning.collectAsState()
    val error by viewModel.error.collectAsState()
    val loggedEvent by viewModel.loggedEvent.collectAsState()

    var hasCameraPermission by remember { mutableStateOf(false) }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var capturedBase64 by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    // Show error toast
    LaunchedEffect(error) {
        error?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }

    // Navigate home after food is saved to the database
    LaunchedEffect(loggedEvent) {
        if (loggedEvent) {
            viewModel.consumeLoggedEvent()
            Toast.makeText(context, "✅ Food logged successfully!", Toast.LENGTH_SHORT).show()
            onNavigateHome()
        }
    }

    when {
        !hasCameraPermission -> PermissionGate(
            onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            onGoBack = onNavigateBack,
        )
        phase == ScanPhase.CAMERA -> CameraPhase(
            onPhotoCaptured = { bitmap, base64 ->
                capturedBitmap = bitmap
                capturedBase64 = base64
                viewModel.setPhase(ScanPhase.PREVIEW)
            },
            onGoBack = onNavigateBack,
        )
        phase == ScanPhase.PREVIEW -> PreviewPhase(
            bitmap = capturedBitmap,
            scanning = scanning,
            onScan = {
                capturedBase64?.let { viewModel.analyzeImage(it) }
            },
            onRetake = {
                capturedBitmap = null
                capturedBase64 = null
                viewModel.setPhase(ScanPhase.CAMERA)
            },
        )
        phase == ScanPhase.RESULT -> ResultPhase(
            viewModel = viewModel,
            bitmap = capturedBitmap,
            onLog = { adj ->
                // Save directly to the database — no more just populating form fields
                viewModel.saveScannedFood(adj)
            },
            onScanAgain = {
                capturedBitmap = null
                capturedBase64 = null
                viewModel.resetForNewScan()
            },
            onCancel = onNavigateBack,
        )
    }
}

@Composable
private fun PermissionGate(onRequestPermission: () -> Unit, onGoBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("📷", fontSize = 64.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Camera Access Needed", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimary, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "DailyDash needs camera access to scan nutrition labels on food packaging.",
            fontSize = 15.sp, color = TextSecondary, textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))
        MacroButton(text = "Grant Camera Access", onClick = onRequestPermission)
        MacroButton(text = "Go Back", onClick = onGoBack, variant = ButtonVariant.SECONDARY)
    }
}

@Composable
private fun CameraPhase(
    onPhotoCaptured: (Bitmap, String) -> Unit,
    onGoBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val imageCapture = remember { ImageCapture.Builder().setJpegQuality(45).build() }
    val previewView = remember { PreviewView(context) }
    val haptics = rememberHaptics()

    LaunchedEffect(lifecycleOwner) {
        val cameraProvider = ProcessCameraProvider.awaitInstance(context)
        val preview = Preview.Builder().build().also {
            it.surfaceProvider = previewView.surfaceProvider
        }
        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(
            lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Camera preview
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize(),
        )

        // Overlay
        Column(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Top bar
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 40.dp, start = 20.dp, end = 20.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onGoBack,
                    modifier = Modifier.size(40.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape),
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
                Text("Scan Nutrition Label", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                Spacer(modifier = Modifier.width(40.dp))
            }

            // Viewfinder
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .size(300.dp, 200.dp)
                        .border(3.dp, Primary, RoundedCornerShape(12.dp)),
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Point at the nutrition facts label on the back of the product",
                    color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }

            // Shutter button
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp, start = 40.dp, end = 40.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.25f))
                        .border(4.dp, Color.White, CircleShape)
                        .clickable {
                            haptics.confirm()
                            imageCapture.takePicture(
                                ContextCompat.getMainExecutor(context),
                                object : ImageCapture.OnImageCapturedCallback() {
                                    override fun onCaptureSuccess(image: ImageProxy) {
                                        val buffer = image.planes[0].buffer
                                        val bytes = ByteArray(buffer.remaining())
                                        buffer.get(bytes)
                                        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                        val base64 = bitmapToBase64(bitmap)
                                        image.close()
                                        onPhotoCaptured(bitmap, base64)
                                    }

                                    override fun onError(exception: ImageCaptureException) {
                                        // Silently fail - user can retry
                                    }
                                }
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color.White),
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewPhase(
    bitmap: Bitmap?,
    scanning: Boolean,
    onScan: () -> Unit,
    onRetake: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().background(Background)) {
        // Image preview
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Captured photo",
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentScale = ContentScale.Fit,
            )
        } else {
            Box(modifier = Modifier.fillMaxWidth().weight(1f).background(Color.Black))
        }

        // Bottom panel
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Surface, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .padding(24.dp),
        ) {
            Text("Looking good?", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(
                "Make sure the nutrition label is clear and fully visible.",
                fontSize = 14.sp, color = TextSecondary, modifier = Modifier.padding(bottom = 20.dp),
            )

            if (scanning) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)) {
                    CircularProgressIndicator(color = Primary, modifier = Modifier.size(40.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Analysing nutrition label…", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Text("AI is reading the values for you ✨", fontSize = 13.sp, color = TextSecondary, modifier = Modifier.padding(top = 6.dp))
                }
            } else {
                MacroButton(text = "🔍  Scan This Photo", onClick = onScan)
                MacroButton(text = "Retake", onClick = onRetake, variant = ButtonVariant.SECONDARY)
            }
        }
    }
}

@Composable
private fun ResultPhase(
    viewModel: CameraScanViewModel,
    bitmap: Bitmap?,
    onLog: (ScanResult) -> Unit,
    onScanAgain: () -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val foodNameOverride by viewModel.foodNameOverride.collectAsState()
    val caloriesOverride by viewModel.caloriesOverride.collectAsState()
    val proteinOverride by viewModel.proteinOverride.collectAsState()
    val servingsOverride by viewModel.servingsOverride.collectAsState()
    val servingSizeOverride by viewModel.servingSizeOverride.collectAsState()
    val packageWeightOverride by viewModel.packageWeightOverride.collectAsState()
    val haptics = rememberHaptics()

    // The ORIGINAL scan result — used to decide which follow-up fields to show.
    // We must NOT use `adj` for this, because `adj` includes user-typed values
    // and would cause the input fields to disappear the moment the user types a valid number.
    val originalResult by viewModel.result.collectAsState()

    val adj = viewModel.getAdjustedResult() ?: return

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .background(Background),
    ) {
        // Thumbnail
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Scanned photo",
                modifier = Modifier.fillMaxWidth().height(200.dp),
                contentScale = ContentScale.Crop,
            )
        }

        // Result card
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("📊 Nutrition Scan Results", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                Text(adj.foodName, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Primary, modifier = Modifier.padding(bottom = 20.dp))

                // Per serving
                Text("PER SERVING", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary, modifier = Modifier.padding(bottom = 10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MacroPill("${adj.caloriesPerServing}", "kcal", PrimaryVariant, Modifier.weight(1f))
                    MacroPill("${adj.proteinPerServing}g", "protein", Color(0xFF1A5E5A), Modifier.weight(1f))
                }

                // Package meta
                if (adj.packageWeightGrams > 0 || adj.servingSizeGrams > 0) {
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (adj.packageWeightGrams > 0) MetaChip("Package: ${adj.packageWeightGrams}g")
                        if (adj.servingSizeGrams > 0) MetaChip("Serving: ${adj.servingSizeGrams}g")
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Servings override
                Text("SERVINGS IN WHOLE PACKAGE", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary, modifier = Modifier.padding(bottom = 10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Border, RoundedCornerShape(12.dp))
                        .background(Background, RoundedCornerShape(12.dp)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "−", fontSize = 24.sp, color = Primary, fontWeight = FontWeight.Light,
                        modifier = Modifier
                            .clickable {
                                haptics.tick()
                                val v = servingsOverride.toDoubleOrNull() ?: 1.0
                                viewModel.setServingsOverride("%.1f".format(maxOf(0.5, v - 0.5)))
                            }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                    )
                    MacroTextField(
                        value = servingsOverride,
                        onValueChange = { viewModel.setServingsOverride(it) },
                        placeholder = "1",
                        keyboardType = KeyboardType.Decimal,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "+", fontSize = 24.sp, color = Primary, fontWeight = FontWeight.Light,
                        modifier = Modifier
                            .clickable {
                                haptics.tick()
                                val v = servingsOverride.toDoubleOrNull() ?: 1.0
                                viewModel.setServingsOverride("%.1f".format(v + 0.5))
                            }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                    )
                }

                // Follow-up fields — use original scan result to decide which to show
                Spacer(modifier = Modifier.height(16.dp))

                val orig = originalResult
                val hasNamedFood = orig != null && orig.foodName.isNotBlank() && orig.foodName.lowercase() != "scanned food"
                if (!hasNamedFood) {
                    FollowUpField("Product Name *", "Enter product name", foodNameOverride, { viewModel.setFoodNameOverride(it) })
                }
                if (orig == null || orig.caloriesPerServing <= 0) {
                    FollowUpField("Calories Per Serving *", "e.g. 180", caloriesOverride, { viewModel.setCaloriesOverride(it) }, KeyboardType.Decimal)
                }
                if (orig == null || orig.proteinPerServing <= 0) {
                    FollowUpField("Protein Per Serving (g)", "e.g. 12", proteinOverride, { viewModel.setProteinOverride(it) }, KeyboardType.Decimal)
                }
                if (orig == null || orig.servingSizeGrams <= 0) {
                    FollowUpField("Serving Size (g)", "e.g. 85", servingSizeOverride, { viewModel.setServingSizeOverride(it) }, KeyboardType.Decimal)
                }
                if (orig == null || orig.packageWeightGrams <= 0) {
                    FollowUpField("Total Product Weight (g)", "e.g. 340", packageWeightOverride, { viewModel.setPackageWeightOverride(it) }, KeyboardType.Decimal)
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Whole package totals
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Primary.copy(alpha = 0.09f)),
                    border = BorderStroke(1.dp, Primary.copy(alpha = 0.25f)),
                ) {
                    Column(modifier = Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🛍 WHOLE PACKAGE TOTALS", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${adj.totalCalories}", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = Primary)
                                Text("kcal", fontSize = 13.sp, color = TextSecondary)
                            }
                            Box(modifier = Modifier.width(1.dp).height(50.dp).background(Border))
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${adj.totalProtein}g", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = Secondary)
                                Text("protein", fontSize = 13.sp, color = TextSecondary)
                            }
                        }
                        Text(
                            "(${adj.caloriesPerServing} kcal × ${"%.1f".format(adj.servingsPerContainer)} servings)",
                            fontSize = 12.sp, color = TextSecondary, fontStyle = FontStyle.Italic,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }
            }
        }

        // Action buttons
        MacroButton(
            text = "➕  Log to Today",
            onClick = {
                val missingRequired = mutableListOf<String>()
                if (adj.foodName.isBlank() || adj.foodName.lowercase() == "scanned food") missingRequired.add("product name")
                if (adj.caloriesPerServing <= 0) missingRequired.add("calories per serving")
                if (adj.servingsPerContainer <= 0) missingRequired.add("servings in package")

                if (missingRequired.isNotEmpty()) {
                    haptics.reject()
                    Toast.makeText(context, "Please fill: ${missingRequired.joinToString(", ")}", Toast.LENGTH_SHORT).show()
                } else {
                    haptics.confirm()
                    onLog(adj)
                }
            },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        MacroButton(text = "Scan Again", onClick = onScanAgain, variant = ButtonVariant.SECONDARY, modifier = Modifier.padding(horizontal = 16.dp))
        MacroButton(text = "Cancel", onClick = onCancel, variant = ButtonVariant.SECONDARY, modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(modifier = Modifier.height(40.dp))
    }
}

@Composable
private fun MacroPill(value: String, label: String, bgColor: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .padding(14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(label, fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun MetaChip(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        color = TextSecondary,
        modifier = Modifier
            .border(1.dp, Border, CircleShape)
            .background(Background, CircleShape)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

@Composable
private fun FollowUpField(
    label: String,
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    Column(modifier = Modifier.padding(bottom = 12.dp)) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, modifier = Modifier.padding(bottom = 6.dp))
        MacroTextField(value = value, onValueChange = onValueChange, placeholder = placeholder, keyboardType = keyboardType)
    }
}

private fun bitmapToBase64(bitmap: Bitmap): String {
    val stream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 45, stream)
    val bytes = stream.toByteArray()
    return Base64.encodeToString(bytes, Base64.NO_WRAP)
}

