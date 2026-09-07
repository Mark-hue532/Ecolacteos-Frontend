package com.ecolacteos.acopio.plataforma

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureMetadataOutput
import platform.AVFoundation.AVCaptureMetadataOutputObjectsDelegateProtocol
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVMetadataMachineReadableCodeObject
import platform.AVFoundation.AVMetadataObjectTypeQRCode
import platform.CoreGraphics.CGRectMake
import platform.QuartzCore.CATransaction
import platform.UIKit.UIView
import platform.darwin.NSObject
import platform.darwin.dispatch_get_main_queue

/**
 * `AVCaptureSession` con `AVCaptureMetadataOutput` (`AVMetadataObjectTypeQRCode`) -- decodificación nativa
 * de iOS, sin ninguna librería de terceros (a diferencia de Android/ZXing, `§3.2` del prompt: "`AVFoundation`
 * es lo nativo"). El preview se muestra vía `UIKitView` sobre un `UIView` con un `AVCaptureVideoPreviewLayer`.
 *
 * Se asume permiso de cámara ya `CONCEDIDO` -- ver `EscanerQr.kt`.
 *
 * ⚠️ Compila pero no se verificó en simulador/dispositivo desde esta sesión (Windows, `CLAUDE.md §8`) --
 * es, textualmente, "la primera capacidad del proyecto que no se puede verificar ni siquiera parcialmente
 * en Windows" (`PROMPT_FASE_08.md §8` riesgo 1).
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun EscanerQr(onCodigoDetectado: (String) -> Unit, modifier: Modifier) {
    val onCodigoDetectadoActualizado = rememberUpdatedState(onCodigoDetectado)
    val session = remember { AVCaptureSession() }
    val previewLayer = remember { AVCaptureVideoPreviewLayer(session = session) }

    val delegado = remember {
        object : NSObject(), AVCaptureMetadataOutputObjectsDelegateProtocol {
            override fun captureOutput(
                output: AVCaptureOutput,
                didOutputMetadataObjects: List<*>,
                fromConnection: AVCaptureConnection,
            ) {
                val objeto = didOutputMetadataObjects.firstOrNull() as? AVMetadataMachineReadableCodeObject ?: return
                val texto = objeto.stringValue ?: return
                onCodigoDetectadoActualizado.value(texto)
            }
        }
    }

    UIKitView(
        factory = {
            val contenedor = UIView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0))
            previewLayer.videoGravity = AVLayerVideoGravityResizeAspectFill
            contenedor.layer.addSublayer(previewLayer)
            contenedor
        },
        modifier = modifier,
        update = { vista ->
            // Sin animación implícita: el preview debe ajustarse al tamaño del contenedor en cada
            // recomposición sin el "fundido" que Core Animation aplica por defecto a los cambios de frame.
            CATransaction.begin()
            CATransaction.setDisableActions(true)
            previewLayer.frame = vista.bounds
            CATransaction.commit()
        },
    )

    DisposableEffect(Unit) {
        val dispositivo = AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo)
        val entrada = dispositivo?.let { AVCaptureDeviceInput.deviceInputWithDevice(it, null) as? AVCaptureDeviceInput }
        val salida = AVCaptureMetadataOutput()

        if (entrada != null && session.canAddInput(entrada) && session.canAddOutput(salida)) {
            session.addInput(entrada)
            session.addOutput(salida)
            salida.setMetadataObjectsDelegate(delegado, queue = dispatch_get_main_queue())
            salida.metadataObjectTypes = listOf(AVMetadataObjectTypeQRCode)
            session.startRunning()
        }
        // Si no se pudo armar la sesión (sin cámara, dispositivo simulador sin hardware, etc.) queda un
        // preview vacío sin decodificación -- sin crashear la pantalla, `A-03` sigue siendo la salida.

        onDispose {
            session.stopRunning()
        }
    }
}
