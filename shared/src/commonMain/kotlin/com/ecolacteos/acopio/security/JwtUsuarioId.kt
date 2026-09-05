package com.ecolacteos.acopio.security

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * Extrae el claim `usuarioId` del payload de un JWT (`PROMPT_FASE_03.md §4`). `LoginResponse` no lo trae
 * (`MOBILE_DATA_MAPPING.md §5.1`: solo `token`, `rol`, `nombre`, `expiraEnSegundos`), pero es un claim del
 * JWT (`MOBILE_ARCHITECTURE.md §4`: "Claims: `sub`=email, `rol`, `usuarioId`") y hace falta para
 * `usuario_id NOT NULL` en las 4 tablas `*_local` de la Fase 4.
 *
 * ⚠️ **No verifica la firma, a propósito.** El cliente no tiene (ni debe tener) el secreto HS256 con el que
 * el backend firma -- embeberlo en la app sería inseguro y, además, inútil: el backend ya revalida el JWT
 * en cada request (`MOBILE_ARCHITECTURE.md §4`), así que un token alterado nunca pasaría de ahí. Esta
 * función solo lee un campo de un JSON en el que el cliente igual no puede confiar para nada que importe
 * -- confiar en el `usuarioId` leído acá para autorizar algo sería el error real; acá solo se usa para
 * escribirlo como dato local (`usuario_id` de las tablas `*_local`), no para tomar decisiones de seguridad.
 *
 * Un token malformado (menos o más de 3 segmentos, base64url inválido, JSON inválido, o sin el claim)
 * **no lanza** -- devuelve `null`, tratado como sesión inválida por quien llama.
 */
fun usuarioIdDesdeJwt(token: String): String? {
    val segmentos = token.split(".")
    if (segmentos.size != 3) return null

    val payloadBytes = decodificarBase64Url(segmentos[1]) ?: return null
    // decodeToString() nunca lanza (reemplaza secuencias UTF-8 invalidas por U+FFFD en vez de tirar) --
    // un payload con bytes invalidos simplemente no va a parsear como JSON despues, y cae al `null` de abajo.
    val payloadTexto = payloadBytes.decodeToString()

    return try {
        Json.parseToJsonElement(payloadTexto).jsonObject["usuarioId"]
            ?.let { it as? JsonPrimitive }
            ?.content
    } catch (e: SerializationException) {
        null // payload no es JSON valido
    } catch (e: IllegalArgumentException) {
        null // payload es JSON valido pero no un objeto (ej. un array o un primitivo suelto)
    }
}

/**
 * Decodifica base64url (RFC 4648 §5): alfabeto con `-`/`_` en vez de `+`/`/`, y **sin padding** -- el
 * formato exacto en que viaja cada segmento de un JWT (RFC 7515 §2). Implementación propia en vez de
 * `kotlin.io.encoding.Base64` -- misma razón que `Uuid.kt`: ~20 líneas auditables sin depender de una API
 * todavía marcada experimental en la versión de Kotlin de este proyecto.
 *
 * `null` ante cualquier carácter fuera del alfabeto -- nunca lanza.
 */
private const val ALFABETO_BASE64URL = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

internal fun decodificarBase64Url(texto: String): ByteArray? {
    if (texto.isEmpty()) return null

    val valores = IntArray(texto.length)
    for (i in texto.indices) {
        val valor = ALFABETO_BASE64URL.indexOf(texto[i])
        if (valor < 0) return null
        valores[i] = valor
    }

    val salida = ByteArray((texto.length * 6) / 8)
    var buffer = 0
    var bitsEnBuffer = 0
    var posicion = 0
    for (valor in valores) {
        buffer = (buffer shl 6) or valor
        bitsEnBuffer += 6
        if (bitsEnBuffer >= 8) {
            bitsEnBuffer -= 8
            salida[posicion++] = ((buffer shr bitsEnBuffer) and 0xFF).toByte()
        }
    }
    return salida
}
