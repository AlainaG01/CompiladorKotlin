package sintactico

import lexico.OPERADORES_RELACIONALES
import lexico.Token
import lexico.TipoToken
import modelo.Simbolo
import modelo.TipoDato
import simbolos.TablaSimbolos

// ──────────────────────────────────────────────────────────────
// CAPA SINTÁCTICA — FASE 2 (sintáctico) + FASE 3 (semántico embebido)
// ──────────────────────────────────────────────────────────────
class AnalizadorSintactico(private val tokens: List<Token>) {
    private var pos = 0
    val errores = mutableListOf<String>()
    val tablaSimbolos = TablaSimbolos()

    private fun actual() = tokens[pos]
    private fun avanzar() { if (pos < tokens.size - 1) pos++ }

    private fun consumir(tipo: TipoToken, esperado: String = ""): Token? {
        val t = actual()
        return if (t.tipo == tipo) { avanzar(); t }
        else {
            val msg = if (esperado.isNotEmpty()) esperado else tipo.toString()
            errores.add("ERROR SINTACTICO [linea ${t.linea}, columna ${t.columna}]: se esperaba $msg pero se encontro '${t.valor}'")
            null
        }
    }

    private fun consumirReservada(palabra: String): Token? {
        val t = actual()
        return if (t.tipo == TipoToken.RESERVADA && t.valor == palabra) { avanzar(); t }
        else { errores.add("ERROR SINTACTICO [linea ${t.linea}, columna ${t.columna}]: se esperaba '$palabra' pero se encontro '${t.valor}'"); null }
    }

    fun analizar() {
        tablaSimbolos.entrarAmbito()
        while (actual().tipo != TipoToken.FIN_ARCHIVO) parsearDeclaracion()
        tablaSimbolos.salirAmbito()
    }

    private fun parsearDeclaracion() {
        val t = actual()
        when {
            t.tipo == TipoToken.RESERVADA && t.valor == "varjay"    -> parsearVar(false)
            t.tipo == TipoToken.RESERVADA && t.valor == "constjay"  -> parsearVar(true)
            t.tipo == TipoToken.RESERVADA && t.valor == "ifjay"     -> parsearIf()
            t.tipo == TipoToken.RESERVADA && t.valor == "whilejay"  -> parsearWhile()
            t.tipo == TipoToken.RESERVADA && t.valor == "forjay"    -> parsearFor()
            t.tipo == TipoToken.RESERVADA && t.valor == "funjay"    -> parsearFun()
            t.tipo == TipoToken.RESERVADA && t.valor == "printjay"  -> parsearPrint()
            t.tipo == TipoToken.RESERVADA && t.valor == "breakjay"  -> parsearBreak()
            t.tipo == TipoToken.IDENTIFICADOR -> parsearAsignacion()
            t.tipo == TipoToken.LLAVE_A       -> parsearBloque()
            else -> { errores.add("ERROR SINTACTICO [linea ${t.linea}, columna ${t.columna}]: declaracion inesperada '${t.valor}'"); avanzar() }
        }
    }

    // varjay | constjay  tipo  id := expresion ;
    private fun parsearVar(esConst: Boolean) {
        val lineaDecl = actual().linea
        avanzar()
        val tipoTok = actual()
        val tipoDato = when (tipoTok.valor) {
            "intjay"    -> TipoDato.INTJAY
            "floatjay"  -> TipoDato.FLOATJAY
            "stringjay" -> TipoDato.STRINGJAY
            else -> { errores.add("ERROR SINTACTICO [linea ${tipoTok.linea}, columna ${tipoTok.columna}]: tipo desconocido '${tipoTok.valor}'"); TipoDato.DESCONOCIDO }
        }
        avanzar()
        val idTok = consumir(TipoToken.IDENTIFICADOR, "identificador") ?: return
        consumir(TipoToken.ASIGNACION, ":=") ?: return
        val (_, valor) = parsearExpresion()
        consumir(TipoToken.PUNTO_COMA, ";") ?: return
        val ok = tablaSimbolos.declarar(Simbolo(idTok.valor, tipoDato, valor, esConst, lineaDecl))
        if (!ok) errores.add("ERROR SEMANTICO [linea $lineaDecl, columna ${idTok.columna}]: '${idTok.valor}' ya fue declarado en este ambito")
    }

