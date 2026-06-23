#!/bin/bash
cat << 'KOTLIN' > autumn-compiler-plugin/src/main/kotlin/dev/autumn/compiler/fir/TestExtract.kt
package dev.autumn.compiler.fir
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.expressions.FirLiteralExpression
import org.jetbrains.kotlin.fir.expressions.FirExpression
fun extract(ann: FirAnnotation) {
    val expr: FirExpression? = null
    val literal = expr as? FirLiteralExpression
}
KOTLIN
./gradlew :autumn-compiler-plugin:compileKotlin
