package com.mexadev.aura.ui.notifications

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.view.View
import android.view.animation.OvershootInterpolator
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.RecyclerView

class NotificationItemAnimator : DefaultItemAnimator() {

    override fun animateAdd(holder: RecyclerView.ViewHolder): Boolean {
        val view = holder.itemView
        view.alpha = 0f
        view.scaleX = 0f
        view.scaleY = 0f
        
        // Wait for move animations to finish before adding
        dispatchAddStarting(holder)
        
        // We delay the add animation by the move duration so it happens AFTER shifting
        val delay = moveDuration
        
        view.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(addDuration)
            .setStartDelay(delay)
            .setInterpolator(OvershootInterpolator(1.2f))
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    dispatchAddStarting(holder)
                }
                override fun onAnimationEnd(animation: Animator) {
                    animation.listeners.remove(this)
                    dispatchAddFinished(holder)
                }
                override fun onAnimationCancel(animation: Animator) {
                    view.alpha = 1f
                    view.scaleX = 1f
                    view.scaleY = 1f
                }
            })
            .start()
            
        return false // We handle the animation ourselves
    }
}
