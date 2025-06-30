package dev.restate.idea.settings

import com.intellij.openapi.options.Configurable
import javax.swing.JComponent

/**
 * Provides controller functionality for application settings.
 */
class RestateSettingsConfigurable : Configurable {
  private var mySettingsComponent: RestateSettingsComponent? = null

  // A default constructor with no arguments is required by the IntelliJ Platform
  override fun getDisplayName(): String = "Restate"

  override fun getPreferredFocusedComponent(): JComponent = mySettingsComponent!!.preferredFocusedComponent

  override fun createComponent(): JComponent {
    mySettingsComponent = RestateSettingsComponent()
    return mySettingsComponent!!.panel
  }

  override fun isModified(): Boolean {
    val settings = RestateSettings.getInstance()
    return (mySettingsComponent!!.downloadRestateServer != settings.downloadRestateServer) or (mySettingsComponent!!.environmentVariablesString != settings.environmentVariablesString)
  }

  override fun apply() {
    val settings = RestateSettings.getInstance()
    settings.downloadRestateServer = mySettingsComponent!!.downloadRestateServer
    settings.environmentVariablesString = mySettingsComponent!!.environmentVariablesString
  }

  override fun reset() {
    val settings = RestateSettings.getInstance()
    mySettingsComponent!!.downloadRestateServer = settings.downloadRestateServer
    mySettingsComponent!!.environmentVariablesString = settings.environmentVariablesString
  }

  override fun disposeUIResources() {
    mySettingsComponent = null
  }
}
