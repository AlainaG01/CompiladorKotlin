package lexico

data class Token(val tipo: TipoToken, val valor: String, val linea: Int, val columna: Int = 1) {
    override fun toString() = "[$tipo | \"$valor\" | línea $linea, columna $columna]"
}
