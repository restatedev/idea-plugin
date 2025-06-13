package com.example.restate.project

import com.intellij.ide.util.projectWizard.WebTemplateNewProjectWizard
import com.intellij.ide.wizard.GeneratorNewProjectWizardBuilderAdapter

class RestateJavaGradleModuleProjectGenerator : GeneratorNewProjectWizardBuilderAdapter(
    WebTemplateNewProjectWizard(
        RestateJavaGradleProjectGenerator()
    )
)