    private fun parsearAsignacion() {
        val idTok = consumir(TipoToken.IDENTIFICADOR, "identificador") ?: return
        val simb = tablaSimbolos.buscar(idTok.valor)
        if (simb == null) errores.add("ERROR SEMANTICO [linea ${idTok.linea}, columna ${idTok.columna}]: '${idTok.valor}' no fue declarado")
        else if (simb.esConstante) errores.add("ERROR SEMANTICO [linea ${idTok.linea}, columna ${idTok.columna}]: '${idTok.valor}' es constante y no puede reasignarse")
        consumir(TipoToken.ASIGNACION, ":=") ?: return
        val (_, valor) = parsearExpresion()
        consumir(TipoToken.PUNTO_COMA, ";") ?: return
        if (simb != null && !simb.esConstante) tablaSimbolos.actualizar(idTok.valor, valor)
    }

    private fun parsearIf() {
        consumirReservada("ifjay")
        consumir(TipoToken.PARENTESIS_A, "(")
        parsearExpresion()
        consumir(TipoToken.PARENTESIS_C, ")")
        parsearBloque()
        if (actual().tipo == TipoToken.RESERVADA && actual().valor == "elsejay") { avanzar(); parsearBloque() }
    }

    private fun parsearWhile() {
        consumirReservada("whilejay")
        consumir(TipoToken.PARENTESIS_A, "(")
        parsearExpresion()
        consumir(TipoToken.PARENTESIS_C, ")")
        parsearBloque()
    }

    // forjay ( inicializacion ; condicion ; incremento ) { bloque }
    // La inicializacion admite una declaracion "varjay" completa (con su propio ';')
    // o una asignacion simple "id := expresion". El incremento es una asignacion
    // simple sin ';' final, ya que el propio delimitador ')' cierra la sentencia.
    private fun parsearFor() {
        consumirReservada("forjay")
        consumir(TipoToken.PARENTESIS_A, "(")
        tablaSimbolos.entrarAmbito()

        if (actual().tipo == TipoToken.RESERVADA && actual().valor == "varjay") {
            parsearVar(false)
        } else if (actual().tipo == TipoToken.IDENTIFICADOR) {
            parsearAsignacion()
        } else {
            errores.add("ERROR SINTACTICO [linea ${actual().linea}, columna ${actual().columna}]: se esperaba inicializacion del for")
        }

        parsearExpresion()
        consumir(TipoToken.PUNTO_COMA, ";")

        if (actual().tipo == TipoToken.IDENTIFICADOR) {
            parsearAsignacionSinPuntoComa()
        } else {
            errores.add("ERROR SINTACTICO [linea ${actual().linea}, columna ${actual().columna}]: se esperaba incremento del for")
        }

        consumir(TipoToken.PARENTESIS_C, ")")
        parsearBloque()  // el cuerpo abre su propio ambito anidado dentro del ambito del for
        tablaSimbolos.salirAmbito()
    }

    // Variante de asignacion usada en el encabezado del for: no consume ';'
    private fun parsearAsignacionSinPuntoComa() {
        val idTok = consumir(TipoToken.IDENTIFICADOR, "identificador") ?: return
        val simb = tablaSimbolos.buscar(idTok.valor)
        if (simb == null) errores.add("ERROR SEMANTICO [linea ${idTok.linea}, columna ${idTok.columna}]: '${idTok.valor}' no fue declarado")
        else if (simb.esConstante) errores.add("ERROR SEMANTICO [linea ${idTok.linea}, columna ${idTok.columna}]: '${idTok.valor}' es constante y no puede reasignarse")
        consumir(TipoToken.ASIGNACION, ":=") ?: return
        val (_, valor) = parsearExpresion()
        if (simb != null && !simb.esConstante) tablaSimbolos.actualizar(idTok.valor, valor)
    }

    // breakjay ;
    private fun parsearBreak() {
        consumirReservada("breakjay")
        consumir(TipoToken.PUNTO_COMA, ";")
    }

    private fun parsearFun() {
        consumirReservada("funjay")
        val nomTok = consumir(TipoToken.IDENTIFICADOR, "nombre de funcion") ?: return
        tablaSimbolos.declarar(Simbolo(nomTok.valor, TipoDato.DESCONOCIDO, null, false, nomTok.linea))
        consumir(TipoToken.PARENTESIS_A, "(")
        while (actual().tipo != TipoToken.PARENTESIS_C && actual().tipo != TipoToken.FIN_ARCHIVO) avanzar()
        consumir(TipoToken.PARENTESIS_C, ")")
        parsearBloque()
    }

