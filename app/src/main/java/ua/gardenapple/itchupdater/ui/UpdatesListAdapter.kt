package ua.gardenapple.itchupdater.ui

import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.acra.ACRA
import ua.gardenapple.itchupdater.NOTIFICATION_TAG_UPDATE_CHECK
import ua.gardenapple.itchupdater.R
import ua.gardenapple.itchupdater.Utils
import ua.gardenapple.itchupdater.client.GameDownloader
import ua.gardenapple.itchupdater.client.UpdateCheckResult
import ua.gardenapple.itchupdater.database.updatecheck.InstallUpdateCheckResult
import ua.gardenapple.itchupdater.databinding.UpdatesItemBinding

class UpdatesListAdapter internal constructor(
    private val activity: Activity,
    val list: RecyclerView,
) : RecyclerView.Adapter<UpdatesListAdapter.UpdateCheckResultViewHolder>() {

    companion object {
        private const val LOGGING_TAG = "UpdatesAdapter"
    }
    
    private val context: Context = activity

    private val inflater: LayoutInflater = LayoutInflater.from(context)
    var availableUpdates = emptyList<InstallUpdateCheckResult>() // Cached copy of games
        internal set(value) {
            field = value
            notifyDataSetChanged()
        }


    inner class UpdateCheckResultViewHolder(val binding: UpdatesItemBinding)
        : RecyclerView.ViewHolder(binding.root)


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UpdateCheckResultViewHolder {
        val binding = UpdatesItemBinding.inflate(inflater, parent, false)
        val holder = UpdateCheckResultViewHolder(binding)
        return holder
    }

    override fun onBindViewHolder(holder: UpdateCheckResultViewHolder, position: Int) {
        val availableUpdate = availableUpdates[position]
        val updateCheckResult = availableUpdate.updateCheckResult
        val binding = holder.binding

        binding.updateCheckGameName.text = availableUpdate.gameName
        //Required for marquee animation
        binding.updateCheckGameName.isSelected = true
        binding.updateCheckUploadInfo.isSelected = true

        if (availableUpdate.packageName != null) {
            try {
                binding.appIcon.setImageDrawable(context.packageManager
                    .getApplicationIcon(availableUpdate.packageName))

                binding.appIcon.visibility = View.VISIBLE
                binding.gameThumbnail.visibility = View.INVISIBLE
                binding.gameThumbnailEmpty.visibility = View.INVISIBLE

            } catch (e: PackageManager.NameNotFoundException) {
                Log.w(LOGGING_TAG, "Could not find icon for package ${availableUpdate.packageName}", e)
                binding.appIcon.visibility = View.INVISIBLE
                binding.gameThumbnail.visibility = View.INVISIBLE
                binding.gameThumbnailEmpty.visibility = View.INVISIBLE
            }
        } else if (availableUpdate.thumbnailUrl != null) {
            binding.appIcon.visibility = View.INVISIBLE
            binding.gameThumbnail.visibility = View.VISIBLE
            binding.gameThumbnailEmpty.visibility = View.INVISIBLE

            Glide.with(context)
                .load(availableUpdate.thumbnailUrl)
                .override(UpdatesFragment.THUMBNAIL_WIDTH, UpdatesFragment.THUMBNAIL_HEIGHT)
                .into(binding.gameThumbnail)
        } else {
            binding.appIcon.visibility = View.INVISIBLE
            binding.gameThumbnail.visibility = View.INVISIBLE
            binding.gameThumbnailEmpty.visibility = View.VISIBLE
        }


        if (updateCheckResult.uploadID != null) {
            if (updateCheckResult.isInstalling) {
                binding.updateButton.visibility = View.INVISIBLE
                binding.updateProgressBar.visibility = View.VISIBLE
            } else {
                binding.updateButton.visibility = View.VISIBLE
                binding.updateProgressBar.visibility = View.INVISIBLE

                binding.updateButton.setOnClickListener { _ ->
                    (activity as MainActivity).launch {
                        GameDownloader.startUpdate(context, updateCheckResult)

                        val notificationManager =
                            context.getSystemService(Activity.NOTIFICATION_SERVICE) as NotificationManager
                        notificationManager.cancel(NOTIFICATION_TAG_UPDATE_CHECK, updateCheckResult.installationId)
                    }
                }
            }
            binding.updateCheckUploadInfo.text = context.getString(
                R.string.updates_size_and_name,
                updateCheckResult.newSize,
                updateCheckResult.newUploadName
            )
            binding.updateCheckUploadInfo2.visibility = View.VISIBLE
            binding.updateCheckUploadInfo2.text =
                if (availableUpdate.currentVersion == null) {
                    updateCheckResult.newTimestamp
                } else {
                    context.getString(
                        R.string.updates_version_change,
                        availableUpdate.currentVersion,
                        updateCheckResult.newVersionString
                    )
                }
        } else if (updateCheckResult.code == UpdateCheckResult.UPDATE_AVAILABLE) {
            binding.updateCheckUploadInfo.text = context.getString(R.string.updates_multiple)
            binding.updateCheckUploadInfo2.visibility = View.INVISIBLE

            binding.updateButton.visibility = View.VISIBLE
            binding.updateProgressBar.visibility = View.INVISIBLE

            binding.updateButton.setOnClickListener { _ ->
                val url = if (updateCheckResult.downloadPageUrl?.isPermanent == true)
                    updateCheckResult.downloadPageUrl.url
                else
                    availableUpdate.storeUrl

                if (activity is MainActivity) {
                    activity.browseUrl(url)
                } else {
                    val intent = Intent(
                        Intent.ACTION_VIEW, Uri.parse(url),
                        context, MainActivity::class.java
                    )
                    context.startActivity(intent)
                }
            }
        } else {
            binding.updateButton.visibility = View.GONE
            binding.updateProgressBar.visibility = View.INVISIBLE

            binding.updateCheckUploadInfo.text = when (updateCheckResult.code) {
                UpdateCheckResult.ERROR -> context.resources.getString(R.string.notification_update_fail)
                UpdateCheckResult.ACCESS_DENIED -> context.resources.getString(R.string.notification_update_access_denied)
                UpdateCheckResult.EMPTY -> context.resources.getString(R.string.notification_update_empty)
                else -> context.resources.getString(R.string.notification_update_unknown)
            }
            
            binding.updateCheckUploadInfo2.visibility = View.INVISIBLE
            binding.updateButton.visibility = View.GONE
        }

        binding.root.setOnClickListener {
            if (updateCheckResult.code == UpdateCheckResult.ERROR) {
                ACRA.getErrorReporter().handleException(Utils.ErrorReport(updateCheckResult.errorReport!!))
            } else if (activity is MainActivity) {
                activity.browseUrl(availableUpdate.storeUrl)
            } else {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(availableUpdate.storeUrl),
                    context, MainActivity::class.java)
                context.startActivity(intent)
            }
        }
    }

    override fun getItemCount(): Int = availableUpdates.size
}
