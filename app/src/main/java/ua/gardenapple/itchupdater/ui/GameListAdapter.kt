package ua.gardenapple.itchupdater.ui

import android.app.Activity
import android.app.DownloadManager
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import kotlinx.android.synthetic.main.library_item.view.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import ua.gardenapple.itchupdater.MitchApp
import ua.gardenapple.itchupdater.NOTIFICATION_TAG_DOWNLOAD_RESULT
import ua.gardenapple.itchupdater.R
import ua.gardenapple.itchupdater.database.AppDatabase
import ua.gardenapple.itchupdater.database.game.GameInstallation
import ua.gardenapple.itchupdater.database.game.GameRepository
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
    var gameInstalls = emptyList<GameInstallation>() // Cached copy of games
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
        val currentGame = gameInstalls[position].game
        holder.gameName.text = currentGame.name
        holder.authorName.text = currentGame.author

        if (type == GameRepository.Type.Pending) {
            holder.progressBarLayout.visibility = View.VISIBLE

            holder.progressBarLabel.text = when (gameInstalls[position].status) {
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

    override fun getItemCount() = gameInstalls.size

    private fun onCardClick(view: View) {
        val position = list.getChildLayoutPosition(view)
        val gameInstall = gameInstalls[position]

        if (gameInstall.status == Installation.STATUS_READY_TO_INSTALL) {
            val notificationService = context.getSystemService(Activity.NOTIFICATION_SERVICE) as NotificationManager
            notificationService.cancel(NOTIFICATION_TAG_DOWNLOAD_RESULT, gameInstall.downloadOrInstallId.toInt())

            GlobalScope.launch {
                MitchApp.installer.installFromDownloadId(context, gameInstall.downloadOrInstallId)
            }
        } else {
            PopupMenu(context, view).apply {
                setOnMenuItemClickListener { menuItem -> onMenuItemClick(menuItem, gameInstall) }
                inflate(R.menu.game_actions)

                if (type != GameRepository.Type.Installed)
                    menu.removeItem(R.id.app_info)
                if (type != GameRepository.Type.Downloads)
                    menu.removeItem(R.id.remove_from_app)
                if (type != GameRepository.Type.Pending)
                    menu.removeItem(R.id.cancel)

                show()
            }
        }
    }

    private fun onMenuItemClick(item: MenuItem?,
                                gameInstall: GameInstallation): Boolean {
        val game = gameInstall.game
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
                try {
                    val packageUri = Uri.parse("package:${gameInstall.packageName}")
                    val intent =
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
                    context.startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    val toast =
                        Toast.makeText(context, R.string.game_package_info_fail, Toast.LENGTH_LONG)
                    toast.show()
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
            R.id.cancel -> {
                when (gameInstall.status) {
                    Installation.STATUS_INSTALLING -> {
                        val pkgInstaller = context.packageManager.packageInstaller
                        pkgInstaller.abandonSession(gameInstall.downloadOrInstallId.toInt())
                    }
                    Installation.STATUS_DOWNLOADING -> {
                        val downloadManager = context.getSystemService(Activity.DOWNLOAD_SERVICE) as DownloadManager
                        downloadManager.remove(gameInstall.downloadOrInstallId)
                    }
                }
                runBlocking(Dispatchers.IO) { 
                    val db = AppDatabase.getDatabase(context)
                    db.installDao.delete(gameInstall.installId)
                }
                return true
            }
            else -> return false
        }
    }
}