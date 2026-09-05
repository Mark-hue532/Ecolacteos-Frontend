package com.ecolacteos.acopio.core

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * Utilidades sobre `kotlinx.datetime.LocalDateTime` -- hora de pared, sin zona embebida.
 *
 * CLAUDE.md §3.2: todo `LocalDateTime` que **llega del backend** (`fechaHora`, `creadoEn`,
 * `sincronizadoEn`, ...) se muestra tal cual llega, sin convertir jamás a `Instant` ni a la zona del
 * dispositivo -- por eso este archivo **no** define ninguna función `LocalDateTime -> Instant`.
 *
 * [ahoraComoFechaHora] es distinto: genera la hora local **del propio dispositivo** para un campo que el
 * dispositivo produce (ej. `fechaHora` de una captura offline). Ahí sí hace falta el reloj y la zona del
 * dispositivo -- es el origen del dato, no una reinterpretación de un dato ajeno.
 *
 * `Clock`/`Instant` se importan de `kotlin.time` (stdlib), no de `kotlinx.datetime`: desde
 * `kotlinx-datetime 0.7.0` esos tipos son typealiases a los de `kotlin.time` y `TimeZone.toLocalDateTime`
 * ya espera un `kotlin.time.Instant` -- usar el tipo del stdlib directo evita depender de cómo cada
 * versión reexporte el alias.
 */
fun ahoraComoFechaHora(reloj: Clock = Clock.System, zona: TimeZone = TimeZone.currentSystemDefault()): LocalDateTime =
    reloj.now().toLocalDateTime(zona)

/**
 * Formatea a ISO-8601 sin offset (`"2026-09-04T10:15:30"`), igual al formato que produce
 * `java.time.LocalDateTime` del backend (`MOBILE_DATA_MAPPING.md §1.4`).
 */
fun LocalDateTime.aTextoIso8601(): String = this.toString()

/** Parsea el formato ISO-8601 sin offset que envía el backend para todo campo `LocalDateTime`. */
fun fechaHoraDesdeTexto(texto: String): LocalDateTime = LocalDateTime.parse(texto)
