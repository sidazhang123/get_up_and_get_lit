package com.getupandgetlit.dingshihai.ui.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.getupandgetlit.dingshihai.DingShiHaiApp
import com.getupandgetlit.dingshihai.R
import com.getupandgetlit.dingshihai.databinding.ActivityHomeBinding
import com.getupandgetlit.dingshihai.service.SchedulerForegroundService
import com.getupandgetlit.dingshihai.ui.common.AppViewModelFactory
import com.getupandgetlit.dingshihai.ui.taskform.TaskFormActivity
import com.getupandgetlit.dingshihai.util.ServiceLauncher
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HomeActivity : AppCompatActivity() {
    private lateinit var binding: ActivityHomeBinding
    private val appContainer by lazy { (application as DingShiHaiApp).appContainer }
    private val viewModel: HomeViewModel by viewModels {
        AppViewModelFactory { HomeViewModel(appContainer.taskRepository) }
    }
    private val adapter = HomeTaskAdapter()
    private lateinit var swipeController: TaskSwipeController

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.any { granted -> !granted }) {
            Toast.makeText(this, getString(R.string.permission_storage_required), Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupRecyclerView()
        setupActions()
        requestStartupPermissions()
        observeUi()
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            val runtimeState = appContainer.taskRepository.getRuntimeState()
            if (runtimeState.batchActive) {
                ServiceLauncher.startServiceCompat(
                    this@HomeActivity,
                    SchedulerForegroundService.reviveIntent(this@HomeActivity),
                )
            }
        }
    }

    private fun setupRecyclerView() {
        binding.recyclerView.adapter = adapter
        swipeController = TaskSwipeController(
            context = this,
            recyclerView = binding.recyclerView,
            adapter = adapter,
            isSwipeEnabled = { binding.reserveButton.text != getString(R.string.cancel_reserve) },
        ) { position, action ->
            val task = adapter.currentList.getOrNull(position) ?: return@TaskSwipeController
            when (action) {
                SwipeAction.EDIT -> {
                    startActivity(TaskFormActivity.editIntent(this@HomeActivity, task.id))
                }

                SwipeAction.DELETE -> {
                    showDeleteDialog(task.id)
                }
            }
        }
        swipeController.attach()
    }

    private fun setupActions() {
        binding.createButton.setOnClickListener {
            startActivity(Intent(this, TaskFormActivity::class.java))
        }
        binding.reserveButton.setOnClickListener {
            if (!hasStoragePermission()) {
                requestStartupPermissions()
                Toast.makeText(this, getString(R.string.permission_storage_required), Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val intent = if (binding.reserveButton.text == getString(R.string.reserve_all)) {
                SchedulerForegroundService.reserveAllIntent(this)
            } else {
                SchedulerForegroundService.cancelReserveIntent(this)
            }
            ServiceLauncher.startServiceCompat(this, intent)
        }
        binding.swipeRefreshLayout.setOnRefreshListener {
            binding.swipeRefreshLayout.isRefreshing = false
        }
    }

    private fun observeUi() {
        lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                adapter.submitList(state.tasks)
                binding.reserveButton.text = getString(
                    if (state.batchActive) R.string.cancel_reserve else R.string.reserve_all
                )
                binding.createButton.isEnabled = !state.batchActive
                binding.emptyView.text = if (state.tasks.isEmpty()) getString(R.string.no_tasks) else ""
                if (state.batchActive) {
                    swipeController.closeOpenItem()
                }
            }
        }
    }

    private fun requestStartupPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions += Manifest.permission.POST_NOTIFICATIONS
        }
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun hasStoragePermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun showDeleteDialog(taskId: Long) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_task_title)
            .setMessage(R.string.delete_task_message)
            .setPositiveButton(R.string.confirm) { _, _ ->
                lifecycleScope.launch {
                    val task = appContainer.taskRepository.getTaskById(taskId)
                    appContainer.taskRepository.deleteTask(taskId)
                    appContainer.appLogger.log(
                        event = "task_deleted",
                        result = "ok",
                        task = task,
                    )
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
