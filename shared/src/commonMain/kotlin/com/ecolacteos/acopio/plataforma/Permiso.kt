package com.ecolacteos.acopio.plataforma

/** Los 2 permisos del sistema de esta fase (`MOBILE_SCREENS.md §12`) -- ninguno más se agrega sin pasar por acá. */
enum class Permiso {
    CAMARA,
    UBICACION,
}

/**
 * Estado de un [Permiso] tal como lo necesita la UI (`§12` regla 3): distinguir "todavía no se preguntó" /
 * "se preguntó y se puede volver a preguntar" de "denegado para siempre, la única salida son los ajustes
 * del sistema". Ningún permiso de este catálogo es obligatorio para operar (`§12` regla 4) -- este enum
 * nunca se usa para bloquear una pantalla, solo para decidir qué explicación mostrar.
 */
enum class EstadoPermiso {
    CONCEDIDO,
    DENEGADO,
    DENEGADO_PERMANENTE,
    NO_DETERMINADO,
}
