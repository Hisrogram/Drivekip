package com.heeseung.drivekip.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

object ShareUtil {

    private const val FILE_PROVIDER_AUTHORITY = "com.heeseung.drivekip.fileprovider"

    fun shareSingleFile(context: Context, file: File, mimeType: String, title: String = "파일 공유") {
        val uri: Uri = FileProvider.getUriForFile(context, FILE_PROVIDER_AUTHORITY, file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, title))
    }

    fun shareMultipleFiles(context: Context, files: List<File>, title: String = "파일 묶음 공유") {
        val uris = ArrayList<Uri>()
        for (f in files) {
            val uri = FileProvider.getUriForFile(context, FILE_PROVIDER_AUTHORITY, f)
            uris.add(uri)
        }

        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, title))
    }
}
