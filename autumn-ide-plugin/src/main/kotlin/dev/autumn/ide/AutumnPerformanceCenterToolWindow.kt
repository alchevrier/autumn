package dev.autumn.ide

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import java.awt.BorderLayout
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.SwingConstants

class AutumnPerformanceCenterToolWindow : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = JBPanel<JBPanel<*>>(BorderLayout())
        panel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)

        val title = JBLabel("Autumn Architecture Topology & Circuit Budgets")
        title.font = title.font.deriveFont(Font.BOLD, 14f)
        panel.add(title, BorderLayout.NORTH)

        val contentPanel = JBPanel<JBPanel<*>>()
        contentPanel.layout = BoxLayout(contentPanel, BoxLayout.Y_AXIS)

        contentPanel.add(JBLabel("Target Platform: 🔳 Intel Skylake (Simulated 64-Byte L1)"))
        contentPanel.add(JBLabel("Execution Ports: ⚠️ Port 1 (ALU) Saturation Detected"))
        contentPanel.add(JBLabel(" "))
        contentPanel.add(JBLabel("Pipeline Topology:"))
        contentPanel.add(JBLabel("  [BoundaryChannel: Ingress UDP]"))
        contentPanel.add(JBLabel("    ↳ @Observe parseNet() ⚡ 37 Cycles | 📦 64B L1"))
        contentPanel.add(JBLabel("      ↳ [ColdChannel: Risk Command]"))
        contentPanel.add(JBLabel("         ↳ @Observe checkRisk() ⚡ 12 Cycles"))

        panel.add(JBScrollPane(contentPanel), BorderLayout.CENTER)

        val contentFactory = ContentFactory.getInstance()
        val content = contentFactory.createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
    }
}
