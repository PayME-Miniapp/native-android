package com.payme.sdk.utils

import java.io.File

internal object KycImageStore {
    val names = setOf("kycFrontIdCard.jpeg", "kycBackIdCard.jpeg", "kycFace1.jpeg", "kycFace2.jpeg", "kycFace3.jpeg", "authenFace.jpeg")
    private fun directory(filesDir: File) = File(filesDir, "payme-private-kyc")
    fun file(filesDir: File, name: String): File {
        require(name in names)
        return File(directory(filesDir).apply { mkdirs() }, name)
    }
    fun resolve(filesDir: File, path: String): File? {
        val name = path.removePrefix("/images/")
        return if (path == "/images/$name" && name in names) file(filesDir, name) else null
    }
    fun clear(filesDir: File) {
        names.forEach { File(directory(filesDir), it).delete() }
    }
    fun clearLegacy(root: File) {
        names.forEach { File(root, "images/$it").delete() }
    }
}
