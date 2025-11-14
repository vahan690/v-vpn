package com.vvpn.android.bg

import java.io.Closeable

interface AbstractInstance : Closeable {

    fun launch()

}