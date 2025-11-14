/******************************************************************************
 *                                                                            *
 * Copyright (C) 2021 by nekohasekai <contact-sagernet@sekai.icu>             *
 *                                                                            *
 * This program is free software: you can redistribute it and/or modify       *
 * it under the terms of the GNU General Public License as published by       *
 * the Free Software Foundation, either version 3 of the License, or          *
 *  (at your option) any later version.                                       *
 *                                                                            *
 * This program is distributed in the hope that it will be useful,            *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of             *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the              *
 * GNU General Public License for more details.                               *
 *                                                                            *
 * You should have received a copy of the GNU General Public License          *
 * along with this program. If not, see <http://www.gnu.org/licenses/>.       *
 *                                                                            *
 ******************************************************************************/

package com.vvpn.android.plugin

object PluginContract {

    const val ACTION_NATIVE_PLUGIN = "com.vvpn.android.plugin.ACTION_NATIVE_PLUGIN"
    const val EXTRA_ENTRY = "com.vvpn.android.plugin.EXTRA_ENTRY"
    const val METADATA_KEY_ID = "com.vvpn.android.plugin.id"
    const val METADATA_KEY_EXECUTABLE_PATH = "com.vvpn.android.plugin.executable_path"
    const val METHOD_GET_EXECUTABLE = "sagernet:getExecutable"

    const val COLUMN_PATH = "path"
    const val COLUMN_MODE = "mode"
    const val SCHEME = "plugin"
    const val AUTHORITY = "fr.husi"
}
