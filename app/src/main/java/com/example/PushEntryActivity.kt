package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity

class PushEntryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val type = intent.getStringExtra(PUSH_TYPE_EXTRA)?.trim().orEmpty()
        val exactEntity = type == PUSH_TYPE_NEW_INVOICE ||
            type == PUSH_TYPE_VERIFIED_SAVINGS_OPPORTUNITY

        if (exactEntity) {
            val targetIntent = Intent(this, PushTargetActivity::class.java).apply {
                intent.extras?.let(::putExtras)
            }
            startActivity(targetIntent)
        } else {
            startActivity(Intent(this, MainActivity::class.java))
        }
        finish()
    }
}
