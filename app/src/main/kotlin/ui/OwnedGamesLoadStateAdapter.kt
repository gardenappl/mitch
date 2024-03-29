package garden.appl.mitch.ui

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.paging.LoadState
import androidx.paging.LoadStateAdapter
import androidx.recyclerview.widget.RecyclerView
import garden.appl.mitch.ItchWebsiteUtils
import garden.appl.mitch.R
import garden.appl.mitch.client.ItchAccessDeniedException
import garden.appl.mitch.databinding.OwnedItemLoadStateFooterBinding

class OwnedGamesLoadStateAdapter(private val retry: () -> Unit) :
    LoadStateAdapter<OwnedGamesLoadStateAdapter.OwnedGamesLoadStateViewHolder>() {

    class OwnedGamesLoadStateViewHolder(
        val binding: OwnedItemLoadStateFooterBinding
    ) : RecyclerView.ViewHolder(binding.root)

    companion object {
        fun bind(
            binding: OwnedItemLoadStateFooterBinding,
            context: Context,
            loadState: LoadState,
            retry: () -> Unit
        ) {
            binding.logInButton.isVisible = false
            if (loadState is LoadState.Error) {
                if (loadState.error is ItchAccessDeniedException) {
                    binding.logInButton.visibility = View.VISIBLE
                    binding.errorMsg.text =
                        context.resources.getString(R.string.library_not_logged_in)
                } else {
                    binding.errorMsg.text = loadState.error.localizedMessage
                }
            }
            binding.errorMsg.isVisible = loadState is LoadState.Error
            binding.progressBar.isVisible = loadState is LoadState.Loading
            binding.retryButton.isVisible = loadState is LoadState.Error

            binding.retryButton.setOnClickListener {
                retry()
            }
            binding.logInButton.setOnClickListener {
                val intent = Intent(
                    Intent.ACTION_VIEW, ItchWebsiteUtils.LOGIN_PAGE_URI, context,
                    MainActivity::class.java
                )
                context.startActivity(intent)
            }
        }
    }

    override fun onBindViewHolder(holder: OwnedGamesLoadStateViewHolder, loadState: LoadState) {
        bind(holder.binding, holder.itemView.context, loadState, retry)
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        loadState: LoadState
    ): OwnedGamesLoadStateViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.owned_item_load_state_footer, parent, false)
        val binding = OwnedItemLoadStateFooterBinding.bind(view)
        bind(binding, parent.context, loadState, retry)
        return OwnedGamesLoadStateViewHolder(binding)
    }
}