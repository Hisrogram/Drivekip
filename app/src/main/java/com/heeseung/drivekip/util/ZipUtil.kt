package com.heeseung.drivekip.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ZipUtil {

    suspend fun createZip(files: List<File>, outputZipFile: File): File = withContext(Dispatchers.IO) {
        if (outputZipFile.exists()) {
            outputZipFile.delete()
        }

        ZipOutputStream(FileOutputStream(outputZipFile)).use { zipOut ->
            val buffer = ByteArray(8192)
            for (file in files) {
                if (!file.exists()) continue
                FileInputStream(file).use { fis ->
                    val zipEntry = ZipEntry(file.name)
                    zipOut.putNextEntry(zipEntry)
                    var length: Int
                    while (fis.read(buffer).also { length = it } >= 0) {
                        zipOut.write(buffer, 0, length)
                    }
                    zipOut.closeEntry()
                }
            }
        }
        outputZipFile
    }
}
