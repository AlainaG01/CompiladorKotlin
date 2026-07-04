package app

import lexico.AnalizadorLexico
import lexico.TipoToken
import sintactico.AnalizadorSintactico

// ──────────────────────────────────────────────────────────────
// CAPA DE APLICACIÓN — orquesta las capas lexico -> sintactico
// y presenta el resultado por consola.
// ──────────────────────────────────────────────────────────────

fun compilar(nombre: String, codigo: String) {
    println("\n===========================================================")
    println("  PROGRAMA: $nombre")
    println("===========================================================")

    val lexico = AnalizadorLexico(codigo)
    val tokens = lexico.analizar()

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
