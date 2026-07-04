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

    // Limite de seguridad para evitar que un whilejay/forjay con una condicion
    // mal escrita (que nunca se vuelva falsa) cuelgue el programa para siempre.
    private val LIMITE_ITERACIONES = 100_000

    // Controla si las declaraciones/sentencias que se estan parseando en este
    // momento deben producir efectos visibles de verdad (imprimir con printjay,
    // actualizar valores de variables) o no. Se pone en "false" temporalmente
    // cuando se recorre una rama de ifjay que NO se tomo, o el cuerpo de un
    // whilejay/forjay que ya no debe volver a correr, para poder seguir
    // parseando esos tokens (y seguir reportando sus errores semanticos reales)
    // sin que ademas se comporten como si hubieran corrido.
    private var ejecutando = true

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

    // Un valor de condicion cuenta como verdadero solo si es literalmente el
    // booleano true. Cualquier otra cosa (false, null por error previo, etc.)
    // se trata como falso.
    private fun esVerdadero(valor: Any?): Boolean = valor == true

    fun analizar() {
        tablaSimbolos.entrarAmbito()
        while (actual().tipo != TipoToken.FIN_ARCHIVO) parsearDeclaracion()
        // No cerramos el ambito global aqui: se deja abierto a proposito para que
        // Compilador.kt pueda imprimir la tabla de simbolos con las variables del
        // programa despues de terminar el analisis.
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
        val (tipoExpr, valorExpr) = parsearExpresion()
        consumir(TipoToken.PUNTO_COMA, ";") ?: return
        var valorFinal = valorExpr
        if (tipoDato != TipoDato.DESCONOCIDO && tipoExpr != TipoDato.DESCONOCIDO && tipoDato != tipoExpr) {
            errores.add("ERROR SEMANTICO [linea $lineaDecl, columna ${idTok.columna}]: no se puede asignar un valor de tipo $tipoExpr a la variable '${idTok.valor}' declarada como $tipoDato")
            valorFinal = null
        }
        val ok = tablaSimbolos.declarar(Simbolo(idTok.valor, tipoDato, valorFinal, esConst, lineaDecl))
        if (!ok) errores.add("ERROR SEMANTICO [linea $lineaDecl, columna ${idTok.columna}]: '${idTok.valor}' ya fue declarado en este ambito")
    }

    private fun parsearAsignacion() {
        val idTok = consumir(TipoToken.IDENTIFICADOR, "identificador") ?: return
        val simb = tablaSimbolos.buscar(idTok.valor)
        if (simb == null) errores.add("ERROR SEMANTICO [linea ${idTok.linea}, columna ${idTok.columna}]: '${idTok.valor}' no fue declarado")
        else if (simb.esConstante) errores.add("ERROR SEMANTICO [linea ${idTok.linea}, columna ${idTok.columna}]: '${idTok.valor}' es constante y no puede reasignarse")
        consumir(TipoToken.ASIGNACION, ":=") ?: return
        val (tipoExpr, valorExpr) = parsearExpresion()
        consumir(TipoToken.PUNTO_COMA, ";") ?: return
        if (simb != null && !simb.esConstante) {
            if (simb.tipo != TipoDato.DESCONOCIDO && tipoExpr != TipoDato.DESCONOCIDO && simb.tipo != tipoExpr) {
                errores.add("ERROR SEMANTICO [linea ${idTok.linea}, columna ${idTok.columna}]: no se puede asignar un valor de tipo $tipoExpr a '${idTok.valor}' declarada como ${simb.tipo}")
            } else if (ejecutando) {
                tablaSimbolos.actualizar(idTok.valor, valorExpr)
            }
        }
    }

    // ifjay ( condicion ) { bloque } [ elsejay { bloque } ]
    // Ambas ramas se parsean siempre (para reportar sus errores reales), pero
    // solo la rama que corresponde al valor real de la condicion se ejecuta
    // con efectos visibles (prints, asignaciones). La otra se parsea con
    // "ejecutando = false".
    private fun parsearIf() {
        consumirReservada("ifjay")
        consumir(TipoToken.PARENTESIS_A, "(")
        val condTok = actual()
        val (tipoCond, valorCond) = parsearExpresion()
        if (tipoCond != TipoDato.BOOLEAN && tipoCond != TipoDato.DESCONOCIDO)
            errores.add("ERROR SEMANTICO [linea ${condTok.linea}, columna ${condTok.columna}]: la condicion de ifjay debe ser booleana, se recibio $tipoCond")
        consumir(TipoToken.PARENTESIS_C, ")")

        val esVerdadera = esVerdadero(valorCond)

        if (esVerdadera) {
            parsearBloque()
        } else {
            val previo = ejecutando
            ejecutando = false
            parsearBloque()
            ejecutando = previo
        }

        if (actual().tipo == TipoToken.RESERVADA && actual().valor == "elsejay") {
            avanzar()
            if (esVerdadera) {
                val previo = ejecutando
                ejecutando = false
                parsearBloque()
                ejecutando = previo
            } else {
                parsearBloque()
            }
        }
    }

    // whilejay ( condicion ) { bloque }
    // Tecnica usada para iterar de verdad sin tener un AST: se guarda la
    // posicion (indice de token) donde empieza la condicion, y cada vez que
    // hace falta volver a evaluarla, se "rebobina" pos hasta ahi y se vuelve a
    // parsear. Como parsear una expresion sin efectos secundarios es
    // determinista (misma lista de tokens, mismos valores en la tabla de
    // simbolos si nadie los cambio), esto es seguro y no duplica tokens.
    private fun parsearWhile() {
        consumirReservada("whilejay")
        consumir(TipoToken.PARENTESIS_A, "(")
        val posCondicion = pos
        var iteraciones = 0

        while (true) {
            pos = posCondicion
            val condTok = actual()
            val (tipoCond, valorCond) = parsearExpresion()
            if (tipoCond != TipoDato.BOOLEAN && tipoCond != TipoDato.DESCONOCIDO)
                errores.add("ERROR SEMANTICO [linea ${condTok.linea}, columna ${condTok.columna}]: la condicion de whilejay debe ser booleana, se recibio $tipoCond")
            consumir(TipoToken.PARENTESIS_C, ")")
            val posCuerpo = pos

            if (esVerdadero(valorCond) && iteraciones < LIMITE_ITERACIONES) {
                pos = posCuerpo
                parsearBloque()
                iteraciones++
            } else {
                if (iteraciones >= LIMITE_ITERACIONES)
                    errores.add("ERROR SEMANTICO [linea ${condTok.linea}, columna ${condTok.columna}]: whilejay supero el limite de $LIMITE_ITERACIONES iteraciones (posible ciclo infinito)")
                pos = posCuerpo
                val previo = ejecutando
                ejecutando = false
                parsearBloque()
                ejecutando = previo
                break
            }
        }
    }

    // forjay ( inicializacion ; condicion ; incremento ) { bloque }
    // Misma tecnica de rebobinado de "pos" que whilejay, adaptada a las 3
    // clausulas del for: la inicializacion se ejecuta una sola vez, la
    // condicion y el incremento se re-parsean en cada vuelta.
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

        val posCondicion = pos
        var iteraciones = 0

        while (true) {
            pos = posCondicion
            val condTok = actual()
            val (tipoCond, valorCond) = parsearExpresion()
            if (tipoCond != TipoDato.BOOLEAN && tipoCond != TipoDato.DESCONOCIDO)
                errores.add("ERROR SEMANTICO [linea ${condTok.linea}, columna ${condTok.columna}]: la condicion de forjay debe ser booleana, se recibio $tipoCond")
            consumir(TipoToken.PUNTO_COMA, ";")
            val posIncremento = pos

            // Primera pasada por el incremento: solo para saber donde termina
            // (llegar al ")"), sin aplicarlo todavia. Se aplica de verdad
            // despues de correr el cuerpo, mas abajo.
            val previoIncrementoDry = ejecutando
            ejecutando = false
            parsearAsignacionSinPuntoComa()
            ejecutando = previoIncrementoDry

            consumir(TipoToken.PARENTESIS_C, ")")
            val posCuerpo = pos

            if (esVerdadero(valorCond) && iteraciones < LIMITE_ITERACIONES) {
                pos = posCuerpo
                parsearBloque()
                // Ahora si se aplica el incremento de verdad para la siguiente vuelta.
                pos = posIncremento
                parsearAsignacionSinPuntoComa()
                iteraciones++
            } else {
                if (iteraciones >= LIMITE_ITERACIONES)
                    errores.add("ERROR SEMANTICO [linea ${condTok.linea}, columna ${condTok.columna}]: forjay supero el limite de $LIMITE_ITERACIONES iteraciones (posible ciclo infinito)")
                pos = posCuerpo
                val previo = ejecutando
                ejecutando = false
                parsearBloque()
                ejecutando = previo
                break
            }
        }

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
        if (simb != null && !simb.esConstante && ejecutando) tablaSimbolos.actualizar(idTok.valor, valor)
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
        val (_, valor) = parsearExpresion()
        consumir(TipoToken.PARENTESIS_C, ")")
        consumir(TipoToken.PUNTO_COMA, ";")
        if (ejecutando) println(">> ${valor ?: "null"}")
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
    // Ahora cada nivel calcula tanto el TIPO como el VALOR real de la
    // expresion (incluyendo booleanos reales en comparaciones, and/or/not),
    // para que ifjay/whilejay/forjay puedan decidir de verdad que rama tomar.
    // ────────────────────────────────────────────────────────────

    private fun parsearExpresion(): Pair<TipoDato, Any?> = parsearOr()

    private fun parsearOr(): Pair<TipoDato, Any?> {
        var (tipoIzq, valIzq) = parsearAnd()
        while (actual().tipo == TipoToken.RESERVADA && actual().valor == "orjay") {
            val opTok = actual(); avanzar()
            val (tipoDer, valDer) = parsearAnd()
            if (tipoIzq != TipoDato.BOOLEAN || tipoDer != TipoDato.BOOLEAN)
                errores.add("ERROR SEMANTICO [linea ${opTok.linea}, columna ${opTok.columna}]: 'orjay' requiere operandos booleanos, se recibio $tipoIzq y $tipoDer")
            valIzq = if (valIzq is Boolean && valDer is Boolean) (valIzq || valDer) else null
            tipoIzq = TipoDato.BOOLEAN
        }
        return Pair(tipoIzq, valIzq)
    }

    private fun parsearAnd(): Pair<TipoDato, Any?> {
        var (tipoIzq, valIzq) = parsearRelacional()
        while (actual().tipo == TipoToken.RESERVADA && actual().valor == "andjay") {
            val opTok = actual(); avanzar()
            val (tipoDer, valDer) = parsearRelacional()
            if (tipoIzq != TipoDato.BOOLEAN || tipoDer != TipoDato.BOOLEAN)
                errores.add("ERROR SEMANTICO [linea ${opTok.linea}, columna ${opTok.columna}]: 'andjay' requiere operandos booleanos, se recibio $tipoIzq y $tipoDer")
            valIzq = if (valIzq is Boolean && valDer is Boolean) (valIzq && valDer) else null
            tipoIzq = TipoDato.BOOLEAN
        }
        return Pair(tipoIzq, valIzq)
    }

    private fun parsearRelacional(): Pair<TipoDato, Any?> {
        var (tipoIzq, valIzq) = parsearSuma()
        while (actual().tipo == TipoToken.OPERADOR && actual().valor in OPERADORES_RELACIONALES) {
            val op = actual(); avanzar()
            val (tipoDer, valDer) = parsearSuma()
            if (tipoIzq != tipoDer && tipoIzq != TipoDato.DESCONOCIDO && tipoDer != TipoDato.DESCONOCIDO)
                errores.add("ERROR SEMANTICO [linea ${op.linea}, columna ${op.columna}]: operacion '${op.valor}' entre tipos incompatibles $tipoIzq y $tipoDer")
            valIzq = evaluarRelacional(op.valor, valIzq, valDer)
            tipoIzq = TipoDato.BOOLEAN
        }
        return Pair(tipoIzq, valIzq)
    }

    private fun parsearSuma(): Pair<TipoDato, Any?> {
        var (tipoIzq, valIzq) = parsearTermino()
        while (actual().tipo == TipoToken.OPERADOR && actual().valor in listOf("+", "-")) {
            val op = actual(); avanzar()
            val (tipoDer, valDer) = parsearTermino()
            if (tipoIzq != tipoDer && tipoIzq != TipoDato.DESCONOCIDO && tipoDer != TipoDato.DESCONOCIDO)
                errores.add("ERROR SEMANTICO [linea ${op.linea}, columna ${op.columna}]: operacion '${op.valor}' entre tipos incompatibles $tipoIzq y $tipoDer")
            valIzq = evaluarBinaria(op.valor, valIzq, valDer)
        }
        return Pair(tipoIzq, valIzq)
    }

    private fun parsearTermino(): Pair<TipoDato, Any?> {
        var (tipoIzq, valIzq) = parsearUnario()
        while (actual().tipo == TipoToken.OPERADOR && actual().valor in listOf("*", "/", "%")) {
            val op = actual(); avanzar()
            val (tipoDer, valDer) = parsearUnario()
            if (tipoIzq != tipoDer && tipoIzq != TipoDato.DESCONOCIDO && tipoDer != TipoDato.DESCONOCIDO)
                errores.add("ERROR SEMANTICO [linea ${op.linea}, columna ${op.columna}]: operacion '${op.valor}' entre tipos incompatibles $tipoIzq y $tipoDer")
            valIzq = evaluarBinaria(op.valor, valIzq, valDer)
        }
        return Pair(tipoIzq, valIzq)
    }

    // Mini-evaluador de operaciones aritmeticas en tiempo de "compilacion/ejecucion".
    private fun evaluarBinaria(op: String, izq: Any?, der: Any?): Any? {
        if (izq == null || der == null) return null
        return when {
            izq is Int && der is Int -> when (op) {
                "+" -> izq + der
                "-" -> izq - der
                "*" -> izq * der
                "/" -> if (der != 0) izq / der else null
                "%" -> if (der != 0) izq % der else null
                else -> null
            }
            izq is Double && der is Double -> when (op) {
                "+" -> izq + der
                "-" -> izq - der
                "*" -> izq * der
                "/" -> izq / der
                "%" -> izq % der
                else -> null
            }
            op == "+" && izq is String && der is String -> izq + der
            else -> null
        }
    }

    // Evaluador de comparaciones relacionales, para poder saber de verdad si
    // una condicion de ifjay/whilejay/forjay es verdadera o falsa.
    private fun evaluarRelacional(op: String, izq: Any?, der: Any?): Any? {
        if (izq == null || der == null) return null
        return when {
            izq is Int && der is Int -> when (op) {
                "==" -> izq == der; "!=" -> izq != der
                "<"  -> izq < der;  ">"  -> izq > der
                "<=" -> izq <= der; ">=" -> izq >= der
                else -> null
            }
            izq is Double && der is Double -> when (op) {
                "==" -> izq == der; "!=" -> izq != der
                "<"  -> izq < der;  ">"  -> izq > der
                "<=" -> izq <= der; ">=" -> izq >= der
                else -> null
            }
            izq is String && der is String -> when (op) {
                "==" -> izq == der; "!=" -> izq != der
                else -> null
            }
            izq is Boolean && der is Boolean -> when (op) {
                "==" -> izq == der; "!=" -> izq != der
                else -> null
            }
            else -> null
        }
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
            val (tipo, valor) = parsearUnario()
            if (tipo != TipoDato.BOOLEAN && tipo != TipoDato.DESCONOCIDO)
                errores.add("ERROR SEMANTICO [linea ${t.linea}, columna ${t.columna}]: 'notjay' requiere un operando booleano, se recibio $tipo")
            val nuevoValor = if (valor is Boolean) !valor else null
            return Pair(TipoDato.BOOLEAN, nuevoValor)
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