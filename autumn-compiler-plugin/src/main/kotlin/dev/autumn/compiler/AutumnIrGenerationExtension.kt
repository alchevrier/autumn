package dev.autumn.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment

class AutumnIrGenerationExtension(
    private val messageCollector: MessageCollector
) : IrGenerationExtension {

    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        messageCollector.report(org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity.WARNING, "=== IR GENERATION ENTRY: ${moduleFragment.name} ===")

        // 1. Process literal injections for circuit breaker budgets
        val injectionTransformer = BudgetInjectionTransformer(pluginContext, messageCollector)
        moduleFragment.transform(injectionTransformer, null)

        // 2. Validate strict zero-allocation boundaries
        val visitor = AllocationVisitor(pluginContext, messageCollector)
        moduleFragment.accept(visitor, null)

        val cacheVisitor = ThreadCacheBudgetVisitor(pluginContext, messageCollector)

        val cycleVisitor = CycleBudgetVisitor(pluginContext, messageCollector)
        moduleFragment.accept(cycleVisitor, null)
        moduleFragment.accept(cacheVisitor, null)

        // 3. Lower @Pipelined data structures into Structure of Arrays (SoA) layouts using Global Pooling
        val soaTransformer = PipelinedSoATransformer(pluginContext, messageCollector)
        soaTransformer.buildMemoryMap(moduleFragment)
        moduleFragment.transform(soaTransformer, null)
        
        // 4. Inject Memory Bank hardware boundary bootstrap automatically into Main
        val totalBytesToAllocate = soaTransformer.totalAllocatedBytes[0]
        if (totalBytesToAllocate > 0) {
            // Disabled in Benchmarks to allow manual MemoryBank testing sizing until bounds logic is finalized.
            // val initializationTransformer = MemoryBankInitializationTransformer(pluginContext, messageCollector, totalBytesToAllocate)
            // moduleFragment.transform(initializationTransformer, null)
        }


        // 5. Synthesize continuous Dataflow topologies (Network, Cold, Register Channels) into a unified Arbiter schedule.
        val topologyTransformer = TopologySynthesisTransformer(pluginContext, messageCollector)
        moduleFragment.transform(topologyTransformer, null)
        
        
        // 6. Export the final Topology and execution bounds to a local JSON artifact for the IDE Plugin to ingest natively!
        val firstFile = moduleFragment.files.firstOrNull()?.fileEntry?.name
        val safePath = if (firstFile != null && firstFile.contains("/src/")) {
            firstFile.substringBefore("/src/") + "/build/reports/autumn"
        } else {
            "build/reports/autumn"
        }
        TopologyExportSerializer.dumpToJson(safePath, messageCollector)


    }
}


