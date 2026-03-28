package com.getupandgetlit.dingshihai.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.getupandgetlit.dingshihai.R
import com.getupandgetlit.dingshihai.data.entity.TaskEntity
import com.getupandgetlit.dingshihai.databinding.ItemTaskBinding
import com.getupandgetlit.dingshihai.util.displayName
import com.getupandgetlit.dingshihai.util.displayTime
import com.getupandgetlit.dingshihai.util.playPlanRes

class HomeTaskAdapter : ListAdapter<TaskEntity, HomeTaskAdapter.TaskViewHolder>(DiffCallback) {
    private var openItemId: Long? = null
    private var openSide: SwipeOpenSide? = null

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = getItem(position).id

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val binding = ItemTaskBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TaskViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(
            item = item,
            openSide = if (item.id == openItemId) openSide else null,
        )
    }

    fun openItem(position: Int, side: SwipeOpenSide) {
        val previousPosition = getOpenPosition()
        val item = currentList.getOrNull(position) ?: return
        openItemId = item.id
        openSide = side
        if (previousPosition != RecyclerView.NO_POSITION && previousPosition != position) {
            notifyItemChanged(previousPosition)
        }
        notifyItemChanged(position)
    }

    fun closeOpenItem() {
        val previousPosition = getOpenPosition()
        if (previousPosition == RecyclerView.NO_POSITION) {
            openItemId = null
            openSide = null
            return
        }
        openItemId = null
        openSide = null
        notifyItemChanged(previousPosition)
    }

    fun hasOpenItem(): Boolean = getOpenPosition() != RecyclerView.NO_POSITION

    fun getOpenPosition(): Int {
        val itemId = openItemId ?: return RecyclerView.NO_POSITION
        return currentList.indexOfFirst { it.id == itemId }
    }

    fun getOpenSide(): SwipeOpenSide? = openSide

    fun openTranslationForPosition(position: Int, actionWidthPx: Int): Float {
        val item = currentList.getOrNull(position) ?: return 0f
        if (item.id != openItemId) {
            return 0f
        }
        return when (openSide) {
            SwipeOpenSide.LEFT -> actionWidthPx.toFloat()
            SwipeOpenSide.RIGHT -> -actionWidthPx.toFloat()
            null -> 0f
        }
    }

    class TaskViewHolder(
        private val binding: ItemTaskBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        val foregroundView = binding.foregroundContainer

        fun bind(item: TaskEntity, openSide: SwipeOpenSide?) {
            binding.titleView.text = binding.root.context.getString(
                R.string.task_title_format,
                item.displayName(),
                item.displayTime(),
            )
            binding.statusView.text = binding.root.context.getString(
                R.string.execution_status_format,
                item.status,
            )
            binding.planView.setText(item.playPlanRes())
            binding.foregroundContainer.post {
                binding.foregroundContainer.translationX = when (openSide) {
                    SwipeOpenSide.LEFT -> binding.editActionView.width.toFloat()
                    SwipeOpenSide.RIGHT -> -binding.deleteActionView.width.toFloat()
                    null -> 0f
                }
            }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<TaskEntity>() {
        override fun areItemsTheSame(oldItem: TaskEntity, newItem: TaskEntity): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: TaskEntity, newItem: TaskEntity): Boolean {
            return oldItem == newItem
        }
    }
}
