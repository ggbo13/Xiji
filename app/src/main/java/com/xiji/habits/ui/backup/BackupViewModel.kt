package com.xiji.habits.ui.backup

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xiji.habits.backup.BackupFile
import com.xiji.habits.backup.BackupManager
import com.xiji.habits.backup.ImportPreview
import com.xiji.habits.backup.ImportResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BackupUiState(
    val busy: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
    val pendingFile: BackupFile? = null,
    val preview: ImportPreview? = null,
    val replaceMode: Boolean = false
)

class BackupViewModel(private val manager: BackupManager) : ViewModel() {

    private val _state = MutableStateFlow(BackupUiState())
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    fun exportJson(uri: Uri) {
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val r = manager.exportJson(uri)
            _state.update {
                it.copy(
                    busy = false,
                    message = r.fold(
                        { n -> "已导出 $n 条记录到所选位置" },
                        { e -> "导出失败：${e.message}" }
                    ),
                    isError = r.isFailure
                )
            }
        }
    }

    fun exportCsv(uri: Uri) {
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val r = manager.exportCsv(uri)
            _state.update {
                it.copy(
                    busy = false,
                    message = r.fold({ n -> "已导出 $n 条记录（CSV）" }, { e -> "导出失败：${e.message}" }),
                    isError = r.isFailure
                )
            }
        }
    }

    fun pickImport(uri: Uri) {
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val parsed = manager.parse(uri)
            parsed.fold(
                onSuccess = { file ->
                    _state.update {
                        it.copy(
                            busy = false,
                            pendingFile = file,
                            preview = manager.preview(file),
                            message = null
                        )
                    }
                },
                onFailure = { e ->
                    _state.update {
                        it.copy(busy = false, message = "无法读取备份：${e.message}", isError = true)
                    }
                }
            )
        }
    }

    fun setReplaceMode(replace: Boolean) = _state.update { it.copy(replaceMode = replace) }

    fun confirmImport(onDone: () -> Unit) {
        val file = _state.value.pendingFile ?: return
        val replace = _state.value.replaceMode
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            when (val r = manager.import(file, replace)) {
                is ImportResult.Success -> {
                    _state.update {
                        it.copy(
                            busy = false, pendingFile = null, preview = null,
                            message = "已导入 ${r.habits} 个习惯、${r.records} 条记录",
                            isError = false
                        )
                    }
                    onDone()
                }
                is ImportResult.Failure -> _state.update {
                    it.copy(busy = false, message = r.reason, isError = true)
                }
            }
        }
    }

    fun cancelImport() = _state.update { it.copy(pendingFile = null, preview = null) }

    fun consumeMessage() = _state.update { it.copy(message = null) }
}
