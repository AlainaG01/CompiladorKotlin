package simbolos

import modelo.Simbolo

// ──────────────────────────────────────────────────────────────
// CAPA DE TABLA DE SÍMBOLOS
// ──────────────────────────────────────────────────────────────
class TablaSimbolos {
    private val pilaAmbitos: java.util.ArrayDeque<MutableMap<String, Simbolo>> = java.util.ArrayDeque()

    fun entrarAmbito() = pilaAmbitos.push(mutableMapOf<String, Simbolo>())
    fun salirAmbito()  = pilaAmbitos.poll()

    fun declarar(s: Simbolo): Boolean {
        val ambito = pilaAmbitos.peek() ?: return false
        if (ambito.containsKey(s.nombre)) return false
        ambito[s.nombre] = s
        return true
    }

    fun buscar(nombre: String): Simbolo? {
        for (ambito in pilaAmbitos.toList()) {
            if (ambito.containsKey(nombre)) return ambito[nombre]
        }
        return null
    }

    fun actualizar(nombre: String, valor: Any?) {
        for (ambito in pilaAmbitos.toList()) {
            if (ambito.containsKey(nombre)) { ambito[nombre]!!.valor = valor; return }
        }
    }

    fun imprimir() {
        println("\n+----------------+----------+-----------+-------------+")
        println("|  TABLA DE SIMBOLOS                                   |")
        println("+----------------+----------+-----------+-------------+")
        println("| Nombre         | Tipo     | Constante | Valor       |")
        println("+----------------+----------+-----------+-------------+")
        var haySimbolos = false
        for (ambito in pilaAmbitos.toList()) {
            for ((_, s) in ambito) {
                haySimbolos = true
                println("| ${s.nombre.padEnd(14)} | ${s.tipo.toString().padEnd(8)} | ${if (s.esConstante) "Si ".padEnd(9) else "No ".padEnd(9)} | ${s.valor.toString().padEnd(11)} |")
            }
        }
        if (!haySimbolos) println("|  (tabla vacia)                                       |")
        println("+----------------+----------+-----------+-------------+")
    }
}
