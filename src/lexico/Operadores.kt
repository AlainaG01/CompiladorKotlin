package lexico

// Operadores matemáticos tradicionales soportados por el lenguaje.
// El diseño del lenguaje utiliza exclusivamente estos símbolos convencionales
// (suma, resta, multiplicación, división); no existen operadores propios
// ni palabras-operador alternativas para la aritmética.
val OPERADORES_ARITMETICOS = setOf("+", "-", "*", "/")
val OPERADORES_RELACIONALES = setOf("==", "!=", "<", ">", "<=", ">=")
