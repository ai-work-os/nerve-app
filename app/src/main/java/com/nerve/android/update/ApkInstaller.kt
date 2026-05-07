package com.nerve.android.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.nerve.android.util.Logger
import java.io.File

object ApkInstaller {
    fun launchInstall(context: Context, apk: File) {
        val authority = "${context.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, apk)
        Logger.debug(
            "ApkInstaller",
            "launch_install",
            mapOf("authority" to authority, "uri" to uri.toString(), "apk" to apk.path),
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
