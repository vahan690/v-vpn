package com.vvpn.android.aidl

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
class Connections(
    val connections: List<Connection>,
) : Parcelable