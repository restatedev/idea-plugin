package com.example.restate.wizard

import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.openapi.util.Key

interface RestateWizardData {
  enum class Language {
    Java,
    Kotlin
  }

  val language: Language

  companion object {
    public val KEY: Key<RestateWizardData> = Key.create(RestateWizardData::class.java.name)
    @JvmStatic
    val NewProjectWizardStep.restateWizardData: RestateWizardData?
      get() = data.getUserData(KEY)
  }
}