    private fun parsearPrint() {
        consumirReservada("printjay")
        consumir(TipoToken.PARENTESIS_A, "(")
        parsearExpresion()
        consumir(TipoToken.PARENTESIS_C, ")")
        consumir(TipoToken.PUNTO_COMA, ";")
    }

    private fun parsearBloque() {
        consumir(TipoToken.LLAVE_A, "{") ?: return
        tablaSimbolos.entrarAmbito()
        while (actual().tipo != TipoToken.LLAVE_C && actual().tipo != TipoToken.FIN_ARCHIVO) parsearDeclaracion()
        consumir(TipoToken.LLAVE_C, "}")
        tablaSimbolos.salirAmbito()
    }

    // ────────────────────────────────────────────────────────────
    // Jerarquia de expresiones, de menor a mayor precedencia:
    //   expresion   := expresionOr
    //   expresionOr := expresionAnd ( "orjay"  expresionAnd )*
    //   expresionAnd:= relacional   ( "andjay" relacional   )*
    //   relacional  := suma ( ("==" | "!=" | "<" | ">" | "<=" | ">=") suma )*
    //   suma        := termino ( ("+" | "-") termino )*
    //   termino     := unario  ( ("*" | "/" | "%") unario )*
    //   unario      := ("-" | "notjay") unario | primario
    //   primario    := NUMERO | CADENA | truejay | falsejay | identificador
    //                | "(" expresion ")"
    // Esto respeta la precedencia matematica tradicional (paréntesis > unario >
    // */ > +- > relacionales > and > or) y le da uso real a las palabras
    // reservadas logicas ("andjay", "orjay", "notjay"), que antes estaban
    // declaradas mas nunca conectadas a la gramatica.
    // ────────────────────────────────────────────────────────────

    private fun parsearExpresion(): Pair<TipoDato, Any?> = parsearOr()

    private fun parsearOr(): Pair<TipoDato, Any?> {
        var (tipoIzq, valIzq) = parsearAnd()
        while (actual().tipo == TipoToken.RESERVADA && actual().valor == "orjay") {
            val opTok = actual(); avanzar()
            val (tipoDer, _) = parsearAnd()
            if (tipoIzq != TipoDato.BOOLEAN || tipoDer != TipoDato.BOOLEAN)
                errores.add("ERROR SEMANTICO [linea ${opTok.linea}, columna ${opTok.columna}]: 'orjay' requiere operandos booleanos, se recibio $tipoIzq y $tipoDer")
            tipoIzq = TipoDato.BOOLEAN
        }
        return Pair(tipoIzq, valIzq)
    }

    private fun parsearAnd(): Pair<TipoDato, Any?> {
        var (tipoIzq, valIzq) = parsearRelacional()
        while (actual().tipo == TipoToken.RESERVADA && actual().valor == "andjay") {
            val opTok = actual(); avanzar()
            val (tipoDer, _) = parsearRelacional()
            if (tipoIzq != TipoDato.BOOLEAN || tipoDer != TipoDato.BOOLEAN)
                errores.add("ERROR SEMANTICO [linea ${opTok.linea}, columna ${opTok.columna}]: 'andjay' requiere operandos booleanos, se recibio $tipoIzq y $tipoDer")
            tipoIzq = TipoDato.BOOLEAN
        }
        return Pair(tipoIzq, valIzq)
    }

    private fun parsearRelacional(): Pair<TipoDato, Any?> {
        var (tipoIzq, valIzq) = parsearSuma()
        while (actual().tipo == TipoToken.OPERADOR && actual().valor in OPERADORES_RELACIONALES) {
            val op = actual(); avanzar()
            val (tipoDer, _) = parsearSuma()
            if (tipoIzq != tipoDer && tipoIzq != TipoDato.DESCONOCIDO && tipoDer != TipoDato.DESCONOCIDO)
                errores.add("ERROR SEMANTICO [linea ${op.linea}, columna ${op.columna}]: operacion '${op.valor}' entre tipos incompatibles $tipoIzq y $tipoDer")
            tipoIzq = TipoDato.BOOLEAN
        }
        return Pair(tipoIzq, valIzq)
    }

