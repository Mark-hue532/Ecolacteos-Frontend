package com.ecolacteos.acopio.plataforma

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Vista en vivo de la cámara con lectura de QR (`A-02`, `MOBILE_SCREENS.md §5`). `expect`/`actual` de UI,
 * no una interfaz inyectable por Koin: la cámara necesita renderizar un `PreviewView`/`AVCaptureVideoPreviewLayer`
 * dentro del árbol de Compose, algo que un `ViewModel` no puede hacer (`CLAUDE.md §3.5`).
 *
 * [onCodigoDetectado] es la **única** vía de comunicación hacia afuera: reporta el texto crudo decodificado,
 * ni una vez más, ni interpretado. Quien la usa (`ui/screens/acopio/EscanearQrScreen.kt`) reenvía ese texto
 * al `ViewModel` como `Event` (`CLAUDE.md §3.4`, trampa #15 de `PROMPT_FASE_08A.md`) -- toda la resolución
 * contra `proveedor_cache`/red vive en el `ViewModel`, nunca acá. Este componente es tan "tonto" como
 * `CampoDecimal`: reporta un valor crudo, no decide nada.
 *
 * Se monta **solo** cuando el permiso de cámara ya está `CONCEDIDO` -- quien la usa es responsable de esa
 * verificación, este componente no vuelve a chequear permisos por su cuenta.
 *
 * ⚠️ El `actual` de iOS compila pero no se verificó en simulador/dispositivo desde esta sesión (Windows,
 * `CLAUDE.md §8`) -- ver el checkpoint.
 */
@Composable
expect fun EscanerQr(onCodigoDetectado: (String) -> Unit, modifier: Modifier)
