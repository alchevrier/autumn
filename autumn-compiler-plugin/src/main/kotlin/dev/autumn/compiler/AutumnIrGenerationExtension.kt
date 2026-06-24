package dev.autumn.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment

class AutumnIrGenerationExtension(
    private val messageCollector: MessageCollector
) : IrGenerationExtension {

    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        // 1. Process literal injections for circuit breaker budgets
        val injectionTransformer = BudgetInjectionTransformer(pluginContext, messageCollector)
        moduleFragment.transform(injectionTransformer, null)

        // 2. Validate strict zero-allocation boundaries
        val visitor = AllocationVisitor(pluginContext, messageCollector)
        moduleFragment.accept(visitor, null)

        val cacheVisitor = ThreadCacheBudgetVisitor(pluginContext, messageCollector)
        moduleFragment.accept(cacheVisitor, null)

        // 3. Lower @Pipelined data structures into Structure of Arrays (SoA) layouts using Global Pooling
        val soaTransformer = PipelinedSoATransformer(pluginContext, messageCollector)
        soaTransformer.buildMemoryMap(moduleFragment)
        moduleFragment.transform(soaTransformer, null)
        
        // 4. Inject Memory Bank hardware boundary bootstrap automatically into Main
        val totalBytesToAllocate = soaTransformer.totalAllocatedBytes[0]
        if (totalBytesToAllocate > 0) {
            val initializationTransformer = MemoryBankInitializationTransformer(pluginContext, messageCollector, totalBytesToAllocate)
            moduleFragment.transform(initializationTransformer, null)
        }
    }
}
