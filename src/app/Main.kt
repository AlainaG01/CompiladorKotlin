package app

fun main(args: Array<String>) {
    // Forzamos salida en UTF-8 explícitamente: algunos entornos (Windows,
    // terminales con locale POSIX/C, etc.) usan por defecto un charset distinto
    // y corrompen los acentos de los mensajes (p. ej. "línea" -> "l?nea").
    System.setOut(java.io.PrintStream(java.io.FileOutputStream(java.io.FileDescriptor.out), true, "UTF-8"))

    // Ejecuta directamente el archivo programa.jay
    compilarArchivo("programa.jay")
    return

    /*
    // Prueba 1: Correcta — asignación básica (igual al enunciado del ejercicio)
    compilar("ASIGNACION BASICA (CORRECTO)",
        """
        varjay intjay result := 0;
        varjay intjay value := 5;
        result := value + 5;
        printjay(result);
        """.trimIndent()
    )

    // Prueba 2: Correcta — función, bucle, constante
    compilar("FUNCION + BUCLE (CORRECTO)",
        """
        constjay intjay max := 10;
        varjay intjay counter := 0;
        funjay sum(a, b) {
            varjay intjay temp := 0;
        }
        whilejay (counter < max) {
            counter := counter + 1;
        }
        ifjay (counter == max) {
            printjay(counter);
        } elsejay {
            printjay(max);
        }
        """.trimIndent()
    )

    // Prueba 3: Con 4 errores semánticos intencionales
    compilar("CON ERRORES SEMANTICOS",
        """
        varjay intjay x := 42;
        varjay intjay x := 99;
        constjay floatjay pi := 3.14;
        pi := 3.0;
        varjay intjay sum := x + noExists;
        ifjay (x == truejay) {
            printjay(x);
        }
        """.trimIndent()
    )

    // Prueba 4: Correcta — estructuras nuevas: forjay, breakjay, unario '-', andjay/orjay/notjay
    compilar("ESTRUCTURAS EXTENDIDAS (CORRECTO)",
        """
        varjay intjay total := 0;
        forjay (varjay intjay i := 0; i < 5; i := i + 1) {
            total := total + i;
            ifjay (total > 100) {
                breakjay;
            }
        }
        varjay intjay negativo := -5;
        varjay floatjay opuesto := -3.5;
        ifjay (total >= 0 andjay negativo < 0) {
            printjay(total);
        } elsejay {
            printjay(negativo);
        }
        ifjay (notjay falsejay orjay total == 0) {
            printjay(opuesto);
        }
        """.trimIndent()
    )

    // Prueba 5: Con errores léxicos (columna) — caracter desconocido y cadena sin cerrar
    compilar("CON ERRORES LEXICOS (columna)",
        """
        varjay intjay a := 5 @ 2;
        varjay stringjay s := "texto sin cerrar;
        """.trimIndent()
    )
    */
}