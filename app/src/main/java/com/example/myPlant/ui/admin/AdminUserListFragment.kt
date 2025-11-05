package com.example.myPlant.ui.admin

import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myPlant.R
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.example.myPlant.data.model.UserItem
import android.widget.SearchView

class AdminUserListFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: UserListAdapter
    private lateinit var searchView: SearchView
    private lateinit var spinnerRole: Spinner

    private val firestore = FirebaseFirestore.getInstance()
    private var allUsers: List<UserItem> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_admin_user_list, container, false)

        recyclerView = view.findViewById(R.id.recyclerViewUsers)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = UserListAdapter()
        recyclerView.adapter = adapter

        searchView = view.findViewById(R.id.searchView)
        spinnerRole = view.findViewById(R.id.spinnerRoleFilter)

        setupSpinner()
        setupSearch()
        fetchUsers()

        return view
    }

    private fun setupSpinner() {
        val roles = listOf("All", "admin", "public")
        val adapterSpinner = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, roles)
        adapterSpinner.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerRole.adapter = adapterSpinner

        spinnerRole.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>, view: View?, position: Int, id: Long
            ) {
                applyFilters()
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun setupSearch() {
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                applyFilters()
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                applyFilters()
                return true
            }
        })
    }

    private fun applyFilters() {
        val searchQuery = searchView.query.toString().trim().lowercase()
        val selectedRole = spinnerRole.selectedItem.toString()

        val filteredList = allUsers.filter { user ->
            val matchesSearch = user.email?.lowercase()?.contains(searchQuery) ?: false
            val matchesRole = selectedRole == "All" || user.role.equals(selectedRole, ignoreCase = true)
            matchesSearch && matchesRole
        }

        adapter.submitList(filteredList)
    }

    private fun fetchUsers() {
        firestore.collection("userProfiles")
            .orderBy("dateJoined", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snapshot ->
                allUsers = snapshot.documents.mapNotNull { it.toObject(UserItem::class.java) }
                adapter.submitList(allUsers)
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
