#!/bin/bash
cat << 'KOTLIN' > autumn-compiler-plugin/src/main/kotlin/dev/autumn/compiler/fir/TestIr.kt
package dev.autumn.compiler.fir
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.builders.irInt
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
fun testBuilder(context: IrPluginContext, symbol: IrSymbol): IrExpression {
    val builder = DeclarationIrBuilder(context, symbol)
    return builder.irInt(42)
}
KOTLIN
./gradlew :autumn-compiler-plugin:compileKotlin
