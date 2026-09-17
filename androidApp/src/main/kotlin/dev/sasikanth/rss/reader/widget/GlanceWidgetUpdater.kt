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
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object GlanceWidgetUpdater {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private val mutex = Mutex()
  private var dirty = false
  private var inFlight = false

  fun update(context: Context) {
    val appContext = context.applicationContext
    scope.launch {
      val shouldStart =
        mutex.withLock {
          dirty = true
          if (inFlight) {
            false
          } else {
            inFlight = true
            true
          }
        }
      if (!shouldStart) return@launch

      try {
        do {
          delay(DEBOUNCE_MS)
          mutex.withLock { dirty = false }
          runDataPass(appContext)
        } while (
          mutex.withLock {
            if (dirty) {
              true
            } else {
              inFlight = false
              false
            }
          }
        )
      } catch (e: CancellationException) {
        mutex.withLock { inFlight = false }
        throw e
      } catch (e: Exception) {
        Log.e(TAG, "Unread widget update failed", e)
        mutex.withLock { inFlight = false }
      }
    }
  }

  private suspend fun runDataPass(context: Context) {
    UnreadWidgetSnapshotCache.invalidate()
    val manager = GlanceAppWidgetManager(context)
    updateWidget(context, manager, TwineUnreadSmallWidget::class.java, TwineUnreadSmallWidget())
    updateWidget(context, manager, TwineUnreadMediumWidget::class.java, TwineUnreadMediumWidget())
    updateLargeWidget(context, manager)
  }

  private suspend fun updateWidget(
    context: Context,
    manager: GlanceAppWidgetManager,
    widgetClass: Class<out GlanceAppWidget>,
    widget: GlanceAppWidget,
  ) {
    try {
      if (manager.getGlanceIds(widgetClass).isEmpty()) return
      widget.updateAll(context)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Failed to update ${widgetClass.simpleName}", e)
    }
  }

  private suspend fun updateLargeWidget(context: Context, manager: GlanceAppWidgetManager) {
    try {
      val glanceIds = manager.getGlanceIds(TwineUnreadLargeWidget::class.java)
      if (glanceIds.isEmpty()) return
      for (glanceId in glanceIds) {
        try {
          updateAppWidgetState(context, glanceId) { prefs ->
            prefs[TwineUnreadLargeWidget.RefreshingKey] = false
          }
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          Log.e(TAG, "Failed to clear large widget refreshing state", e)
        }
      }
      TwineUnreadLargeWidget().updateAll(context)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Failed to update TwineUnreadLargeWidget", e)
    }
  }

  private const val TAG = "GlanceWidgetUpdater"
  private const val DEBOUNCE_MS = 400L
}
