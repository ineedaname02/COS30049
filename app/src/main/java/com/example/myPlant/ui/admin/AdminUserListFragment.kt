package com.example.myPlant.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myPlant.R
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import android.widget.Toast
import com.example.myPlant.data.model.UserItem

class AdminUserListFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: UserListAdapter
    private val firestore = FirebaseFirestore.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_admin_user_list, container, false)

        recyclerView = view.findViewById(R.id.recyclerViewUsers)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = UserListAdapter()
        recyclerView.adapter = adapter

        fetchUsers()

        return view
    }

    private fun fetchUsers() {
        firestore.collection("userProfiles")
            .orderBy("dateJoined", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snapshot ->
                val users = snapshot.documents.mapNotNull { it.toObject(UserItem::class.java) }
                adapter.submitList(users)
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
