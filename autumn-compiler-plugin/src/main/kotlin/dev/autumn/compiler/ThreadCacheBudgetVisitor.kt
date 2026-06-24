package dev.autumn.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.util.getAnnotation
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.primaryConstructor
import org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid
import org.jetbrains.kotlin.name.FqName

class ThreadCacheBudgetVisitor(
    private val pluginContext: IrPluginContext,
    private val messageCollector: MessageCollector
) : IrElementVisitorVoid {

    private val BUDGET_FQ_NAME = FqName("dev.autumn.annotations.ThreadCacheBudget")

    override fun visitFunction(declaration: IrFunction) {
        if (declaration.hasAnnotation(BUDGET_FQ_NAME)) {
            val budget = getBudgetCapacity(declaration)
            
            var totalBytes = 0
            
            // 1. Calculate the weight of all value parameters (function arguments)
            declaration.valueParameters.forEach { param ->
                totalBytes += getTypeSize(param.type)
            }
            
            // 2. Transverse the entire function body to sum all local stack allocations
            declaration.body?.accept(object : IrElementVisitorVoid {
                override fun visitVariable(declaration: IrVariable) {
                    totalBytes += getTypeSize(declaration.type)
                    super.visitVariable(declaration)
                }
                override fun visitElement(element: IrElement) {
                    element.acceptChildren(this, null)
                }
            }, null)
            
            if (totalBytes > budget) {
                messageCollector.report(
                    CompilerMessageSeverity.ERROR,
                    "[Autumn HLS] L1 Cache Bounds Exceeded: Function '${declaration.name}' allocates $totalBytes bytes on the stack, exceeding its strict budget of $budget bytes."
                )
            } else {
                messageCollector.report(
                    CompilerMessageSeverity.INFO,
                    "[Autumn HLS] Circuit Verified: Function '${declaration.name}' stack size $totalBytes bytes fits within strict L1 cache budget ($budget bytes)."
                )
            }
        }
        super.visitFunction(declaration)
    }

    override fun visitElement(element: IrElement) {
        element.acceptChildren(this, null)
    }

    private fun getBudgetCapacity(declaration: IrFunction): Int {
        val annotation = declaration.getAnnotation(BUDGET_FQ_NAME) ?: return 32768
        val arg = annotation.getValueArgument(0) as? IrConst
        return (arg?.value as? Int) ?: 32768
    }

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    private fun getTypeSize(type: IrType, visited: MutableSet<IrClass> = mutableSetOf()): Int {
        val name = type.classFqName?.shortName()?.asString() ?: "Object"
        when (name) {
            "Int", "Float" -> return 4
            "Long", "Double" -> return 8
            "Short", "Char" -> return 2
            "Byte", "Boolean" -> return 1
        }

        val irClass = type.classOrNull?.owner
        if (irClass != null) {
            // Protect against infinite recursion in linked lists or cyclic graphs
            if (!visited.add(irClass)) return 8

            // 1. Unroll Multiplatform Inline/Value Classes natively to their intrinsic data bounds
            if (irClass.isValue) {
                var intrinsicSize = 0
                irClass.primaryConstructor?.valueParameters?.forEach { param ->
                    intrinsicSize += getTypeSize(param.type, visited)
                }
                if (intrinsicSize > 0) return intrinsicSize
            }

            // 2. Struct layouts / @Pipelined data models tracking exact cache line width properties
            if (irClass.hasAnnotation(FqName("dev.autumn.annotations.Pipelined"))) {
                var structCacheSize = 0
                irClass.declarations.forEach { decl ->
                    if (decl is IrProperty) {
                        val fieldType = decl.backingField?.type ?: decl.getter?.returnType
                        if (fieldType != null) {
                            structCacheSize += getTypeSize(fieldType, visited)
                        }
                    }
                }
                messageCollector.report(
                    CompilerMessageSeverity.ERROR,
                    "[Debug] Pipelined Class ${irClass.name} intrinsic size: $structCacheSize"
                )
                if (structCacheSize > 0) return structCacheSize
            }
        }

        return 8 // Conservatively assume standard 64-bit reference pointer for external objects/arrays
    }
}
