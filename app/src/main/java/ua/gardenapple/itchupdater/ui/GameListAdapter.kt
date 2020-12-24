package ua.gardenapple.itchupdater.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PorterDuff
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
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import kotlinx.android.synthetic.main.library_item.view.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import ua.gardenapple.itchupdater.R
import ua.gardenapple.itchupdater.database.AppDatabase
import ua.gardenapple.itchupdater.database.game.Game
import ua.gardenapple.itchupdater.database.game.GameRepository
import ua.gardenapple.itchupdater.database.game.GameWithInstallationStatus
import ua.gardenapple.itchupdater.database.installation.Installation

class GameListAdapter internal constructor(
    val context: Context,
    val list: RecyclerView,
    val type: GameRepository.Type
) : RecyclerView.Adapter<GameListAdapter.GameViewHolder>() {

    companion object {
        private const val LOGGING_TAG = "GameListAdapter"
    }

    private val inflater: LayoutInflater = LayoutInflater.from(context)
    var games = emptyList<GameWithInstallationStatus>() // Cached copy of games
        internal set(value) {
            field = value
            notifyDataSetChanged()
        }

    inner class GameViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val thumbnailView = itemView.gameThumbnail
        val gameName = itemView.gameName
        val authorName = itemView.authorName
        val progressBarLayout = itemView.progressBarLayout
        val progressBarLabel = itemView.progressBarLabel
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GameViewHolder {
        val itemView = inflater.inflate(R.layout.library_item, parent, false)
        itemView.setOnClickListener { view -> onCardClick(view) }
        return GameViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: GameViewHolder, position: Int) {
        val currentGame = games[position].game
        holder.gameName.text = currentGame.name
        holder.authorName.text = currentGame.author

        if (type == GameRepository.Type.Pending) {
            holder.progressBarLayout.visibility = View.VISIBLE

            holder.progressBarLabel.text = when (games[position].status) {
                Installation.STATUS_READY_TO_INSTALL ->
                    context.resources.getString(R.string.library_item_ready_to_install)
                Installation.STATUS_DOWNLOADING ->
                    context.resources.getString(R.string.library_item_downloading)
                Installation.STATUS_INSTALLING ->
                    context.resources.getString(R.string.library_item_installing)
                else -> ""
            }
        }

        Glide.with(context)
            .load(currentGame.thumbnailUrl)
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
            when (type) {
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

    private fun onMenuItemClick(item: MenuItem?,
                                gameWithStatus: GameWithInstallationStatus): Boolean {
        val game = gameWithStatus.game
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