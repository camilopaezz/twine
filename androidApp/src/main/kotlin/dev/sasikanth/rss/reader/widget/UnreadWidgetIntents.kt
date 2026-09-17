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

package dev.sasikanth.rss.reader.widget

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import dev.sasikanth.rss.reader.MainActivity
import dev.sasikanth.rss.reader.app.Screen
import dev.sasikanth.rss.reader.reader.ReaderScreenArgs

object UnreadWidgetIntents {

  fun reader(context: Context, postIndex: Int, postId: String): Intent {
    val uri =
      Screen.Reader(
          ReaderScreenArgs(
            postIndex = postIndex,
            postId = postId,
            fromScreen = ReaderScreenArgs.FromScreen.UnreadWidget,
          )
        )
        .toRoute()
        .toUri()
    return Intent(Intent.ACTION_VIEW, uri, context, MainActivity::class.java)
  }

  fun bookmarks(context: Context): Intent {
    return Intent(
      Intent.ACTION_VIEW,
      "twine://bookmarks".toUri(),
      context,
      MainActivity::class.java,
    )
  }
}
