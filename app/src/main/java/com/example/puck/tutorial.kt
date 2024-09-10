package com.example.puck

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import enums.GameState
import gameobjects.Settings
import kotlinx.android.synthetic.main.activity_tutorial.*

class tutorial : AppCompatActivity() {
    lateinit var tutorialView: TutorialView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//        setContentView(R.layout.activity_tutorial)

        tutorialView = TutorialView(this, this)
        tutorialView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN
        tutorialView.contentDescription = getString(R.string.gameViewDescription)
        setContentView(tutorialView)
    }

    override fun onBackPressed() {
        if (Settings.pauseGame) {
            super.onBackPressed()
        } else {
            //donothing
        }
    }
}