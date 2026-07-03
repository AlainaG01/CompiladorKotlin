# CompiladorJayLang — Entrega 40%

## Arquitectura en capas

El proyecto pasó de un único archivo `.kt` a una estructura organizada por
capas, cada una en su propio paquete Kotlin, con dependencia en un solo
sentido (de arriba hacia abajo):

```
src/
├── app/          Capa de aplicación (orquestación + punto de entrada)
│   ├── Main.kt         → fun main(): corre las pruebas embebidas o
│   │                      recibe una ruta de archivo .jay por argumento
│   └── Compilador.kt   → fun compilar()/compilarArchivo(): coordina
│                          lexico -> sintactico y presenta resultados
│
├── sintactico/   Capa sintáctica (fase 2) + validaciones semánticas (fase 3)
│   └── AnalizadorSintactico.kt   → parser descendente recursivo,
│                                    depende de lexico, modelo y simbolos
│
├── simbolos/     Capa de tabla de símbolos (estado de la compilación)
│   └── TablaSimbolos.kt   → pila de ámbitos, depende solo de modelo
│
├── modelo/       Capa de modelo/dominio (entidades de datos puras)
│   ├── TipoDato.kt
│   └── Simbolo.kt
│
└── lexico/       Capa léxica (fase 1)
    ├── Token.kt
    ├── TipoToken.kt
    ├── Operadores.kt
    ├── PalabrasReservadas.kt
    └── AnalizadorLexico.kt
```

Dirección de las dependencias: `app → sintactico → simbolos → modelo` y
`app / sintactico → lexico`. Ninguna capa inferior conoce ni importa nada de
una capa superior (p. ej. `lexico` y `modelo` no saben que existe `app`).
Esto deja el proyecto listo para que la próxima entrega agregue una capa
`semantico/` (hoy embebida dentro de `sintactico`) y una capa `codegen/`
sin tener que reestructurar lo ya construido.

**Nota:** no se movió ninguna línea de lógica — cada archivo nuevo contiene
exactamente el mismo código que tenía el `.kt` monolítico, solo reubicado en
su capa correspondiente con los imports necesarios entre paquetes.

## Cómo ejecutar

Compilado con Kotlin (JVM). El jar ya incluye el runtime.

```
java -jar CompiladorJayLang.jar
```
Sin argumentos, corre las 5 pruebas embebidas en `app/Main.kt`.

```
java -jar CompiladorJayLang.jar ruta/a/archivo.jay
```
Con un argumento, lee y compila ese archivo fuente desde disco.

### Recompilar desde el código fuente (IntelliJ / terminal)

```bash
cd ProyectoFinal30
kotlinc $(find src -name "*.kt") -include-runtime -d CompiladorJayLang.jar
java -jar CompiladorJayLang.jar
```

---

## 1. Funcionalidades que ya estaban implementadas (antes de esta entrega)

- Diseño base del lenguaje "JayLang": palabras reservadas, tipos `intjay`,
  `floatjay`, `stringjay`, booleanos.
- Lexer con reconocimiento de: identificadores, palabras reservadas, enteros,
  decimales, cadenas, operadores `+ - * / < > % == != <= >=`, delimitadores
  `( ) { } ; ,`, asignación `:=`, comentarios de línea `//`, y omisión de
  espacios/tabs/saltos de línea.
- Los operadores aritméticos ya usaban la notación tradicional `+ - * /`
  (no había operadores "propios" que reemplazar).
- Tabla de símbolos con pila de ámbitos (`entrarAmbito`/`salirAmbito`),
  declarar/buscar/actualizar, e impresión en formato de tabla.
- Parser descendente recursivo con: `varjay`/`constjay`, asignación, `ifjay`/
  `elsejay`, `whilejay`, `funjay` (cabecera), `printjay`, bloques con ámbito
  propio, y una gramática de expresiones de dos niveles (término/factor) con
  paréntesis.
- Validaciones semánticas básicas: variable duplicada, variable no declarada,
  reasignación de constante, incompatibilidad de tipos en operaciones.
- Reporte de errores por línea (léxicos, sintácticos y semánticos), separados
  por fase.
- Representación interna de literales (binario 16 bits para enteros, IEEE-754
  32 bits para reales) — utilidad extra ya presente.

## 2. Funcionalidades agregadas/corregidas en esta entrega