    private fun parsearSuma(): Pair<TipoDato, Any?> {
        var (tipoIzq, valIzq) = parsearTermino()
        while (actual().tipo == TipoToken.OPERADOR && actual().valor in listOf("+", "-")) {
            val op = actual(); avanzar()
            val (tipoDer, _) = parsearTermino()
            if (tipoIzq != tipoDer && tipoIzq != TipoDato.DESCONOCIDO && tipoDer != TipoDato.DESCONOCIDO)
                errores.add("ERROR SEMANTICO [linea ${op.linea}, columna ${op.columna}]: operacion '${op.valor}' entre tipos incompatibles $tipoIzq y $tipoDer")
        }
        return Pair(tipoIzq, valIzq)
    }

    private fun parsearTermino(): Pair<TipoDato, Any?> {
        var (tipoIzq, valIzq) = parsearUnario()
        while (actual().tipo == TipoToken.OPERADOR && actual().valor in listOf("*", "/", "%")) {
            val op = actual(); avanzar()
            val (tipoDer, _) = parsearUnario()
            if (tipoIzq != tipoDer && tipoIzq != TipoDato.DESCONOCIDO && tipoDer != TipoDato.DESCONOCIDO)
                errores.add("ERROR SEMANTICO [linea ${op.linea}, columna ${op.columna}]: operacion '${op.valor}' entre tipos incompatibles $tipoIzq y $tipoDer")
        }
        return Pair(tipoIzq, valIzq)
    }

    // Operadores unarios: negacion aritmetica "-" y negacion logica "notjay"
    private fun parsearUnario(): Pair<TipoDato, Any?> {
        val t = actual()
        if (t.tipo == TipoToken.OPERADOR && t.valor == "-") {
            avanzar()
            val (tipo, valor) = parsearUnario()
            if (tipo != TipoDato.INTJAY && tipo != TipoDato.FLOATJAY && tipo != TipoDato.DESCONOCIDO)
                errores.add("ERROR SEMANTICO [linea ${t.linea}, columna ${t.columna}]: el operador unario '-' requiere un tipo numerico, se recibio $tipo")
            val nuevoValor = when (valor) { is Int -> -valor; is Double -> -valor; else -> null }
            return Pair(tipo, nuevoValor)
        }
        if (t.tipo == TipoToken.RESERVADA && t.valor == "notjay") {
            avanzar()
            val (tipo, _) = parsearUnario()
            if (tipo != TipoDato.BOOLEAN && tipo != TipoDato.DESCONOCIDO)
                errores.add("ERROR SEMANTICO [linea ${t.linea}, columna ${t.columna}]: 'notjay' requiere un operando booleano, se recibio $tipo")
            return Pair(TipoDato.BOOLEAN, null)
        }
        return parsearPrimario()
    }

    private fun parsearPrimario(): Pair<TipoDato, Any?> {
        val t = actual()
        return when {
            t.tipo == TipoToken.NUMERO_ENTERO -> { avanzar(); Pair(TipoDato.INTJAY, t.valor.toIntOrNull()) }
            t.tipo == TipoToken.NUMERO_REAL   -> { avanzar(); Pair(TipoDato.FLOATJAY, t.valor.toDoubleOrNull()) }
            t.tipo == TipoToken.CADENA        -> { avanzar(); Pair(TipoDato.STRINGJAY, t.valor) }
            t.tipo == TipoToken.RESERVADA && t.valor in listOf("truejay", "falsejay") -> { avanzar(); Pair(TipoDato.BOOLEAN, t.valor == "truejay") }
            t.tipo == TipoToken.IDENTIFICADOR -> {
                val simb = tablaSimbolos.buscar(t.valor)
                if (simb == null) errores.add("ERROR SEMANTICO [linea ${t.linea}, columna ${t.columna}]: '${t.valor}' no fue declarado")
                avanzar(); Pair(simb?.tipo ?: TipoDato.DESCONOCIDO, simb?.valor)
            }
            t.tipo == TipoToken.PARENTESIS_A -> { avanzar(); val r = parsearExpresion(); consumir(TipoToken.PARENTESIS_C, ")"); r }
            else -> { errores.add("ERROR SINTACTICO [linea ${t.linea}, columna ${t.columna}]: factor inesperado '${t.valor}'"); avanzar(); Pair(TipoDato.DESCONOCIDO, null) }
        }
    }
}
