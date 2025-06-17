package com.example.restate.settings

import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Supports creating and managing a JPanel for the Settings Dialog.
 */
class RestateSettingsComponent {
  private val downloadRestateServerCheckBox =
    JBCheckBox("Download Restate server (if unchecked, use system installed version)")
  private val environmentVariablesField = JBTextField()

  private val mainPanel: JPanel = FormBuilder.createFormBuilder()
    .addComponent(downloadRestateServerCheckBox, 1)
    .addSeparator()
    .addLabeledComponent(
      JBLabel("Environment Variables (format: KEY1=VALUE1;KEY2=VALUE2):"),
      environmentVariablesField,
      1,
      false
    )
    .addComponentFillVertically(JPanel(), 0)
    .panel

  val panel: JPanel
    get() = mainPanel

  val preferredFocusedComponent: JComponent
    get() = environmentVariablesField

  var downloadRestateServer: Boolean
    get() = downloadRestateServerCheckBox.isSelected
    set(value) {
      downloadRestateServerCheckBox.isSelected = value
    }

  var environmentVariablesString: String
    get() = environmentVariablesField.text
    set(value) {
      environmentVariablesField.text = value
    }
}
