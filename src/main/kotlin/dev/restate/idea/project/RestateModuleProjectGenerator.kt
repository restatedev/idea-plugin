package dev.restate.idea.project

import com.intellij.ide.util.projectWizard.WebTemplateNewProjectWizard
import com.intellij.ide.wizard.GeneratorNewProjectWizardBuilderAdapter

class RestateModuleProjectGenerator : GeneratorNewProjectWizardBuilderAdapter(
  WebTemplateNewProjectWizard(
    RestateProjectGenerator()
  )
)