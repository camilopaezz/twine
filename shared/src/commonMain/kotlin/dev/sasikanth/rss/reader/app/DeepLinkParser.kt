/*
 * Copyright 2026 Sasikanth Miriyampalli
 *
 * Licensed under the GPL, Version 3.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.gnu.org/licenses/gpl-3.0.en.html
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package dev.sasikanth.rss.reader.app

import dev.sasikanth.rss.reader.reader.ReaderScreenArgs
import kotlinx.serialization.json.Json

object DeepLinkParser {

  fun parse(uriString: String): Screen? {
    if (uriString == "twine://add") {
      return Screen.AddFeed
    }
    if (uriString == "twine://bookmarks") {
      return Screen.Main(startTab = Screen.Main.TAB_BOOKMARKS)
    }
    if (uriString.startsWith("twine://reader/")) {
      try {
        val jsonStr = uriString.removePrefix("twine://reader/")
        val decodedJson = percentDecodeUtf8(jsonStr)
        val args = Json.decodeFromString(ReaderScreenArgs.serializer(), decodedJson)
        return Screen.Reader(args)
      } catch (e: Exception) {
        // Fallback or ignore
      }
    }
    return null
  }

  private fun percentDecodeUtf8(value: String): String {
    if ('%' !in value) return value

    val out = ByteArray(value.length * 4)
    var o = 0
    var i = 0
    while (i < value.length) {
      val c = value[i]
      if (c == '%' && i + 2 < value.length) {
        val decoded = value.substring(i + 1, i + 3).toIntOrNull(16)
        if (decoded != null) {
          out[o++] = decoded.toByte()
          i += 3
          continue
        }
      }
      val code = c.code
      if (code < 0x80) {
        out[o++] = code.toByte()
      } else {
        val utf8 = c.toString().encodeToByteArray()
        utf8.copyInto(out, o)
        o += utf8.size
      }
      i++
    }
    return out.decodeToString(endIndex = o)
  }
}
