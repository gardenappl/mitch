package ua.gardenapple.itchupdater.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import ua.gardenapple.itchupdater.R
import ua.gardenapple.itchupdater.client.ItchLibraryItem
import ua.gardenapple.itchupdater.client.ItchWebsiteParser

class OwnedGamesAdapter(
    private val context: Context
) : PagingDataAdapter<ItchLibraryItem, OwnedGamesAdapter.OwnedGameHolder>(OWNED_GAMES_COMPARATOR) {

    private val inflater: LayoutInflater by lazy {
        LayoutInflater.from(context)
    }

    companion object {
        private val OWNED_GAMES_COMPARATOR = object : DiffUtil.ItemCallback<ItchLibraryItem>() {
            override fun areItemsTheSame(
                oldItem: ItchLibraryItem,
                newItem: ItchLibraryItem
            ): Boolean {
                return oldItem == newItem
            }

            override fun areContentsTheSame(
                oldItem: ItchLibraryItem,
                newItem: ItchLibraryItem
            ): Boolean {
                return areItemsTheSame(oldItem, newItem)
            }

        }
    }

    class OwnedGameHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val thumbnailView: ImageView = itemView.findViewById(R.id.ownedGameThumbnail)
        val infoLayout: ConstraintLayout = itemView.findViewById(R.id.ownedGameInfoLayout)
        val gameName: TextView = itemView.findViewById(R.id.ownedGameName)
        val gameAuthor: TextView = itemView.findViewById(R.id.ownedGameAuthor)
        val gameAndroidLabel: TextView = itemView.findViewById(R.id.ownedGameAndroid)
//        val downloadOrInstallButton: MaterialButton =
//            itemView.findViewById(R.id.ownedDownloadOrInstallButton)
        val loadingBar: ProgressBar = itemView.findViewById(R.id.ownedLoadingBar)
    }

    override fun onBindViewHolder(holder: OwnedGameHolder, position: Int) {
        val ownedLibraryItem = getItem(position)

        if (ownedLibraryItem == null) {
            holder.infoLayout.visibility = View.GONE
            holder.loadingBar.visibility = View.VISIBLE
            holder.itemView.isClickable = false
            return
        }

        holder.infoLayout.visibility = View.VISIBLE
        holder.loadingBar.visibility = View.GONE
        
        holder.gameName.text = ownedLibraryItem.title
        holder.gameAuthor.text = ownedLibraryItem.author
        //Required for marquee animation
        holder.gameName.isSelected = true
        holder.gameAuthor.isSelected = true

        holder.gameAndroidLabel.text = if (ownedLibraryItem.isAndroid)
            context.resources.getString(R.string.platform_android)
        else
            ""

//        holder.downloadOrInstallButton.text = if (ownedLibraryItem.isAndroid)
//            context.resources.getString(R.string.library_install)
//        else
//            context.resources.getString(R.string.library_download)


        val downloadUri = Uri.parse(ownedLibraryItem.downloadUrl)

        holder.itemView.setOnClickListener { _ ->
            val storePageUri = Uri.parse(ItchWebsiteParser.getStoreUrlFromDownloadPage(downloadUri))
            val intent = Intent(Intent.ACTION_VIEW, storePageUri, context, MainActivity::class.java)
            context.startActivity(intent)
        }
//        holder.downloadOrInstallButton.setOnClickListener { _ ->
//            val intent = Intent(Intent.ACTION_VIEW, downloadUri)
//            context.startActivity(intent)
//        }


        Glide.with(context)
            .load(ownedLibraryItem.thumbnailUrl)
            .override(OwnedGamesActivity.THUMBNAIL_WIDTH, OwnedGamesActivity.THUMBNAIL_HEIGHT)
            .into(holder.thumbnailView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OwnedGameHolder {
        return OwnedGameHolder(inflater.inflate(R.layout.owned_item, parent, false))
    }
}