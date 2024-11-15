package garden.appl.mitch.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.app.DownloadManager
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.Parcel
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.view.menu.MenuPopupHelper
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import garden.appl.mitch.FILE_PROVIDER
import garden.appl.mitch.Mitch
import garden.appl.mitch.NOTIFICATION_TAG_DOWNLOAD
import garden.appl.mitch.NOTIFICATION_TAG_DOWNLOAD_LONG
import garden.appl.mitch.R
import garden.appl.mitch.Utils
import garden.appl.mitch.database.AppDatabase
import garden.appl.mitch.database.game.Game
import garden.appl.mitch.database.game.GameRepository
import garden.appl.mitch.database.installation.GameInstallation
import garden.appl.mitch.database.installation.Installation
import garden.appl.mitch.install.AbstractInstaller
import garden.appl.mitch.install.Installations
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext


class LibraryAdapter internal constructor(
    private val activity: MitchActivity,
    val list: RecyclerView,
    val type: GameRepository.Type
) : RecyclerView.Adapter<LibraryAdapter.GameViewHolder>() {

    companion object {
        private const val LOGGING_TAG = "GameListAdapter"
    }
    
    private val context: Context = activity
    private val mainActivityScope: CoroutineScope = activity as MainActivity

    private val inflater: LayoutInflater = LayoutInflater.from(context)
    var gameInstalls = emptyList<GameInstallation>() // Cached copy of games
        internal set(value) {
            field = value
            notifyDataSetChanged()
        }

    inner class GameViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val thumbnailView: ImageView = itemView.findViewById(R.id.gameThumbnail)
        val emptyThumbnailView: ImageView = itemView.findViewById(R.id.gameThumbnailEmpty)
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
        holder.itemView.setOnLongClickListener { view ->
            onCardOverflowClick(holder.overflowMenuButton, position)
            return@setOnLongClickListener true
        }

        if (game.thumbnailUrl != null) {
            Glide.with(context)
                .load(game.thumbnailUrl)
                .override(LibraryFragment.THUMBNAIL_WIDTH, LibraryFragment.THUMBNAIL_HEIGHT)
                .into(holder.thumbnailView)
            holder.thumbnailView.visibility = View.VISIBLE
            holder.emptyThumbnailView.visibility = View.INVISIBLE
        } else {
            holder.thumbnailView.visibility = View.INVISIBLE
            holder.emptyThumbnailView.visibility = View.VISIBLE
        }
    }

    override fun getItemCount() = gameInstalls.size
    
    private fun onCardClick(view: View) {
        val position = list.getChildLayoutPosition(view)
        val gameInstall = gameInstalls[position]

        if (gameInstall.status == Installation.STATUS_READY_TO_INSTALL) {
            val notificationService = context.getSystemService(Activity.NOTIFICATION_SERVICE)
                    as NotificationManager

            if (Utils.fitsInInt(gameInstall.downloadOrInstallId!!)) {
                notificationService.cancel(NOTIFICATION_TAG_DOWNLOAD,
                    gameInstall.downloadOrInstallId.toInt())
            } else {
                notificationService.cancel(NOTIFICATION_TAG_DOWNLOAD_LONG,
                    gameInstall.downloadOrInstallId.toInt())
            }

            mainActivityScope.launch {
                val installer = Installations.getInstaller(gameInstall.downloadOrInstallId)
                when (installer.type) {
                    AbstractInstaller.Type.File -> {
                        val file = Mitch.installDownloadManager.getPendingFile(gameInstall.uploadId)!!
                        installer.requestInstall(context, gameInstall.downloadOrInstallId, file)
                    }
                    AbstractInstaller.Type.Stream ->
                        installer.finishStreamInstall(context, gameInstall.downloadOrInstallId.toInt(), gameInstall.game.name)
                }
            }
        } else if (type == GameRepository.Type.WebCached) {
            val intent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse(gameInstall.game.webEntryPoint),
                context,
                GameActivity::class.java
            )
//            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.putExtra(GameActivity.EXTRA_GAME_ID, gameInstall.game.gameId)
            intent.putExtra(GameActivity.EXTRA_LAUNCHED_FROM_INSTALL, true)
            context.startActivity(intent)
        } else if (gameInstall.packageName != null) {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(gameInstall.packageName)
            if (launchIntent != null) {
                context.startActivity(launchIntent)
            } else {
                Toast.makeText(context, R.string.library_open_app_failed, Toast.LENGTH_LONG)
                    .show()
            }
        } else if (gameInstall.externalFileUri != null) {
            Log.d(LOGGING_TAG, "External URI: ${gameInstall.externalFileUri}")
            if (!gameInstall.externalFileUri.contains("://")) {
                Log.d(LOGGING_TAG, "Legacy code: not a URI")
                val fileName = gameInstall.externalFileUri
                Toast.makeText(context, context.getString(R.string.popup_moved_to_downloads, fileName),
                    Toast.LENGTH_LONG).show()
                return
            }
            val uri = Uri.parse(gameInstall.externalFileUri)
            Mitch.externalFileManager.getViewIntent(activity, uri) { intent ->
                if (intent != null) {
                    context.startActivity(Intent.createChooser(intent,
                        context.resources.getString(R.string.select_app_for_file)))
                    return@getViewIntent
                }
                //File is missing
                val dialog = AlertDialog.Builder(context).apply {
                    setTitle(R.string.dialog_missing_file_title)
                    setMessage(context.getString(R.string.dialog_missing_file,
                    gameInstall.externalFileUri, gameInstall.uploadName))

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
            val downloadedFile = Mitch.installDownloadManager.getDownloadedFile(gameInstall.uploadId)
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
            if (!(type == GameRepository.Type.Downloads && gameInstall.externalFileUri == null))
                removeItem(R.id.move_to_downloads)
            if (type != GameRepository.Type.WebCached)
                removeItem(R.id.web_install_launcher_shortcut)
            if (!((type == GameRepository.Type.Downloads && gameInstall.externalFileUri == null) ||
                        type == GameRepository.Type.Installed ||
                        type == GameRepository.Type.WebCached))
                removeItem(R.id.delete)
            if (!(type == GameRepository.Type.Downloads && gameInstall.externalFileUri != null))
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
                mainActivityScope.launch(Dispatchers.IO) {
                    try {
                        Mitch.externalFileManager.moveToDownloads(activity, gameInstall.uploadId) { uri ->
                            if (uri == null) {
                                Log.e(LOGGING_TAG, "externalName is null! " +
                                        "This should only happen with old downloads")

                                mainActivityScope.launch(Dispatchers.Main) {
                                    Toast.makeText(
                                        context,
                                        R.string.popup_move_to_download_error,
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                                return@moveToDownloads
                            }

                            mainActivityScope.launch {
                                val db = AppDatabase.getDatabase(context)
                                val install =
                                    db.installDao.getInstallationById(gameInstall.installId)!!
                                db.installDao.update(install.copy(
                                    externalFileUri = uri.toString()
                                ))
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(
                                        context,
                                        context.resources.getString(R.string.popup_moved_to_downloads,
                                            uri.lastPathSegment),
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
            R.id.web_install_launcher_shortcut -> {
                mainActivityScope.launch {
                    val shortcut = GameActivity.makeShortcut(game, context)
                    if (!ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)) {
                        Toast.makeText(context, R.string.popup_web_install_launcher_shortcut_error, Toast.LENGTH_LONG)
                    }
                }
                return true
            }
            R.id.delete -> {
                if (type == GameRepository.Type.Installed) {
                    val intent = Intent(Intent.ACTION_DELETE,
                        Uri.parse("package:${gameInstall.packageName}"))
                    context.startActivity(intent)
                } else if (type == GameRepository.Type.WebCached) {
                    if (Utils.checkServiceRunning(context, GameForegroundService::class.java)) {
                        Utils.logDebug(LOGGING_TAG, "Running.")
                        // need to check game ID
                        val intent = Intent(context, GameForegroundService::class.java)

                        val deleteGameServiceConnection = object : ServiceConnection {
                            override fun onServiceConnected(className: ComponentName, binderInterface: IBinder) {
                                Utils.logDebug(LOGGING_TAG, "connected")
                                val data = if (Build.VERSION.SDK_INT >= 33)
                                    Parcel.obtain(binderInterface)
                                else
                                    Parcel.obtain()
                                data.writeInt(GameForegroundService.TRANSACT_TYPE_GAME_ID)
                                val reply = if (Build.VERSION.SDK_INT >= 33)
                                    Parcel.obtain(binderInterface)
                                else
                                    Parcel.obtain()
                                binderInterface.transact(Binder.FIRST_CALL_TRANSACTION, data, reply, 0)
                                val gameId = reply.readInt()
                                data.recycle()
                                reply.recycle()
                                Utils.logDebug(LOGGING_TAG, "binder: $gameId, here: ${game.gameId}")
                                onDeleteWebChachedGame(game, gameId == game.gameId)
                                activity.unbindService(this)
                            }

                            override fun onServiceDisconnected(p0: ComponentName?) {
                                Utils.logDebug(LOGGING_TAG, "disconnected")
                            }
                        }

                        if (!activity.bindService(intent, deleteGameServiceConnection, 0)) {
                            Log.d(LOGGING_TAG, "could not connect")
                            onDeleteWebChachedGame(game, false)
                        }
                    } else {
                        Log.d(LOGGING_TAG, "service not running")
                        onDeleteWebChachedGame(game, false)
                    }
                } else {
                    val dialog = AlertDialog.Builder(context).apply {
                        runBlocking(Dispatchers.IO) {
                            if (Mitch.installDownloadManager.getDownloadedFile(
                                    gameInstall.uploadId
                                )?.exists() == true
                            ) {
                                setTitle(R.string.dialog_game_delete_title)
                                setMessage(
                                    context.getString(
                                        R.string.dialog_game_delete,
                                        gameInstall.uploadName
                                    )
                                )
                            } else {
                                //this should only be possible in old versions of Mitch
                                setTitle(R.string.dialog_game_remove_title)
                                setMessage(
                                    context.getString(
                                        R.string.dialog_game_remove,
                                        gameInstall.uploadName
                                    )
                                )
                            }
                        }

                        setPositiveButton(R.string.dialog_delete) { _, _ ->
                            mainActivityScope.launch(Dispatchers.Main) {
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
                }
                return true
            }
            R.id.remove -> {
                val dialog = AlertDialog.Builder(context).apply {
                    setTitle(R.string.dialog_game_remove_title)
                    setMessage(context.getString(R.string.dialog_game_remove, gameInstall.externalFileUri))

                    setPositiveButton(R.string.dialog_remove) { _, _ ->
                        mainActivityScope.launch(Dispatchers.Main) {
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
                mainActivityScope.launch(Dispatchers.Main) {
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

    private fun onDeleteWebChachedGame(game: Game, isRunningNow: Boolean) {
        if (isRunningNow) {
            val dialog = AlertDialog.Builder(context).apply {
                setTitle(R.string.dialog_web_cache_delete_fail_title)
                setMessage(context.getString(R.string.dialog_web_cache_delete_fail, game.name))
                setPositiveButton(android.R.string.ok) { _, _ -> }
                setCancelable(true)

                create()
            }
            dialog.show()
        } else {
            val dialog = AlertDialog.Builder(context).apply {
                setTitle(R.string.dialog_web_cache_delete_title)
                setMessage(context.getString(R.string.dialog_app_delete, game.name))
                setPositiveButton(R.string.dialog_yes) { _, _ ->
                    mainActivityScope.launch {
                        ShortcutManagerCompat.removeDynamicShortcuts(context,
                            listOf(GameActivity.getShortcutId(game.gameId)))
                        Mitch.webGameCache.deleteCacheForGame(context, game.gameId)

                        Toast.makeText(
                            context,
                            context.getString(
                                R.string.popup_game_deleted,
                                game.name
                            ),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                setNegativeButton(R.string.dialog_no) { _, _ -> }
                setCancelable(true)

                create()
            }
            dialog.show()
        }
    }
}