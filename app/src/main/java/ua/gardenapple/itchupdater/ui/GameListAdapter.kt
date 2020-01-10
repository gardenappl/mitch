package ua.gardenapple.itchupdater.ui

import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import ua.gardenapple.itchupdater.R
import ua.gardenapple.itchupdater.database.game.GameEntity

class GameListAdapter internal constructor(
    context: Context
) : RecyclerView.Adapter<GameListAdapter.GameViewHolder>() {

    private val inflater: LayoutInflater = LayoutInflater.from(context)
    private var games = emptyList<GameEntity>() // Cached copy of games

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
        //holder.thumbnailView.setImageURI(Uri.parse(currentGame.thumbnailURL))
        holder.gameName.text = currentGame.name
        holder.authorName.text = currentGame.author
    }

    internal fun setGames(games: List<GameEntity>) {
        this.games = games
        notifyDataSetChanged()
    }

    override fun getItemCount() = games.size
}