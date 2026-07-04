package lexico

// ──────────────────────────────────────────────────────────────
// CAPA LÉXICA — FASE 1: ANÁLISIS LÉXICO
// ──────────────────────────────────────────────────────────────
class AnalizadorLexico(private val fuente: String) {
    private var pos = 0
    private var linea = 1
    private var columna = 1          // columna del carácter que se está leyendo actualmente
    val tokens = mutableListOf<Token>()
    val errores = mutableListOf<String>()

    /** Avanza un carácter, manteniendo linea/columna sincronizadas. */
    private fun avanzarChar() {
        if (pos < fuente.length && fuente[pos] == '\n') { linea++; columna = 1 } else { columna++ }
        pos++
    }

    private fun agregar(tipo: TipoToken, valor: String, lin: Int, col: Int) {
        tokens.add(Token(tipo, valor, lin, col))
    }

    fun analizar(): List<Token> {
        while (pos < fuente.length) {
            saltarEspacios()
            if (pos >= fuente.length) break
            val c = fuente[pos]
            val lin = linea
            val col = columna
            when {
                c == '\n' -> avanzarChar()
                c == '/' && pos+1 < fuente.length && fuente[pos+1] == '/' -> saltarComentarioLinea()
                c == '/' && pos+1 < fuente.length && fuente[pos+1] == '*' -> saltarComentarioBloque(lin, col)
                c == '"'  -> leerCadena(lin, col)
                c.isDigit() -> leerNumero(lin, col)
                c.isLetter() || c == '_' -> leerIdentificadorOReservada(lin, col)
                c == ':' && pos+1 < fuente.length && fuente[pos+1] == '=' -> { agregar(TipoToken.ASIGNACION, ":=", lin, col); avanzarChar(); avanzarChar() }
                c == '=' && pos+1 < fuente.length && fuente[pos+1] == '=' -> { agregar(TipoToken.OPERADOR,  "==", lin, col); avanzarChar(); avanzarChar() }
                c == '=' -> { agregar(TipoToken.ASIGNACION, "=", lin, col); avanzarChar()}
                c == '!' && pos+1 < fuente.length && fuente[pos+1] == '=' -> { agregar(TipoToken.OPERADOR,  "!=", lin, col); avanzarChar(); avanzarChar() }
                c == '<' && pos+1 < fuente.length && fuente[pos+1] == '=' -> { agregar(TipoToken.OPERADOR,  "<=", lin, col); avanzarChar(); avanzarChar() }
                c == '>' && pos+1 < fuente.length && fuente[pos+1] == '=' -> { agregar(TipoToken.OPERADOR,  ">=", lin, col); avanzarChar(); avanzarChar() }
                // Operadores matemáticos tradicionales (+ - * /) más relacionales (< > %)
                c in "+-*/<>%" -> { agregar(TipoToken.OPERADOR, c.toString(), lin, col); avanzarChar() }
                c == '(' -> { agregar(TipoToken.PARENTESIS_A, "(", lin, col); avanzarChar() }
                c == ')' -> { agregar(TipoToken.PARENTESIS_C, ")", lin, col); avanzarChar() }
                c == '{' -> { agregar(TipoToken.LLAVE_A,      "{", lin, col); avanzarChar() }
                c == '}' -> { agregar(TipoToken.LLAVE_C,      "}", lin, col); avanzarChar() }
                c == ';' -> { agregar(TipoToken.PUNTO_COMA,   ";", lin, col); avanzarChar() }
                c == ',' -> { agregar(TipoToken.COMA,         ",", lin, col); avanzarChar() }
                else -> {
                    errores.add("ERROR LEXICO [linea $lin, columna $col]: caracter desconocido '$c'")
                    agregar(TipoToken.DESCONOCIDO, c.toString(), lin, col)
                    avanzarChar()
                }
            }
        }
        tokens.add(Token(TipoToken.FIN_ARCHIVO, "EOF", linea, columna))
        return tokens
    }

    private fun saltarEspacios() { while (pos < fuente.length && fuente[pos] in " \t\r") avanzarChar() }

    private fun saltarComentarioLinea() { while (pos < fuente.length && fuente[pos] != '\n') avanzarChar() }

    private fun saltarComentarioBloque(lin: Int, col: Int) {
        avanzarChar(); avanzarChar() // consume "/*"
        while (pos < fuente.length && !(fuente[pos] == '*' && pos+1 < fuente.length && fuente[pos+1] == '/')) avanzarChar()
        if (pos >= fuente.length) {
            errores.add("ERROR LEXICO [linea $lin, columna $col]: comentario de bloque sin cerrar (falta '*/')")
        } else {
            avanzarChar(); avanzarChar() // consume "*/"
        }
    }

    private fun leerCadena(lin: Int, col: Int) {
        avanzarChar() // consume comilla de apertura
        val sb = StringBuilder()
        var cerrada = false
        while (pos < fuente.length && fuente[pos] != '\n') {
            if (fuente[pos] == '"') { cerrada = true; break }
            if (fuente[pos] == '\\' && pos+1 < fuente.length) {
                // secuencias de escape básicas: \" \\ \n \t
                when (fuente[pos+1]) {
                    '"'  -> { sb.append('"');  avanzarChar(); avanzarChar() }
                    '\\' -> { sb.append('\\'); avanzarChar(); avanzarChar() }
                    'n'  -> { sb.append('\n'); avanzarChar(); avanzarChar() }
                    't'  -> { sb.append('\t'); avanzarChar(); avanzarChar() }
                    else -> { sb.append(fuente[pos]); avanzarChar() }
                }
            } else {
                sb.append(fuente[pos]); avanzarChar()
            }
        }
        if (cerrada) avanzarChar() else errores.add("ERROR LEXICO [linea $lin, columna $col]: cadena de texto sin cerrar")
        agregar(TipoToken.CADENA, sb.toString(), lin, col)
    }

    private fun leerNumero(lin: Int, col: Int) {
        val inicio = pos
        while (pos < fuente.length && fuente[pos].isDigit()) avanzarChar()
        if (pos < fuente.length && fuente[pos] == '.' && pos+1 < fuente.length && fuente[pos+1].isDigit()) {
            avanzarChar()
            while (pos < fuente.length && fuente[pos].isDigit()) avanzarChar()
            agregar(TipoToken.NUMERO_REAL, fuente.substring(inicio, pos), lin, col)
        } else if (pos < fuente.length && fuente[pos] == '.') {
            // ej. "5." sin dígitos después del punto -> error léxico explícito en vez de fallar en silencio
            errores.add("ERROR LEXICO [linea $lin, columna $col]: numero decimal mal formado '${fuente.substring(inicio, pos+1)}'")
            avanzarChar()
            agregar(TipoToken.NUMERO_REAL, fuente.substring(inicio, pos), lin, col)
        } else {
            agregar(TipoToken.NUMERO_ENTERO, fuente.substring(inicio, pos), lin, col)
        }
    }

    private fun leerIdentificadorOReservada(lin: Int, col: Int) {
        val inicio = pos
        while (pos < fuente.length && (fuente[pos].isLetterOrDigit() || fuente[pos] == '_')) avanzarChar()
        val lexema = fuente.substring(inicio, pos)
        val tipo = if (lexema in PALABRAS_RESERVADAS) TipoToken.RESERVADA else TipoToken.IDENTIFICADOR
        agregar(tipo, lexema, lin, col)
    }
}
