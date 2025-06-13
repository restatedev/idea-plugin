package com.example.restate.wizard

import com.intellij.ide.wizard.AbstractNewProjectWizardMultiStep
import com.intellij.ide.wizard.AbstractNewProjectWizardStep
import com.intellij.ide.wizard.NewProjectWizardMultiStepFactory
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.ide.projectWizard.generators.JavaNewProjectWizard
import com.intellij.ide.wizard.NewProjectWizardChainStep.Companion.nextStep
import org.jetbrains.kotlin.tools.projectWizard.KotlinNewProjectWizard
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.RightGap

/**
 * Main entry point for the Restate project wizard.
 * This class extends AbstractNewProjectWizardMultiStep to provide a language selection step
 * that allows users to choose between Java and Kotlin for their Restate project.
 */
class NewRestateProjectWizardStep(
  parent: NewProjectWizardStep,
) :
  AbstractNewProjectWizardMultiStep<NewRestateProjectWizardStep.ChooseLanguageRestateWizardProjectWizardStepInner, NewRestateProjectWizardStep.Factory>(
    parent,
    EP_NAME
  ) {

  companion object {
    val EP_NAME = ExtensionPointName<Factory>("restate.wizard.projectwiz")
  }

  override val self: ChooseLanguageRestateWizardProjectWizardStepInner = ChooseLanguageRestateWizardProjectWizardStepInner(parent)
  override val label: @NlsContexts.Label String = "Restate project"

  class ChooseLanguageRestateWizardProjectWizardStepInner(parent: NewProjectWizardStep) : AbstractNewProjectWizardStep(parent), RestateWizardData {
    private val languageProperty = propertyGraph.property(RestateWizardData.Language.Java)
    override var language: RestateWizardData.Language by languageProperty

    init {
      data.putUserData(RestateWizardData.KEY, this)
    }

    override fun setupUI(builder: Panel) {
      builder.row {
        label("Select language for your Restate project:")
      }
      builder.row {
        segmentedButton(RestateWizardData.Language.entries) { text = it.name }
          .gap(RightGap.SMALL)
          .bind(languageProperty)
      }
    }

    override fun setupProject(project: Project) {}
  }

  interface Factory : NewProjectWizardMultiStepFactory<ChooseLanguageRestateWizardProjectWizardStepInner>

  class JavaRestateProjectWizardStep(): Factory {
    override val name: @NlsContexts.Label String =  "Java Restate Project"
    override fun createStep(parent: ChooseLanguageRestateWizardProjectWizardStepInner)= JavaNewProjectWizard.Step(parent).nextStep(::RestateAssetStep)
  }

  class KotlinRestateProjectWizardStep(): Factory {
    override val name: @NlsContexts.Label String =  "Kotlin Restate Project"
    override fun createStep(parent: ChooseLanguageRestateWizardProjectWizardStepInner)= KotlinNewProjectWizard.Step(parent).nextStep(::RestateAssetStep)
  }
}
