package com.example.myPlant.ui.admin

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.example.myPlant.R
import com.example.myPlant.data.model.UserItem
import com.google.firebase.firestore.FirebaseFirestore

class UserListAdapter : RecyclerView.Adapter<UserListAdapter.ViewHolder>() {

    private var userList: List<UserItem> = emptyList()
    private val firestore = FirebaseFirestore.getInstance()

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
        holder.roleText.text = "Role: ${user.role}"

        // Set up spinner
        val roles = listOf("admin", "public")
        val adapterSpinner = ArrayAdapter(holder.itemView.context, android.R.layout.simple_spinner_item, roles)
        adapterSpinner.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        holder.roleSpinner.adapter = adapterSpinner

        // Set current selection
        val currentIndex = roles.indexOf(user.role)
        if (currentIndex != -1) holder.roleSpinner.setSelection(currentIndex)

        // Click card to toggle spinner visibility
        holder.itemView.setOnClickListener {
            holder.roleSpinner.visibility =
                if (holder.roleSpinner.visibility == View.GONE) View.VISIBLE else View.GONE
        }

        // When role changed
        holder.roleSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                val newRole = roles[pos]
                if (newRole != user.role) {
                    firestore.collection("userProfiles").document(user.uid)
                        .update("role", newRole)
                        .addOnSuccessListener {
                            user.role = newRole
                            holder.roleText.text = "Role: $newRole"
                            Toast.makeText(holder.itemView.context, "Role updated to $newRole", Toast.LENGTH_SHORT).show()
                        }
                        .addOnFailureListener {
                            Toast.makeText(holder.itemView.context, "Failed to update role", Toast.LENGTH_SHORT).show()
                        }
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    override fun getItemCount() = userList.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val emailText: TextView = view.findViewById(R.id.textUserEmail)
        val roleText: TextView = view.findViewById(R.id.textUserRole)
        val roleSpinner: Spinner = view.findViewById(R.id.spinnerUserRole)
    }
}
