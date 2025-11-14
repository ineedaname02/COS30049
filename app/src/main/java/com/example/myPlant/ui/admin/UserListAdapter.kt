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
import java.text.SimpleDateFormat
import java.util.*

class UserListAdapter : RecyclerView.Adapter<UserListAdapter.ViewHolder>() {

    private var userList: List<UserItem> = emptyList()
    private val firestore = FirebaseFirestore.getInstance()
    private val dateFormat = SimpleDateFormat("dd MMM yyyy 'at' HH:mm", Locale.getDefault())

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

        // Basic info
        holder.emailText.text = user.email ?: "No email"
        holder.roleText.text = "Role: ${user.role}"

        // User statistics - with null safety
        holder.observationsText.text = "Observations: ${user.contributionStats?.observations ?: 0}"
        holder.verifiedIdsText.text = "Verified IDs: ${user.contributionStats?.verifiedIdentifications ?: 0}"
        holder.flagsSubmittedText.text = "Flags: ${user.contributionStats?.flagsSubmitted ?: 0}"
        holder.totalPointsText.text = "Points: ${user.contributionStats?.totalPoints ?: 0}"

        // Account information
        holder.dateJoinedText.text = "Joined: ${formatTimestamp(user.dateJoined)}"
        holder.lastLoginText.text = "Last Login: ${formatTimestamp(user.lastLogin)}"
        holder.lastUpdateText.text = "Last Update: ${formatTimestamp(user.lastProfileUpdate)}"
        holder.locationText.text = "Location: ${user.location ?: "Not set"}"

        // Privacy preferences - with null safety
        // Alternative: Use Map-based access for preferences
        val preferencesMap = user.preferences as? Map<*, *>
        val showContributions = preferencesMap?.get("showContributions") as? Boolean ?: true
        val showEmail = preferencesMap?.get("showEmail") as? Boolean ?: false
        val showLocation = preferencesMap?.get("showLocation") as? String ?: "approximate"

        holder.showContributionsText.text = "Show Contributions: ${if (showContributions) "Yes" else "No"}"
        holder.showEmailText.text = "Show Email: ${if (showEmail) "Yes" else "No"}"
        holder.showLocationText.text = "Location Visibility: ${formatLocationVisibility(showLocation)}"

        // Set up role spinner
        val roles = listOf("admin", "public")
        val adapterSpinner = ArrayAdapter(holder.itemView.context, android.R.layout.simple_spinner_item, roles)
        adapterSpinner.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        holder.roleSpinner.adapter = adapterSpinner

        // Set current selection
        val currentIndex = roles.indexOf(user.role)
        if (currentIndex != -1) holder.roleSpinner.setSelection(currentIndex)

        // Initially hide stats and spinner
        holder.statsLayout.visibility = View.GONE
        holder.roleSpinner.visibility = View.GONE

        // Click card to toggle stats visibility and spinner
        holder.itemView.setOnClickListener {
            val shouldShowStats = holder.statsLayout.visibility == View.GONE
            holder.statsLayout.visibility = if (shouldShowStats) View.VISIBLE else View.GONE
            holder.roleSpinner.visibility = if (shouldShowStats) View.VISIBLE else View.GONE
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

    private fun formatTimestamp(timestamp: com.google.firebase.Timestamp?): String {
        return if (timestamp != null) {
            dateFormat.format(timestamp.toDate())
        } else {
            "-"
        }
    }

    private fun formatLocationVisibility(visibility: String): String {
        return when (visibility.lowercase()) {
            "hidden" -> "Hidden"
            "approximate" -> "Approximate"
            "exact" -> "Exact"
            else -> visibility.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
            }
        }
    }

    override fun getItemCount() = userList.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        // Basic info
        val emailText: TextView = view.findViewById(R.id.textUserEmail)
        val roleText: TextView = view.findViewById(R.id.textUserRole)
        val roleSpinner: Spinner = view.findViewById(R.id.spinnerUserRole)

        // Statistics layout
        val statsLayout: View = view.findViewById(R.id.layoutUserStats)

        // Statistics
        val observationsText: TextView = view.findViewById(R.id.textObservations)
        val verifiedIdsText: TextView = view.findViewById(R.id.textVerifiedIds)
        val flagsSubmittedText: TextView = view.findViewById(R.id.textFlagsSubmitted)
        val totalPointsText: TextView = view.findViewById(R.id.textTotalPoints)

        // Account info
        val dateJoinedText: TextView = view.findViewById(R.id.textDateJoined)
        val lastLoginText: TextView = view.findViewById(R.id.textLastLogin)
        val lastUpdateText: TextView = view.findViewById(R.id.textLastUpdate)
        val locationText: TextView = view.findViewById(R.id.textLocation)

        // Preferences
        val showContributionsText: TextView = view.findViewById(R.id.textShowContributions)
        val showEmailText: TextView = view.findViewById(R.id.textShowEmail)
        val showLocationText: TextView = view.findViewById(R.id.textShowLocation)
    }
}