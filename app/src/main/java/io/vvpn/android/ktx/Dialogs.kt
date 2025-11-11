package io.vvpn.android.ktx

import android.content.Context
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.vvpn.android.R

fun Context.alert(text: String): AlertDialog {
    return MaterialAlertDialogBuilder(this).setTitle(R.string.error_title)
        .setMessage(text)
        .setPositiveButton(android.R.string.ok, null)
        .create()
}

fun Context.alertAndLog(e: Exception): AlertDialog {
    Logs.e(e)
    return alert(e.readableMessage)
}

fun Fragment.alert(text: String) = requireContext().alert(text)
