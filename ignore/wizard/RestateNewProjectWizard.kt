package com.example.restate.wizard

import com.intellij.icons.AllIcons
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ide.wizard.GeneratorNewProjectWizard
import com.intellij.ide.wizard.NewProjectWizardChainStep.Companion.nextStep
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.ide.wizard.RootNewProjectWizardStep
import com.intellij.ide.wizard.comment.CommentNewProjectWizardStep
import com.intellij.ide.wizard.comment.LinkNewProjectWizardStep
import com.intellij.ui.UIBundle
import javax.swing.Icon

internal class RestateNewProjectWizard : GeneratorNewProjectWizard {

  override val id: String = "RestateNewProjectWizard"

  override val name: String = "Restate"

  override val icon: Icon = AllIcons.Plugins.PluginLogo

  override fun createStep(context: WizardContext) =
    RootNewProjectWizardStep(context)
      .nextStep(::NewRestateProjectWizardStep)

}