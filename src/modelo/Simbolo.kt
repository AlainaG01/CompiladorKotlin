package modelo

data class Simbolo(
    val nombre: String,
    var tipo: TipoDato,
    var valor: Any? = null,
    val esConstante: Boolean = false,
    val linea: Int = 0
)
