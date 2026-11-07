package de.frelina.vorrat.ui.update

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import de.frelina.vorrat.data.update.UpdateManager
import javax.inject.Inject

@HiltViewModel
class UpdateViewModel @Inject constructor(private val manager: UpdateManager) : ViewModel() {
    val state = manager.state

    fun checkAutomatically() = manager.checkAutomatically()
    fun checkNow() = manager.checkNow()
    fun showPrompt() = manager.showPrompt()
    fun dismiss() = manager.dismiss()
    fun install() = manager.install()
    fun permissionChecked() = manager.permissionChecked()
}
