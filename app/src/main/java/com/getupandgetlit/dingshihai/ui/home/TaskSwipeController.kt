package com.getupandgetlit.dingshihai.ui.home

import android.content.Context
import android.graphics.Canvas
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs

class TaskSwipeController(
    context: Context,
    private val recyclerView: RecyclerView,
    private val adapter: HomeTaskAdapter,
    private val isSwipeEnabled: () -> Boolean,
    private val onActionClicked: (position: Int, action: SwipeAction) -> Unit,
) {
    private val actionWidthPx = context.resources.getDimensionPixelSize(
        com.getupandgetlit.dingshihai.R.dimen.swipe_action_width
    )
    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(event: MotionEvent): Boolean {
                return handleSingleTap(event)
            }
        }
    )

    private val touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
        0,
        ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT,
    ) {
        override fun getMovementFlags(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
        ): Int {
            return if (isSwipeEnabled()) {
                makeMovementFlags(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT)
            } else {
                0
            }
        }

        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder,
        ): Boolean = false

        override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder): Float = 0.45f

        override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
            super.onSelectedChanged(viewHolder, actionState)
            val selectedPosition = viewHolder?.bindingAdapterPosition ?: RecyclerView.NO_POSITION
            val openPosition = adapter.getOpenPosition()
            if (
                actionState == ItemTouchHelper.ACTION_STATE_SWIPE &&
                openPosition != RecyclerView.NO_POSITION &&
                openPosition != selectedPosition
            ) {
                adapter.closeOpenItem()
            }
        }

        override fun onChildDraw(
            c: Canvas,
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            dX: Float,
            dY: Float,
            actionState: Int,
            isCurrentlyActive: Boolean,
        ) {
            val holder = viewHolder as? HomeTaskAdapter.TaskViewHolder ?: return
            val clampedDx = dX.coerceIn(-actionWidthPx.toFloat(), actionWidthPx.toFloat())
            getDefaultUIUtil().onDraw(
                c,
                recyclerView,
                holder.foregroundView,
                clampedDx,
                dY,
                actionState,
                isCurrentlyActive,
            )
        }

        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            val holder = viewHolder as? HomeTaskAdapter.TaskViewHolder ?: return
            getDefaultUIUtil().clearView(holder.foregroundView)
            val position = holder.bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION && position == adapter.getOpenPosition()) {
                holder.foregroundView.translationX = adapter.openTranslationForPosition(position, actionWidthPx)
            }
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            if (!isSwipeEnabled()) {
                adapter.closeOpenItem()
                return
            }
            val position = viewHolder.bindingAdapterPosition
            if (position == RecyclerView.NO_POSITION) {
                adapter.closeOpenItem()
                return
            }
            val side = if (direction == ItemTouchHelper.RIGHT) {
                SwipeOpenSide.LEFT
            } else {
                SwipeOpenSide.RIGHT
            }
            adapter.openItem(position, side)
        }
    })

    private val scrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
            if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                adapter.closeOpenItem()
            }
        }
    }

    private val touchListener = object : RecyclerView.SimpleOnItemTouchListener() {
        override fun onInterceptTouchEvent(recyclerView: RecyclerView, event: MotionEvent): Boolean {
            if (!adapter.hasOpenItem()) {
                return false
            }
            return gestureDetector.onTouchEvent(event)
        }
    }

    fun attach() {
        touchHelper.attachToRecyclerView(recyclerView)
        recyclerView.addOnScrollListener(scrollListener)
        recyclerView.addOnItemTouchListener(touchListener)
    }

    fun closeOpenItem() {
        adapter.closeOpenItem()
    }

    private fun handleSingleTap(event: MotionEvent): Boolean {
        val openPosition = adapter.getOpenPosition()
        if (openPosition == RecyclerView.NO_POSITION) {
            return false
        }
        val child = recyclerView.findChildViewUnder(event.x, event.y)
        if (child == null) {
            adapter.closeOpenItem()
            return true
        }
        val tappedPosition = recyclerView.getChildAdapterPosition(child)
        if (tappedPosition != openPosition) {
            adapter.closeOpenItem()
            return true
        }
        val side = adapter.getOpenSide() ?: return false
        val childLeft = child.left.toFloat()
        val childRight = child.right.toFloat()
        val tappedAction = when {
            side == SwipeOpenSide.LEFT &&
                event.x <= childLeft + actionWidthPx -> SwipeAction.EDIT

            side == SwipeOpenSide.RIGHT &&
                event.x >= childRight - actionWidthPx -> SwipeAction.DELETE

            else -> null
        }
        if (tappedAction != null) {
            val position = tappedPosition
            adapter.closeOpenItem()
            onActionClicked(position, tappedAction)
            return true
        }
        adapter.closeOpenItem()
        return true
    }
}
