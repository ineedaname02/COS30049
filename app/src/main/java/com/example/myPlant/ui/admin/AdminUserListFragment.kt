package com.example.myPlant.ui.admin

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.myPlant.R
import com.example.myPlant.data.model.UserItem
import com.example.myPlant.databinding.FragmentAdminUserListBinding
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class AdminUserListFragment : Fragment() {

    private var _binding: FragmentAdminUserListBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: UserListAdapter
    private val firestore = FirebaseFirestore.getInstance()
    private var allUsers: List<UserItem> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminUserListBinding.inflate(inflater, container, false)
        val view = binding.root



        setupRecyclerView()
        setupSpinner()
        setupSearch()
        fetchUsers()

        return view
    }

    private fun setupRecyclerView() {
        adapter = UserListAdapter()
        binding.recyclerViewUsers.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewUsers.adapter = adapter
    }

    private fun setupSpinner() {
        val roles = listOf("All", "admin", "public")
        val adapterSpinner = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, roles)
        (binding.spinnerRoleFilter as? AutoCompleteTextView)?.setAdapter(adapterSpinner)

        (binding.spinnerRoleFilter as? AutoCompleteTextView)?.setOnItemClickListener { parent, _, position, _ ->
            applyFilters()
        }
    }

    private fun setupSearch() {
        binding.searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                applyFilters()
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun applyFilters() {
        val searchQuery = binding.searchEditText.text.toString().trim().lowercase()
        val selectedRole = binding.spinnerRoleFilter.text.toString()

        val filteredList = allUsers.filter { user ->
            val matchesSearch = user.email?.lowercase()?.contains(searchQuery) ?: false
            val matchesRole = selectedRole == "All" || user.role.equals(selectedRole, ignoreCase = true) || selectedRole.isEmpty()
            matchesSearch && matchesRole
        }

        adapter.submitList(filteredList)
    }

    private fun fetchUsers() {
        firestore.collection("userProfiles")
            .orderBy("dateJoined", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snapshot ->
                allUsers = snapshot.documents.mapNotNull { document ->
                    try {
                        val user = document.toObject(UserItem::class.java)
                        user?.uid = document.id // Ensure UID is set
                        user
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), "Error parsing user: ${e.message}", Toast.LENGTH_SHORT).show()
                        null
                    }
                }
                adapter.submitList(allUsers)
                // Set default selection for the spinner
                if (binding.spinnerRoleFilter.text.isEmpty()) {
                    (binding.spinnerRoleFilter as? AutoCompleteTextView)?.setText("All", false)
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
