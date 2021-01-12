package ua.gardenapple.itchupdater.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.app.DownloadManager
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.view.menu.MenuPopupHelper
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import kotlinx.android.synthetic.main.library_item.view.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import ua.gardenapple.itchupdater.*
import ua.gardenapple.itchupdater.database.AppDatabase
import ua.gardenapple.itchupdater.database.game.GameRepository
import ua.gardenapple.itchupdater.database.installation.GameInstallation
import ua.gardenapple.itchupdater.database.installation.Installation
import java.net.URLConnection

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
        val thumbnailView: ImageView = itemView.gameThumbnail
        val gameName: TextView = itemView.gameName
        val authorOrSubtitle: TextView = itemView.authorOrSubtitle
        val progressBarLayout: LinearLayout = itemView.progressBarLayout
        val progressBarLabel: TextView = itemView.progressBarLabel
        val overflowMenuButton: ImageButton = itemView.overflowMenu
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GameViewHolder {
        val itemView = inflater.inflate(R.layout.library_item, parent, false)
        itemView.setOnClickListener { view -> onCardClick(view) }
        return GameViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: GameViewHolder, position: Int) {
        val gameInstall = gameInstalls[position]
        val game = gameInstalls[position].game
        holder.gameName.text = game.name
        holder.authorOrSubtitle.text = gameInstall.librarySubtitle

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

        holder.overflowMenuButton.setOnClickListener { view -> onCardOverflowClick(view, position) }

        Glide.with(context)
            .load(game.thumbnailUrl)
            .override(LibraryFragment.THUMBNAIL_WIDTH, LibraryFragment.THUMBNAIL_HEIGHT)
            .into(holder.thumbnailView)
    }

    override fun getItemCount() = gameInstalls.size
    
    private fun onCardClick(view: View) {
        val position = list.getChildLayoutPosition(view)
        val gameInstall = gameInstalls[position]

        if (gameInstall.status == Installation.STATUS_READY_TO_INSTALL) {
            val notificationService = context.getSystemService(Activity.NOTIFICATION_SERVICE) as NotificationManager
            notificationService.cancel(NOTIFICATION_TAG_DOWNLOAD_RESULT, gameInstall.downloadOrInstallId)

            GlobalScope.launch {
                MitchApp.installer.install(context, gameInstall.downloadOrInstallId, gameInstall.uploadId)
            }
        } else if (gameInstall.packageName != null) {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(gameInstall.packageName)
            if (launchIntent != null) {
                context.startActivity(launchIntent)
            }
        } else if (gameInstall.status == Installation.STATUS_INSTALLED) {
            val downloadedFile = MitchApp.downloadFileManager.getDownloadedFile(gameInstall.uploadId)
            if (downloadedFile?.exists() == true) {
                val intent = Utils.getIntentForFile(context, downloadedFile)
                if (intent.resolveActivity(context.packageManager) == null) {
                    //TODO: suggest to move to Downloads folder, that's why strings are not localized.
                    Toast.makeText(context, "No app found that can open this file", Toast.LENGTH_LONG)
                        .show()
                } else {
                    context.startActivity(Intent.createChooser(intent, context.resources.getString(R.string.select_app_for_file)))
                }
            } else {
                //Should only happen for older versions of Mitch
                val intent = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
        }
    }

    //TODO: Showing icons in a PopupMenu requires restriced API...
    @SuppressLint("RestrictedApi")
    private fun onCardOverflowClick(view: View, position: Int) {
        val gameInstall = gameInstalls[position]

        val popupMenu = MenuBuilder(context).apply {
            MenuInflater(context).inflate(R.menu.game_actions, this)

            if (type != GameRepository.Type.Installed)
                removeItem(R.id.app_info)
            if (type != GameRepository.Type.Downloads)
                removeItem(R.id.delete)
            if (type != GameRepository.Type.Pending)
                removeItem(R.id.cancel)

            setCallback(object : MenuBuilder.Callback {
                override fun onMenuItemSelected(menu: MenuBuilder, item: MenuItem): Boolean =
                    onMenuItemClick(item, gameInstall)

                override fun onMenuModeChange(menu: MenuBuilder) {}
            })
        }
        MenuPopupHelper(context, popupMenu, view).apply { 
            setForceShowIcon(true)
            show()
        }
    }

    private fun onMenuItemClick(item: MenuItem, gameInstall: GameInstallation): Boolean {
        val game = gameInstall.game
        when (item.itemId) {
            R.id.go_to_store -> {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(game.storeUrl),
                    context, MainActivity::class.java)
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
            R.id.delete -> {
                val dialog = AlertDialog.Builder(context).apply {
                    setTitle(R.string.dialog_game_delete_title)
                    runBlocking(Dispatchers.IO) {
                        if (MitchApp.downloadFileManager.getDownloadedFile(
                                gameInstall.uploadId)?.exists() == true) {
                            setMessage(context.getString(R.string.dialog_game_delete, gameInstall.uploadName))
                        } else {
                            //this should only be possible in old versions of Mitch
                            setMessage("""This file is from an older version of Mitch. This means it won't actually be deleted, instead it will stay in your Download folder.
                                |
                                |You'll stop receiving update notifications for this file, but if you want to delete it, you'll have to do it manually.
                                |Sorry for the confusion, this shouldn't happen with files downloaded on newer versions.
                            """.trimMargin())
                        }
                    }

                    setPositiveButton(R.string.dialog_delete) { _, _ ->
                        runBlocking(Dispatchers.IO) {
                            val db = AppDatabase.getDatabase(context)
                            db.installDao.deleteFinishedInstallation(gameInstall.uploadId)
                            MitchApp.downloadFileManager.deleteDownloadedFile(gameInstall.uploadId)
                        }
                        Toast.makeText(
                            context,
                            context.getString(R.string.dialog_game_delete_done, gameInstall.uploadName),
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
                if (gameInstall.status == Installation.STATUS_INSTALLING) {
                    val pkgInstaller = context.packageManager.packageInstaller
                    pkgInstaller.abandonSession(gameInstall.downloadOrInstallId)
                }
                val notificationService = context.getSystemService(Activity.NOTIFICATION_SERVICE) as NotificationManager
                notificationService.cancel(NOTIFICATION_TAG_DOWNLOAD_RESULT, gameInstall.downloadOrInstallId)
                runBlocking(Dispatchers.IO) {
                    if (gameInstall.status == Installation.STATUS_DOWNLOADING) {
                        MitchApp.downloadFileManager.requestCancellation(gameInstall.downloadOrInstallId,
                            gameInstall.uploadId)

                    } else {
                        MitchApp.downloadFileManager.deleteDownloadedFile(gameInstall.uploadId)
                    }
                    val db = AppDatabase.getDatabase(context)
                    db.installDao.delete(gameInstall.installId)
                }
                Toast.makeText(context, context.getString(R.string.dialog_cancel_download_done),
                    Toast.LENGTH_SHORT
                ).show()
                return true
            }
            else -> return false
        }
    }
}