- **Columna en todos los errores**: se agregó seguimiento de columna en el
  lexer (`Token.columna`) y se propagó a *todos* los mensajes de error
  léxicos, sintácticos y semánticos (antes solo reportaban línea).
- **Corrección de bug de codificación**: se forzó `stdout` a UTF-8 en `main()`
  para que los acentos (línea, análisis, etc.) no se corrompan en entornos
  con locale distinto (Windows, terminales sin UTF-8).
- **Comentarios de bloque** `/* ... */` además de los de línea `//`, con
  detección de comentario sin cerrar.
- **Cadenas de texto**: soporte de escapes básicos (`\"`, `\\`, `\n`, `\t`) y
  detección explícita de número decimal mal formado (`5.` sin dígitos).
- **Lectura real de archivos fuente**: `main(args)` ahora acepta una ruta de
  archivo `.jay` como argumento y lo compila (antes solo corría las pruebas
  embebidas como `String`). Se preservaron las pruebas embebidas como
  comportamiento por defecto.
- **Gramática de expresiones completada con precedencia real**, en vez de los
  dos niveles previos (que además dejaban `andjay`/`orjay`/`notjay` como
  palabras reservadas "huérfanas", nunca conectadas a ninguna regla):
  ```
  expresion  → or
  or         → and    ( "orjay"  and    )*
  and        → relac  ( "andjay" relac  )*
  relac      → suma   ( (==|!=|<|>|<=|>=) suma )*
  suma       → termino ( (+|-) termino )*
  termino    → unario ( (*|/|%) unario )*
  unario     → ("-" | "notjay") unario | primario
  primario   → NUMERO | CADENA | true/false | id | "(" expresion ")"
  ```
  Esto corrige además un bug real: expresiones con **negativos** (`-5`,
  `-a`) no compilaban en la versión anterior (el `-` no tenía regla unaria y
  producía "factor inesperado").
- **`forjay` (bucle for) y `breakjay`**: eran palabras reservadas declaradas
  pero sin ninguna regla de gramática — usarlas producía "declaración
  inesperada". Se agregó `parsearFor()` (con inicialización, condición e
  incremento, reutilizando `parsearVar`/asignación existentes) y
  `parsearBreak()`.
- Se agregaron 2 casos de prueba nuevos en `main()` para validar lo anterior:
  uno que ejercita `forjay`/`breakjay`/unario `-`/`andjay`/`orjay`/`notjay`
  sin errores, y otro que fuerza errores léxicos (carácter desconocido y
  cadena sin cerrar) para verificar el reporte con línea+columna.
- No se tocaron nombres de clases, estructura de archivo ni funcionalidades
  previas: `AnalizadorLexico`, `TablaSimbolos`, `AnalizadorSintactico`,
  `Token`, `Simbolo`, etc. conservan su rol e interfaz.

### Nota sobre el enunciado
El encargo original pedía completar el "análisis semántico completo" y la
"generación de código". Esto **no se implementó a propósito**: contradice el
alcance del 40% descrito al inicio del mismo encargo (léxico completo,
sintáctico *iniciado*, arquitectura lista para semántico/codegen en entregas
futuras). Se priorizó dejar el analizador sintáctico sólido y la arquitectura
modular lista para que la próxima entrega (60%) construya semántica completa
y generación de código sin fricción.

## 3. Pendiente para la siguiente entrega (60%)

- Análisis semántico completo: chequeo de tipos en asignaciones (hoy solo se
  valida en operaciones binarias, no al declarar/asignar), verificación de
  tipos de retorno (`returnjay`), y de argumentos en llamadas a función.
- Cuerpo completo de funciones: hoy `parsearFun()` reconoce parámetros pero
  los descarta (`while (...) avanzar()`); falta declarar parámetros en la
  tabla de símbolos y validar llamadas.
- Llamadas a función como expresión (`nombre(args)` dentro de `parsearPrimario`).
  Actualmente solo existe la *declaración* de funciones.
- `returnjay` con expresión de retorno y verificación de tipo contra la
  función.
- Verificar que `breakjay` solo aparezca dentro de un `whilejay`/`forjay`
  (hoy se acepta sintácticamente en cualquier bloque).
- AST explícito (hoy el parser valida y calcula tipos "sobre la marcha" sin
  construir árbol) — necesario como paso previo a la generación de código.
- Generación de código (intermedio o ensamblador/bytecode simple).
- Suite de pruebas automatizada (hoy las pruebas son programas de ejemplo en
  `main()`, impresas por consola, sin asserts).
