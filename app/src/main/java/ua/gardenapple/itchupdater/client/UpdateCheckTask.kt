package ua.gardenapple.itchupdater.client

import android.os.AsyncTask
import ua.gardenapple.itchupdater.GameStoreInfo
import ua.gardenapple.itchupdater.GameVersionInfo

typealias UpdateCheckTask = AsyncTask<GameStoreInfo, Void, GameVersionInfo>