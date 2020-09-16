package com.github.bhlangonijr.flubber.action

import com.github.bhlangonijr.flubber.script.ExtensionNotSupportedException
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.*


class Actions {

    companion object {

        fun from(url: String): Action {

            val resource = URL(url)
            val file = resource.file.toLowerCase()
            return when {
                file.endsWith("js") -> JavascriptAction(readFromUrl(resource))
                file.endsWith("py") -> PythonAction(readFromUrl(resource))
                else -> throw ExtensionNotSupportedException("File extension not supported $url")
            }
        }

        private fun readFromUrl(url: URL): String {
            Scanner(
                url.openStream(),
                StandardCharsets.UTF_8.toString()
            ).use { scanner ->
                scanner.useDelimiter("\\A")
                return if (scanner.hasNext()) scanner.next() else ""
            }
        }
    }
}