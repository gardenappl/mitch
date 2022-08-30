package garden.appl.mitch.ui

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
import androidx.core.view.isVisible
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import garden.appl.mitch.R
import garden.appl.mitch.client.ItchLibraryItem
import garden.appl.mitch.client.ItchWebsiteParser
import garden.appl.mitch.data.ItchLibraryUiModel
import garden.appl.mitch.databinding.OwnedItemSeparatorBinding

class OwnedGamesAdapter(
    private val context: Context
) : PagingDataAdapter<ItchLibraryUiModel, RecyclerView.ViewHolder>(OWNED_GAMES_COMPARATOR) {

    private val inflater: LayoutInflater by lazy {
        LayoutInflater.from(context)
    }

    companion object {
        private val OWNED_GAMES_COMPARATOR = object : DiffUtil.ItemCallback<ItchLibraryUiModel>() {
            override fun areItemsTheSame(
                oldItem: ItchLibraryUiModel,
                newItem: ItchLibraryUiModel
            ): Boolean {
                return oldItem == newItem
            }

            override fun areContentsTheSame(
                oldItem: ItchLibraryUiModel,
                newItem: ItchLibraryUiModel
            ): Boolean {
                return areItemsTheSame(oldItem, newItem)
            }
        }
    }


    class OwnedGameHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val thumbnailView: ImageView = itemView.findViewById(R.id.ownedGameThumbnail)
        val thumbnailEmptyView: ImageView = itemView.findViewById(R.id.ownedGameThumbnailEmpty)
        val infoLayout: ConstraintLayout = itemView.findViewById(R.id.ownedGameInfoLayout)
        val gameName: TextView = itemView.findViewById(R.id.ownedGameName)
        val gameAuthor: TextView = itemView.findViewById(R.id.ownedGameAuthor)
        val gameAndroidLabel: TextView = itemView.findViewById(R.id.ownedGameAndroid)
        val loadingBar: ProgressBar = itemView.findViewById(R.id.ownedLoadingBar)


        fun bind(ownedLibraryItem: ItchLibraryItem, context: Context) {
            infoLayout.visibility = View.VISIBLE
            loadingBar.visibility = View.GONE

            gameName.text = ownedLibraryItem.title
            gameAuthor.text = ownedLibraryItem.author
            //Required for marquee animation
            gameName.isSelected = true
            gameAuthor.isSelected = true

            gameAndroidLabel.text = if (ownedLibraryItem.isAndroid)
                context.resources.getString(R.string.platform_android)
            else
                ""


            val downloadUri = Uri.parse(ownedLibraryItem.downloadUrl)

            itemView.setOnClickListener { _ ->
                val storePageUri = Uri.parse(ItchWebsiteParser.getStoreUrlFromDownloadPage(downloadUri))
                val intent = Intent(Intent.ACTION_VIEW, storePageUri, context, MainActivity::class.java)
                context.startActivity(intent)
            }


            if (ownedLibraryItem.thumbnailUrl != null) {
                thumbnailView.visibility = View.VISIBLE
                thumbnailEmptyView.visibility = View.INVISIBLE
                Glide.with(context)
                    .load(ownedLibraryItem.thumbnailUrl)
                    .override(OwnedGamesActivity.THUMBNAIL_WIDTH, OwnedGamesActivity.THUMBNAIL_HEIGHT)
                    .into(thumbnailView)
            } else {
                thumbnailView.visibility = View.INVISIBLE
                thumbnailEmptyView.visibility = View.VISIBLE
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val uiModel = getItem(position)!!) {
            is ItchLibraryUiModel.Item -> (holder as OwnedGameHolder).bind(uiModel.item, context)
            is ItchLibraryUiModel.Separator -> (holder as SeparatorHolder).bind(uiModel)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == R.layout.owned_item) {
            OwnedGameHolder(inflater.inflate(R.layout.owned_item, parent, false))
        } else {
            SeparatorHolder(OwnedItemSeparatorBinding.inflate(inflater, parent, false))
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is ItchLibraryUiModel.Item -> R.layout.owned_item
            is ItchLibraryUiModel.Separator -> R.layout.owned_item_separator
            else -> throw UnsupportedOperationException("Unknown item type")
        }
    }


    class SeparatorHolder(private val binding: OwnedItemSeparatorBinding)
        : RecyclerView.ViewHolder(binding.root) {

        fun bind(separator: ItchLibraryUiModel.Separator) {
            binding.divider.isVisible = !separator.isFirst
            binding.purchaseDateLabel.text = separator.purchaseDate
        }
    }
}