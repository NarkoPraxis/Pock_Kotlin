package com.example.puck

import android.content.Context
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import utility.Drawing
import utility.Logic
import utility.PaintBucket
import utility.Sounds

open class GameView(context: Context, open var activity: AppCompatActivity) : View(context) {

    var initialized = false

    open fun doOnSizeChange(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
//        Logic.initializeSettings(width, height)
//        Logic.initialize(activity, this)
//        Sounds.initializeGame()
//        PaintBucket.initialize(resources)
//        Drawing.initialize()
////        startPlayers()
    }
}