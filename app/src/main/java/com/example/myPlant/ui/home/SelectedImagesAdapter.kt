package com.example.myPlant.ui.home

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.example.myPlant.R

class SelectedImagesAdapter(
    private val uris: MutableList<Uri>,
    private val onRemoveClick: (Uri) -> Unit
) : RecyclerView.Adapter<SelectedImagesAdapter.ImageViewHolder>() {

    inner class ImageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.image_preview)
        val removeButton: ImageView = view.findViewById(R.id.remove_image_button)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.selected_image_item, parent, false)
        return ImageViewHolder(view)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        val uri = uris[position]
        holder.imageView.setImageURI(uri)
        holder.removeButton.setOnClickListener {
            onRemoveClick(uri)
        }
    }

    override fun getItemCount(): Int = uris.size
}
