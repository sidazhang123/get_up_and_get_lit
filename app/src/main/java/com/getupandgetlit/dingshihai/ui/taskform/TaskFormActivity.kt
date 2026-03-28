package com.getupandgetlit.dingshihai.ui.taskform

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import com.getupandgetlit.dingshihai.DingShiHaiApp
import com.getupandgetlit.dingshihai.R
import com.getupandgetlit.dingshihai.databinding.ActivityTaskFormBinding
import com.getupandgetlit.dingshihai.domain.model.PlayMode
import com.getupandgetlit.dingshihai.ui.common.AppViewModelFactory
import com.getupandgetlit.dingshihai.util.doAfterTextChangedCompat
import java.util.Locale
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class TaskFormActivity : AppCompatActivity() {
    private lateinit var binding: ActivityTaskFormBinding
    private val appContainer by lazy { (application as DingShiHaiApp).appContainer }
    private val viewModel: TaskFormViewModel by viewModels {
        AppViewModelFactory { TaskFormViewModel(appContainer.taskRepository, appContainer.appLogger) }
    }

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@registerForActivityResult
        handleFilePicked(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTaskFormBinding.inflate(layoutInflater)
        setContentView(binding.root)
        intent.getLongExtra(EXTRA_TASK_ID, -1L)
            .takeIf { it != -1L }
            ?.let(viewModel::loadTask)
        setupActions()
        observeUi()
    }

    private fun setupActions() {
        binding.cancelButton.setOnClickListener { finish() }
        binding.saveButton.setOnClickListener {
            lifecycleScope.launch {
                when (val result = viewModel.save()) {
                    TaskSaveResult.Success -> {
                        Toast.makeText(this@TaskFormActivity, getString(R.string.save_success), Toast.LENGTH_SHORT).show()
                        finish()
                    }

                    is TaskSaveResult.Error -> {
                        Toast.makeText(this@TaskFormActivity, getString(result.messageRes), Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
        binding.filePickerButton.setOnClickListener {
            filePickerLauncher.launch(arrayOf("audio/mpeg", "audio/wav", "audio/x-wav"))
        }
        binding.timeField.setOnClickListener { showTimePicker() }
        binding.singleMode.setOnCheckedChangeListener { _, checked ->
            if (checked) viewModel.updatePlayMode(PlayMode.SINGLE)
        }
        binding.intervalMode.setOnCheckedChangeListener { _, checked ->
            if (checked) viewModel.updatePlayMode(PlayMode.INTERVAL)
        }
        binding.nameInput.doAfterTextChangedCompat(viewModel::updateName)
        binding.maxPlaybackMinutesInput.doAfterTextChangedCompat(viewModel::updateMaxPlaybackMinutes)
        binding.loopCountInput.doAfterTextChangedCompat(viewModel::updateLoopCount)
        binding.intervalMinInput.doAfterTextChangedCompat(viewModel::updateIntervalMin)
        binding.intervalMaxInput.doAfterTextChangedCompat(viewModel::updateIntervalMax)
    }

    private fun observeUi() {
        lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                binding.titleView.setText(
                    if (state.isEditMode) R.string.task_edit_title else R.string.task_create_title
                )
                syncTextIfNeeded(binding.nameInput.text?.toString().orEmpty(), state.draft.name) {
                    binding.nameInput.setText(state.draft.name)
                    binding.nameInput.setSelection(binding.nameInput.text?.length ?: 0)
                }
                val timeText = if (state.draft.startHour != null && state.draft.startMinute != null) {
                    String.format(Locale.US, "%02d:%02d", state.draft.startHour, state.draft.startMinute)
                } else {
                    ""
                }
                syncTextIfNeeded(binding.timeField.text?.toString().orEmpty(), timeText) {
                    binding.timeField.setText(timeText)
                }
                val maxPlaybackText = state.draft.maxPlaybackMinutes
                    ?.toString()
                    .orEmpty()
                syncTextIfNeeded(
                    binding.maxPlaybackMinutesInput.text?.toString().orEmpty(),
                    maxPlaybackText,
                ) {
                    binding.maxPlaybackMinutesInput.setText(maxPlaybackText)
                }
                binding.fileNameView.text = state.draft.fileName
                binding.singleMode.isChecked = state.draft.playMode == PlayMode.SINGLE
                binding.intervalMode.isChecked = state.draft.playMode == PlayMode.INTERVAL
                binding.intervalContainer.visibility =
                    if (state.draft.playMode == PlayMode.INTERVAL) View.VISIBLE else View.GONE
                syncTextIfNeeded(binding.loopCountInput.text?.toString().orEmpty(), state.draft.loopCount?.toString().orEmpty()) {
                    binding.loopCountInput.setText(state.draft.loopCount?.toString().orEmpty())
                }
                syncTextIfNeeded(binding.intervalMinInput.text?.toString().orEmpty(), state.draft.intervalMinSec?.toString().orEmpty()) {
                    binding.intervalMinInput.setText(state.draft.intervalMinSec?.toString().orEmpty())
                }
                syncTextIfNeeded(binding.intervalMaxInput.text?.toString().orEmpty(), state.draft.intervalMaxSec?.toString().orEmpty()) {
                    binding.intervalMaxInput.setText(state.draft.intervalMaxSec?.toString().orEmpty())
                }
            }
        }
    }

    private fun showTimePicker() {
        val current = viewModel.uiState.value.draft
        android.app.TimePickerDialog(
            this,
            { _, hourOfDay, minute ->
                viewModel.updateTime(hourOfDay, minute)
            },
            current.startHour ?: 0,
            current.startMinute ?: 0,
            true,
        ).show()
    }

    private fun handleFilePicked(uri: Uri) {
        val documentFile = DocumentFile.fromSingleUri(this, uri)
        val name = documentFile?.name.orEmpty()
        val lowerName = name.lowercase(Locale.US)
        if (!lowerName.endsWith(".mp3") && !lowerName.endsWith(".wav")) {
            Toast.makeText(this, getString(R.string.invalid_file_type), Toast.LENGTH_LONG).show()
            return
        }
        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        viewModel.updateFile(uri.toString(), name)
    }

    private inline fun syncTextIfNeeded(current: String, target: String, apply: () -> Unit) {
        if (current != target) {
            apply()
        }
    }

    companion object {
        private const val EXTRA_TASK_ID = "extra_task_id"

        fun editIntent(context: Context, taskId: Long): Intent {
            return Intent(context, TaskFormActivity::class.java).apply {
                putExtra(EXTRA_TASK_ID, taskId)
            }
        }
    }
}
