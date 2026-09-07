package com.ecolacteos.acopio.plataforma

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.zxing.BinaryBitmap
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import java.util.concurrent.Executors

/**
 * CameraX (`Preview` + `ImageAnalysis`) + ZXing puro (decisión `PROMPT_FASE_08A.md §3.2`: sin ML Kit, sin
 * Google Play Services). Se asume permiso de cámara ya `CONCEDIDO` -- ver `EscanerQr.kt`.
 *
 * `ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST`: si el decodificador no llega a procesar un frame antes del
 * siguiente, descarta el viejo en vez de acumular cola -- un QR en cámara no necesita procesar cada frame,
 * solo el más reciente.
 */
@Composable
actual fun EscanerQr(onCodigoDetectado: (String) -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val onCodigoDetectadoActualizado = rememberUpdatedState(onCodigoDetectado)

    val previewView = remember { PreviewView(context) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val qrCodeReader = remember { QRCodeReader() }

    AndroidView(factory = { previewView }, modifier = modifier)

    DisposableEffect(lifecycleOwner) {
        var cameraProviderVinculado: ProcessCameraProvider? = null
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener(
            {
                val cameraProvider = cameraProviderFuture.get()
                cameraProviderVinculado = cameraProvider

                val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                val analisis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { usoAnalisis ->
                        usoAnalisis.setAnalyzer(cameraExecutor) { imageProxy ->
                            decodificarQr(imageProxy, qrCodeReader)?.let { codigo ->
                                onCodigoDetectadoActualizado.value(codigo)
                            }
                            imageProxy.close()
                        }
                    }

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analisis)
                } catch (sinCamara: IllegalStateException) {
                    // Cámara no disponible (en uso por otra app, hardware ausente) -- sin preview ni
                    // decodificación, pero sin crashear la pantalla. `A-03` sigue siendo la salida.
                } catch (sinCamara: IllegalArgumentException) {
                    // Combinación de casos de uso no soportada por este dispositivo -- mismo criterio de arriba.
                }
            },
            ContextCompat.getMainExecutor(context),
        )

        onDispose {
            cameraProviderVinculado?.unbindAll()
            cameraExecutor.shutdown()
        }
    }
}

private fun decodificarQr(imageProxy: ImageProxy, reader: QRCodeReader): String? {
    val buffer = imageProxy.planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)

    val fuente = PlanarYUVLuminanceSource(
        bytes, imageProxy.width, imageProxy.height, 0, 0, imageProxy.width, imageProxy.height, false,
    )
    val binarizado = BinaryBitmap(HybridBinarizer(fuente))

    return try {
        reader.decode(binarizado).text
    } catch (sinCodigo: NotFoundException) {
        null
    } finally {
        reader.reset()
    }
}

