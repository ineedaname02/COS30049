package com.example.myPlant.ui.admin

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.myPlant.R
import com.example.myPlant.data.model.UserItem

class UserListAdapter : RecyclerView.Adapter<UserListAdapter.ViewHolder>() {

    private var userList: List<UserItem> = emptyList()

    fun submitList(users: List<UserItem>) {
        userList = users
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_user_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val user = userList[position]
        holder.emailText.text = user.email
        holder.roleText.text = user.role
    }

    override fun getItemCount() = userList.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val emailText: TextView = view.findViewById(R.id.textUserEmail)
        val roleText: TextView = view.findViewById(R.id.textUserRole)
    }
}
