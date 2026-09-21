package com.warrantyvault.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/**
 * Compatibility holder so the screen can obtain the shared [ScanWorkflowViewModel]
 * with the Application context via the standard viewModel() factory.
 */
class ScanWorkflowViewModelHolder(app: Application) : AndroidViewModel(app) {
    val delegate = com.warrantyvault.ocr.ScanWorkflowViewModel(app)

    val state get() = delegate.state

    fun onImageSelected(uri: android.net.Uri) = delegate.onImageSelected(uri)
    fun updateField(field: String, value: String) = delegate.updateField(field, value)
    fun updateDateField(field: String, millis: Long?) = delegate.updateDateField(field, millis)
    fun confirmDraft() = delegate.confirmDraft()
    fun cancelReview() = delegate.cancelReview()
    fun cancelMatchDialog() = delegate.cancelMatchDialog()
    fun useExistingProduct(id: Long) = delegate.useExistingProduct(id)
    fun updateExistingProduct(id: Long) = delegate.updateExistingProduct(id)
    fun createNewFromDraft() = delegate.createNewFromDraft()
    fun rescan() = delegate.rescan()
    fun dismissError() = delegate.dismissError()
    fun reset() = delegate.reset()
}
