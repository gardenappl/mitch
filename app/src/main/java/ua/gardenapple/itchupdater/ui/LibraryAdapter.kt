package ua.gardenapple.itchupdater.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.app.DownloadManager
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.view.menu.MenuPopupHelper
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import kotlinx.coroutines.*
import ua.gardenapple.itchupdater.*
import ua.gardenapple.itchupdater.database.AppDatabase
import ua.gardenapple.itchupdater.database.game.GameRepository
import ua.gardenapple.itchupdater.database.installation.GameInstallation
import ua.gardenapple.itchupdater.database.installation.Installation
import ua.gardenapple.itchupdater.installer.Installations


class LibraryAdapter internal constructor(
    private val activity: Activity,
    val list: RecyclerView,
    val type: GameRepository.Type
) : RecyclerView.Adapter<LibraryAdapter.GameViewHolder>() {

    companion object {
        private const val LOGGING_TAG = "GameListAdapter"
    }
    
    private val context: Context = activity

    private val inflater: LayoutInflater = LayoutInflater.from(context)
    var gameInstalls = emptyList<GameInstallation>() // Cached copy of games
        internal set(value) {
            field = value
            notifyDataSetChanged()
        }

    inner class GameViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val thumbnailView: ImageView = itemView.findViewById(R.id.gameThumbnail)
        val emptyThumbnailView: ImageView = itemView.findViewById(R.id.gameThumbnailEmpty);
        val gameName: TextView = itemView.findViewById(R.id.gameName)
        val authorOrSubtitle: TextView = itemView.findViewById(R.id.authorOrSubtitle)
        val progressBarLayout: LinearLayout = itemView.findViewById(R.id.progressBarLayout)
        val progressBarLabel: TextView = itemView.findViewById(R.id.progressBarLabel)
        val overflowMenuButton: ImageButton = itemView.findViewById(R.id.overflowMenu)
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
        //Required for marquee animation
        holder.authorOrSubtitle.isSelected = true

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

        holder.thumbnailView.isVisible = game.thumbnailUrl != null
        if (game.thumbnailUrl != null) {
            Glide.with(context)
                .load(game.thumbnailUrl)
                .override(LibraryFragment.THUMBNAIL_WIDTH, LibraryFragment.THUMBNAIL_HEIGHT)
                .into(holder.thumbnailView)
            holder.emptyThumbnailView.visibility = View.INVISIBLE
        } else {
            holder.emptyThumbnailView.visibility = View.VISIBLE
        }
    }

    override fun getItemCount() = gameInstalls.size
    
    private fun onCardClick(view: View) {
        val position = list.getChildLayoutPosition(view)
        val gameInstall = gameInstalls[position]

        if (gameInstall.status == Installation.STATUS_READY_TO_INSTALL) {
            val notificationService = context.getSystemService(Activity.NOTIFICATION_SERVICE) as NotificationManager
            gameInstall.downloadOrInstallId!!.let {
                if (Utils.fitsInInt(it))
                    notificationService.cancel(NOTIFICATION_TAG_DOWNLOAD, it.toInt())
                else
                    notificationService.cancel(NOTIFICATION_TAG_DOWNLOAD_LONG, it.toInt())
            }

            GlobalScope.launch {
                Mitch.installer.install(context, gameInstall.downloadOrInstallId, gameInstall.uploadId)
            }
        } else if (gameInstall.packageName != null) {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(gameInstall.packageName)
            if (launchIntent != null) {
                context.startActivity(launchIntent)
            } else {
                Toast.makeText(context, R.string.library_open_app_failed, Toast.LENGTH_LONG)
                    .show()
            }
        } else if (gameInstall.externalFileName != null) {
            Mitch.externalFileManager.getViewIntent(activity, gameInstall.externalFileName) { intent ->
                if (intent != null) {
                    context.startActivity(Intent.createChooser(intent,
                        context.resources.getString(R.string.select_app_for_file)))
                    return@getViewIntent
                }
                //File is missing
                val dialog = AlertDialog.Builder(context).apply {
                    setTitle(R.string.dialog_missing_file_title)
                    setMessage(context.getString(R.string.dialog_missing_file,
                    gameInstall.externalFileName, gameInstall.uploadName))

                    setPositiveButton(R.string.dialog_remove) { _, _ ->
                        runBlocking(Dispatchers.IO) {
                            val db = AppDatabase.getDatabase(context)
                            db.installDao.deleteFinishedInstallation(gameInstall.uploadId)
                        }

                        view.post {
                            Toast.makeText(
                                context,
                                context.getString(
                                    R.string.popup_game_removed,
                                    gameInstall.uploadName
                                ),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                    setNegativeButton(R.string.dialog_cancel) { _, _ ->
                        //no-op
                    }

                    create()
                }
                dialog.show()
            }

        } else if (gameInstall.status == Installation.STATUS_INSTALLED) {
            val downloadedFile = Mitch.fileManager.getDownloadedFile(gameInstall.uploadId)
            if (downloadedFile?.exists() == true) {
                val intent = Utils.getIntentForFile(context, downloadedFile, FILE_PROVIDER)
                context.startActivity(Intent.createChooser(intent, context.resources.getString(R.string.select_app_for_file)))
            } else {
                //Should only happen for older versions of Mitch
                val intent = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
        }
    }

    //TODO: Showing icons in a PopupMenu requires restricted API...
    @SuppressLint("RestrictedApi")
    private fun onCardOverflowClick(view: View, position: Int) {
        val gameInstall = gameInstalls[position]

        val popupMenu = MenuBuilder(context).apply {
            MenuInflater(context).inflate(R.menu.game_actions, this)

            if (type != GameRepository.Type.Installed)
                removeItem(R.id.app_info)
            if (!(type == GameRepository.Type.Downloads && gameInstall.externalFileName == null))
                removeItem(R.id.move_to_downloads)
            if (!((type == GameRepository.Type.Downloads && gameInstall.externalFileName == null) ||
                        type == GameRepository.Type.Installed))
                removeItem(R.id.delete)
            if (!(type == GameRepository.Type.Downloads && gameInstall.externalFileName != null))
                removeItem(R.id.remove)
            if (type != GameRepository.Type.Pending)
                removeItem(R.id.cancel)

            setCallback(object : MenuBuilder.Callback {
                override fun onMenuItemSelected(menu: MenuBuilder, item: MenuItem): Boolean {
                    return onMenuItemClick(item, gameInstall)
                }

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
                if (activity is MainActivity) {
                    activity.browseUrl(game.storeUrl)
                } else {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(game.storeUrl),
                        context, MainActivity::class.java)
                    context.startActivity(intent)
                }
                return true
            }
            R.id.app_info -> {
                try {
                    val packageUri = Uri.parse("package:${gameInstall.packageName}")
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
                    context.startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(context, R.string.game_package_info_fail, Toast.LENGTH_LONG)
                        .show()
                }
                return true
            }
            R.id.move_to_downloads -> {
                Toast.makeText(context, R.string.popup_moving_to_downloads, Toast.LENGTH_LONG)
                    .show()
                GlobalScope.launch(Dispatchers.IO) {
                    try {
                        Mitch.externalFileManager.moveToDownloads(activity, gameInstall.uploadId) { externalName ->
                            if (externalName == null) {
                                Log.e(LOGGING_TAG, "externalName is null! " +
                                        "This should only happen with old downloads")

                                GlobalScope.launch(Dispatchers.Main) {
                                    Toast.makeText(
                                        context,
                                        R.string.popup_move_to_download_error,
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }

                            GlobalScope.launch(Dispatchers.IO) {
                                val db = AppDatabase.getDatabase(context)
                                val install =
                                    db.installDao.getInstallationById(gameInstall.installId)!!
                                db.installDao.update(install.copy(
                                    externalFileName = externalName
                                ))
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(
                                        context,
                                        context.resources.getString(R.string.popup_moved_to_downloads,
                                            externalName),
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }
                    } catch(e: Exception) {
                        Log.e(LOGGING_TAG, "Error while moving: ", e)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, R.string.popup_move_to_download_error, Toast.LENGTH_LONG)
                                .show()
                        }
                    }
                }
                return true
            }
            R.id.delete -> {
                if (type == GameRepository.Type.Installed) {
                    val intent = Intent(Intent.ACTION_DELETE,
                        Uri.parse("package:${gameInstall.packageName}"))
                    context.startActivity(intent)
                    return true
                }

                val dialog = AlertDialog.Builder(context).apply {
                    runBlocking(Dispatchers.IO) {
                        if (Mitch.fileManager.getDownloadedFile(
                                gameInstall.uploadId)?.exists() == true) {
                            setTitle(R.string.dialog_game_delete_title)
                            setMessage(context.getString(R.string.dialog_game_delete, gameInstall.uploadName))
                        } else {
                            //this should only be possible in old versions of Mitch
                            setTitle(R.string.dialog_game_remove_title)
                            setMessage(context.getString(R.string.dialog_game_remove, gameInstall.uploadName))
                        }
                    }

                    setPositiveButton(R.string.dialog_delete) { _, _ ->
                        GlobalScope.launch(Dispatchers.Main) {
                            Installations.deleteFinishedInstall(context, gameInstall.uploadId)

                            Toast.makeText(
                                context,
                                context.getString(
                                        R.string.popup_game_deleted,
                                        gameInstall.uploadName
                                    ),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                    setNegativeButton(R.string.dialog_cancel) { _, _ ->
                        //no-op
                    }

                    create()
                }
                dialog.show()
                return true
            }
            R.id.remove -> {
                val dialog = AlertDialog.Builder(context).apply {
                    setTitle(R.string.dialog_game_remove_title)
                    setMessage(context.getString(R.string.dialog_game_remove, gameInstall.externalFileName))

                    setPositiveButton(R.string.dialog_remove) { _, _ ->
                        GlobalScope.launch(Dispatchers.Main) {
                            Installations.deleteFinishedInstall(context, gameInstall.uploadId)
                            Toast.makeText(
                                context,
                                context.getString(
                                    R.string.popup_game_removed,
                                    gameInstall.uploadName
                                ),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
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
                GlobalScope.launch(Dispatchers.Main) {
                    Installations.cancelPending(
                        context,
                        gameInstall.status,
                        gameInstall.downloadOrInstallId!!,
                        gameInstall.uploadId,
                        gameInstall.installId
                    )
                    Toast.makeText(
                        context,
                        context.getString(R.string.dialog_cancel_download_done),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                return true
            }
            else -> return false
        }
    }
}