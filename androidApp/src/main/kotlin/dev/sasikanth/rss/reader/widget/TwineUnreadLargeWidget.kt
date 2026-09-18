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
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.CircularProgressIndicator
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.itemsIndexed
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import dev.sasikanth.rss.reader.R
import dev.sasikanth.rss.reader.ReaderApplication
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class TwineUnreadLargeWidget : GlanceAppWidget() {

  override val sizeMode: SizeMode = SizeMode.Exact

  override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

  override suspend fun provideGlance(context: Context, id: GlanceId) {
    val applicationComponent = (context.applicationContext as ReaderApplication).appComponent
    val widgetDataRepository = applicationComponent.widgetDataRepository
    val imageLoader = applicationComponent.imageLoader
    val billingHandler = applicationComponent.billingHandler

    val postIds =
      widgetDataRepository.unreadPostsBlocking(NUMBER_OF_UNREAD_POSTS_IN_WIDGET).map { it.id }
    val snapshot =
      UnreadWidgetSnapshotCache.get(SNAPSHOT_KIND, postIds)
        ?: loadUnreadWidgetSnapshot(
            context = context,
            widgetDataRepository = widgetDataRepository,
            imageLoader = imageLoader,
            billingHandler = billingHandler,
            postCount = NUMBER_OF_UNREAD_POSTS_IN_WIDGET,
            imagePx = IMAGE_PX,
            iconPx = ICON_PX,
          )
          .also { UnreadWidgetSnapshotCache.put(SNAPSHOT_KIND, postIds, it) }

    provideContent {
      GlanceTheme {
        val prefs = currentState<Preferences>()
        val isRefreshing = prefs[RefreshingKey] == true
        WidgetContent(snapshot = snapshot, isRefreshing = isRefreshing)
      }
    }
  }

  @Composable
  private fun WidgetContent(snapshot: UnreadWidgetSnapshot, isRefreshing: Boolean) {
    if (!snapshot.isSubscribed) {
      RequireTwinePremium()
    } else {
      Box(
        modifier =
          GlanceModifier.fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .appWidgetInnerCornerRadius(28.dp)
            .padding(4.dp)
      ) {
        if (snapshot.posts.isEmpty()) {
          NoPostsLarge()
        } else {
          Column(modifier = GlanceModifier.fillMaxSize()) {
            Header(isRefreshing = isRefreshing)

            LazyColumn {
              itemsIndexed(snapshot.posts) { index, postSnapshot ->
                PostItem(postSnapshot = postSnapshot, index = index)
              }
            }
          }
        }
      }
    }
  }

  @Composable
  private fun Header(isRefreshing: Boolean) {
    val context = LocalContext.current
    Row(
      modifier =
        GlanceModifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = context.getString(R.string.widget_latest),
        style =
          TextStyle(
            color = GlanceTheme.colors.onSurface,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
          ),
      )

      Spacer(GlanceModifier.defaultWeight())

      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
          modifier =
            GlanceModifier.cornerRadius(99.dp)
              .size(32.dp)
              .clickable(actionRunCallback<LargeRefreshAction>()),
          contentAlignment = Alignment.Center,
        ) {
          Box(
            modifier =
              GlanceModifier.size(32.dp).background(GlanceTheme.colors.surface).cornerRadius(99.dp),
            contentAlignment = Alignment.Center,
          ) {
            if (isRefreshing) {
              CircularProgressIndicator(
                color = GlanceTheme.colors.onSurface,
                modifier = GlanceModifier.size(24.dp),
              )
            }
            Image(
              provider = ImageProvider(R.drawable.ic_refresh),
              contentDescription = null,
              colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurface),
              modifier = GlanceModifier.size(16.dp),
            )
          }
        }

        Spacer(GlanceModifier.width(8.dp))

        Box(
          modifier =
            GlanceModifier.cornerRadius(99.dp)
              .size(32.dp)
              .clickable(actionStartActivity(UnreadWidgetIntents.bookmarks(context))),
          contentAlignment = Alignment.Center,
        ) {
          Box(
            modifier =
              GlanceModifier.size(32.dp).background(GlanceTheme.colors.surface).cornerRadius(99.dp),
            contentAlignment = Alignment.Center,
          ) {
            Image(
              provider = ImageProvider(R.drawable.ic_bookmark),
              contentDescription = null,
              colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurface),
              modifier = GlanceModifier.size(16.dp),
            )
          }
        }
      }
    }
  }

  @Composable
  private fun PostItem(postSnapshot: UnreadWidgetPostSnapshot, index: Int) {
    val context = LocalContext.current
    val post = postSnapshot.post
    Row(
      modifier =
        GlanceModifier.fillMaxWidth()
          .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
          .clickable(actionStartActivity(UnreadWidgetIntents.reader(context, index, post.id))),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = GlanceModifier.defaultWeight()) {
        Text(
          text = post.title ?: "",
          style =
            TextStyle(
              color = GlanceTheme.colors.onSurface,
              fontSize = 12.sp,
              fontWeight = FontWeight.Medium,
            ),
          maxLines = 2,
          modifier = GlanceModifier.fillMaxWidth().height(32.dp),
        )
        Spacer(GlanceModifier.height(8.dp))
        PublisherInfo(postSnapshot = postSnapshot)
      }

      Spacer(GlanceModifier.width(12.dp))

      val hasImage = !post.image.isNullOrBlank()
      if (hasImage) {
        PostImage(bitmap = postSnapshot.image)
      } else {
        Box(modifier = GlanceModifier.size(56.dp)) {}
      }
    }
  }

  @Composable
  private fun PostImage(bitmap: Bitmap?) {
    if (bitmap != null) {
      Image(
        provider = ImageProvider(bitmap),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = GlanceModifier.size(56.dp).cornerRadius(14.dp),
      )
    }
  }

  @Composable
  private fun PublisherInfo(postSnapshot: UnreadWidgetPostSnapshot) {
    val context = LocalContext.current
    val post = postSnapshot.post
    Row(verticalAlignment = Alignment.CenterVertically) {
      // Feed Icon
      Box(modifier = GlanceModifier.size(12.dp)) {
        val feedIcon = postSnapshot.feedIcon
        if (feedIcon != null) {
          Image(
            provider = ImageProvider(feedIcon),
            contentDescription = null,
            modifier = GlanceModifier.fillMaxSize().cornerRadius(2.dp),
          )
        } else {
          Image(
            provider = ImageProvider(R.drawable.ic_newsstand),
            contentDescription = null,
            colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurface),
            modifier = GlanceModifier.fillMaxSize(),
          )
        }
      }
      Spacer(GlanceModifier.width(4.dp))
      Text(
        text = post.feedName ?: "",
        style =
          TextStyle(
            color = GlanceTheme.colors.onSurface,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
          ),
        maxLines = 1,
      )

      val relativeTime = post.postedOn.formatRelativeTime(context)
      if (relativeTime.isNotBlank()) {
        Text(
          text = " • $relativeTime",
          style =
            TextStyle(
              color = GlanceTheme.colors.onSurfaceVariant,
              fontSize = 10.sp,
              fontWeight = FontWeight.Normal,
            ),
          maxLines = 1,
        )
      }

      if (post.readingTimeEstimate > 0) {
        Text(
          text = " • ${context.getString(R.string.unit_minutes, post.readingTimeEstimate)} read",
          style =
            TextStyle(
              color = GlanceTheme.colors.onSurfaceVariant,
              fontSize = 10.sp,
              fontWeight = FontWeight.Normal,
            ),
          maxLines = 1,
        )
      }
    }
  }

  @Composable
  private fun NoPostsLarge() {
    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Image(
          provider = ImageProvider(R.drawable.ic_newsstand),
          contentDescription = null,
          modifier = GlanceModifier.size(48.dp),
        )
        Spacer(GlanceModifier.height(8.dp))
        Text(
          text = LocalContext.current.getString(R.string.widget_no_posts),
          style =
            TextStyle(
              color = GlanceTheme.colors.onSurface,
              fontSize = 14.sp,
              fontWeight = FontWeight.Medium,
            ),
        )
      }
    }
  }

  companion object {
    private const val NUMBER_OF_UNREAD_POSTS_IN_WIDGET = 15
    private const val SNAPSHOT_KIND = "large"
    private const val IMAGE_PX = 112
    private const val ICON_PX = 24
    val RefreshingKey = booleanPreferencesKey("large_refreshing")
  }
}

class LargeRefreshAction : ActionCallback {
  override suspend fun onAction(
    context: Context,
    glanceId: GlanceId,
    parameters: ActionParameters,
  ) {
    val appContext = context.applicationContext
    updateAppWidgetState(appContext, glanceId) { prefs ->
      prefs[TwineUnreadLargeWidget.RefreshingKey] = true
    }
    TwineUnreadLargeWidget().update(appContext, glanceId)

    val applicationComponent = (appContext as ReaderApplication).appComponent
    applicationComponent.syncCoordinator.triggerPull()

    refreshTimeoutScope.launch {
      delay(REFRESH_TIMEOUT_MS)
      try {
        val prefs = getAppWidgetState(appContext, PreferencesGlanceStateDefinition, glanceId)
        if (prefs[TwineUnreadLargeWidget.RefreshingKey] == true) {
          updateAppWidgetState(appContext, glanceId) {
            it[TwineUnreadLargeWidget.RefreshingKey] = false
          }
          TwineUnreadLargeWidget().update(appContext, glanceId)
        }
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {}
    }
  }

  private companion object {
    val refreshTimeoutScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    const val REFRESH_TIMEOUT_MS = 30_000L
  }
}
