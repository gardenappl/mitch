package ua.gardenapple.itchupdater.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import ua.gardenapple.itchupdater.R
import ua.gardenapple.itchupdater.database.game.Game

class GameListAdapter internal constructor(
    val context: Context
) : RecyclerView.Adapter<GameListAdapter.GameViewHolder>() {

    private val inflater: LayoutInflater = LayoutInflater.from(context)
    var games = emptyList<Game>() // Cached copy of games
        internal set(value) {
            field = value
            notifyDataSetChanged()
        }

    inner class GameViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val thumbnailView = itemView.findViewById<ImageView>(R.id.gameThumbnail)
        val gameName = itemView.findViewById<TextView>(R.id.gameName)
        val authorName = itemView.findViewById<TextView>(R.id.authorName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GameViewHolder {
        val itemView = inflater.inflate(R.layout.library_item, parent, false)
        return GameViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: GameViewHolder, position: Int) {
        val currentGame = games[position]
        holder.gameName.text = currentGame.name
        holder.authorName.text = currentGame.author
        Glide.with(context)
            .load(games[position].thumbnailUrl)
            .override(LibraryFragment.THUMBNAIL_WIDTH, LibraryFragment.THUMBNAIL_HEIGHT)
            .into(holder.thumbnailView)
    }

    override fun getItemCount() = games.size
}