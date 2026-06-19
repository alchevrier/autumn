package dev.autumn.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.ir.visitors.acceptVoid
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrAnonymousInitializer
import org.jetbrains.kotlin.ir.types.isString
import org.jetbrains.kotlin.ir.types.isPrimitiveType
import org.jetbrains.kotlin.ir.types.classFqName

class AllocationVisitor(
    private val pluginContext: IrPluginContext,
    private val messageCollector: MessageCollector
) : IrElementVisitorVoid {

    companion object {
        val LONG_LIVED_FQ_NAME = FqName("dev.autumn.annotations.LongLived")
    }

    // Keep track of the current enclosing function/class to check annotations.
    private val declarationStack = mutableListOf<IrElement>()

    override fun visitElement(element: IrElement) {
        element.acceptChildrenVoid(this)
    }

    override fun visitClass(declaration: IrClass) {
        declarationStack.add(declaration)
        super.visitClass(declaration)
        declarationStack.removeLast()
    }

    override fun visitFunction(declaration: IrFunction) {
        declarationStack.add(declaration)
        super.visitFunction(declaration)
        declarationStack.removeLast()
    }

    override fun visitProperty(declaration: IrProperty) {
        declarationStack.add(declaration)
        super.visitProperty(declaration)
        declarationStack.removeLast()
    }

    override fun visitField(declaration: IrField) {
        declarationStack.add(declaration)
        super.visitField(declaration)
        declarationStack.removeLast()
    }

    override fun visitAnonymousInitializer(declaration: IrAnonymousInitializer) {
        declarationStack.add(declaration)
        super.visitAnonymousInitializer(declaration)
        declarationStack.removeLast()
    }

    override fun visitConstructorCall(expression: IrConstructorCall) {
        super.visitConstructorCall(expression)

        // 1. Is this constructor call allowed because we're in a LongLived context?
        val isAllowed = declarationStack.any { element ->
            when (element) {
                is IrClass -> element.hasAnnotation(LONG_LIVED_FQ_NAME)
                is IrFunction -> element.hasAnnotation(LONG_LIVED_FQ_NAME)
                is IrProperty -> element.hasAnnotation(LONG_LIVED_FQ_NAME)
                is IrField -> element.hasAnnotation(LONG_LIVED_FQ_NAME)
                is IrAnonymousInitializer -> element.hasAnnotation(LONG_LIVED_FQ_NAME)
                else -> false
            }
        }

        if (!isAllowed) {
            val type = expression.type
            
            // Allow basic types (String, primitives) and Exceptions as they are typically 
            // optimized away or required on slow paths (e.g. throwing error).
            // Note: We deliberately DO NOT allow kotlin.collections here to enforce custom pool usage.
            val fqName = type.classFqName?.asString() ?: ""
            val isSafeType = type.isPrimitiveType() || fqName.endsWith("Array") || 
                             type.isString() ||
                             AllocationExclusions.isSafeType(fqName)

            if (!isSafeType) {
                messageCollector.report(
                    CompilerMessageSeverity.ERROR,
                    "Heap allocation detected in strict zero-allocation scope: ${fqName}. Annotate with @LongLived if this is a safe long-lived allocation."
                )
            }
        }
    }
}
