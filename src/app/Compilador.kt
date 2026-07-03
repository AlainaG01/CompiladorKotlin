package app

import lexico.AnalizadorLexico
import lexico.TipoToken
import sintactico.AnalizadorSintactico

// ──────────────────────────────────────────────────────────────
// CAPA DE APLICACIÓN — orquesta las capas lexico -> sintactico
// y presenta el resultado por consola.
// ──────────────────────────────────────────────────────────────

fun compilar(nombre: String, codigo: String) {
    println("\n\n===========================================================")
    println("  PROGRAMA: $nombre")
    println("===========================================================")
    println("CODIGO FUENTE:\n$codigo")

    println("\n--- FASE 1: ANALISIS LEXICO --------------------------------")
    val lexico = AnalizadorLexico(codigo)
    val tokens = lexico.analizar()
    tokens.filter { it.tipo != TipoToken.FIN_ARCHIVO }.forEach { println("  $it") }
    if (lexico.errores.isNotEmpty()) { println("\nERRORES LEXICOS:"); lexico.errores.forEach { println("  !! $it") } }

    println("\n  -> Representacion interna de literales numericos:")
    tokens.filter { it.tipo == TipoToken.NUMERO_ENTERO }.forEach {
        println("     ${it.valor} -> binario (16 bits): ${it.valor.toInt().toString(2).padStart(16, '0')}")
    }
    tokens.filter { it.tipo == TipoToken.NUMERO_REAL }.forEach {
        println("     ${it.valor} -> IEEE-754 (32 bits): ${java.lang.Float.floatToIntBits(it.valor.toFloat()).toString(2).padStart(32, '0')}")
    }

    println("\n--- FASE 2: SINTACTICO + FASE 3: SEMANTICO ----------------")
    val parser = AnalizadorSintactico(tokens)
    parser.analizar()
    parser.tablaSimbolos.imprimir()

    val todosErrores = lexico.errores + parser.errores
    if (todosErrores.isEmpty()) {
        println("\n  [OK] PROGRAMA ACEPTADO - Sin errores.")
    } else {
        println("\n  [XX] PROGRAMA RECHAZADO - Errores encontrados:")
        todosErrores.forEach { println("    >> $it") }
    }
}

/**
 * Lee un archivo fuente JayLang (.jay) desde disco y lo compila.
 * Cumple el requisito de "leer correctamente archivos fuente" de forma real,
 * ademas de las pruebas embebidas que ya trae el programa.
 */
fun compilarArchivo(ruta: String) {
    val archivo = java.io.File(ruta)
    if (!archivo.exists()) {
        println("ERROR: no se encontro el archivo fuente '$ruta'")
        return
    }
    val codigo = archivo.readText()
    compilar(archivo.name, codigo)
}
