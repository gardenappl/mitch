package ua.gardenapple.itchupdater.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import ua.gardenapple.itchupdater.R
import ua.gardenapple.itchupdater.database.AppDatabase
import ua.gardenapple.itchupdater.database.game.Game
import ua.gardenapple.itchupdater.database.game.GameRepository

class GameListAdapter internal constructor(
    val context: Context,
    val list: RecyclerView,
    val type: GameRepository.Type
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
        itemView.setOnClickListener { view -> onCardClick(view) }
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

    private fun onCardClick(view: View) {
        val position = list.getChildLayoutPosition(view)
        val game = games[position]
        PopupMenu(context, view).apply {
            setOnMenuItemClickListener{ menuItem -> onMenuItemClick(menuItem, game) }
            inflate(R.menu.game_actions)
            when(type) {
                GameRepository.Type.Installed -> {
                    menu.removeItem(R.id.remove_from_app)
                }
                GameRepository.Type.Downloads -> {
                    menu.removeItem(R.id.app_info)
                }
            }
            show()
        }
    }

    private fun onMenuItemClick(item: MenuItem?, game: Game): Boolean {
        when (item?.itemId) {
            R.id.go_to_store -> {
                val intent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(game.storeUrl),
                    context,
                    MainActivity::class.java
                )
                context.startActivity(intent)
                return true
            }
            R.id.app_info -> {
                runBlocking(Dispatchers.IO) {
                    val db = AppDatabase.getDatabase(context)
                    val install = db.installDao.findInstallation(game.gameId)!!
                    val packageUri = Uri.parse("package:${install.packageName}")
                    try {
                        val intent =
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
                        context.startActivity(intent)
                    } catch (e: ActivityNotFoundException) {
                        val toast = Toast.makeText(context, R.string.game_package_info_fail, Toast.LENGTH_LONG)
                        toast.show()
                    }
                }
                return true
            }
            R.id.remove_from_app -> {
                val dialog = AlertDialog.Builder(context).apply {
                    setMessage(context.getString(R.string.dialog_game_remove, game.name))

                    setPositiveButton(R.string.dialog_remove) { _, _ ->
                        runBlocking(Dispatchers.IO) {
                            val db = AppDatabase.getDatabase(context)
                            db.installDao.deleteFinishedInstallation(game.gameId)
                        }
                        Toast.makeText(
                            context,
                            context.getString(R.string.dialog_game_remove_done, game.name),
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    setNegativeButton(R.string.dialog_cancel) { _, _ ->
                        //no-op
                    }

                    create()
                }
                dialog.show()
                return true
            }
            else -> return false
        }
    }
}