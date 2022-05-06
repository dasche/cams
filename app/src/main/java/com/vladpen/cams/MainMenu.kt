package com.vladpen.cams

import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.PopupMenu
import com.vladpen.Navigator

class MainMenu(val context: Context) {
    fun showPopupMenu(view: View) {
        val popup = PopupMenu(context, view)
        popup.menuInflater.inflate(R.menu.main_menu, popup.menu)

        popup.setOnMenuItemClickListener { item ->
            when (item!!.itemId) {
                R.id.iStreamAdd -> {
                    editScreen()
                }
                R.id.iGroupAdd -> {
                    editGroupScreen()
                }
                // R.id.iAbout -> {
                //     aboutScreen()
                // }
            }
            true
        }
        popup.show()
    }

    private fun editScreen() {
        val intent = Intent(context, EditActivity::class.java)
        Navigator.go(context, intent)
    }

    private fun editGroupScreen() {
        val intent = Intent(context, EditGroupActivity::class.java)
        Navigator.go(context, intent)
    }
}