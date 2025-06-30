package dev.restate.idea.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * Persistent settings for the Restate plugin.
 */
@State(
  name = "RestateSettings",
  storages = [Storage("restateSettings.xml")]
)
class RestateSettings : PersistentStateComponent<RestateSettings> {
  // Whether to download the restate-server or use the system one
  var downloadRestateServer: Boolean = true

  // Environment variables to pass to the restate-server (format: KEY1=VALUE1;KEY2=VALUE2)
  var environmentVariablesString: String =
    "RESTATE_ADMIN__experimental_feature_force_journal_retention=365days;RESTATE_WORKER__INVOKER__INACTIVITY_TIMEOUT=1day"

  // Helper function to get environment variables as a map
  fun getEnvironmentVariablesMap(): Map<String, String> {
    if (environmentVariablesString.isBlank()) return emptyMap()

    return environmentVariablesString.split(";")
      .filter { it.isNotBlank() }
      .mapNotNull { entry ->
        val parts = entry.split("=", limit = 2)
        if (parts.size == 2) {
          parts[0].trim() to parts[1].trim()
        } else {
          null
        }
      }.toMap()
  }

  override fun getState(): RestateSettings = this

  override fun loadState(state: RestateSettings) {
    XmlSerializerUtil.copyBean(state, this)
  }

  companion object {
    fun getInstance(): RestateSettings = ApplicationManager.getApplication().getService(RestateSettings::class.java)
  }
}
