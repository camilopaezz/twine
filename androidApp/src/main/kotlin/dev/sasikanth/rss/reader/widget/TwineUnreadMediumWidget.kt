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
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
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

class TwineUnreadMediumWidget : GlanceAppWidget() {

  override val sizeMode: SizeMode = SizeMode.Single

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

    updateAppWidgetState(context, id) { prefs ->
      if (prefs[PostCountKey] != snapshot.posts.size) {
        prefs[PostCountKey] = snapshot.posts.size
      }
    }

    provideContent {
      GlanceTheme {
        val prefs = currentState<Preferences>()
        val currentIndex = prefs[CurrentIndexKey] ?: 0

        WidgetContent(snapshot = snapshot, currentIndex = currentIndex)
      }
    }
  }

  @Composable
  private fun WidgetContent(snapshot: UnreadWidgetSnapshot, currentIndex: Int) {
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
          NoPostsMedium()
        } else {
          val safeIndex = if (currentIndex < snapshot.posts.size) currentIndex else 0
          val postSnapshot = snapshot.posts[safeIndex]

          PostContent(postSnapshot = postSnapshot, index = safeIndex, count = snapshot.posts.size)
        }
      }
    }
  }

  @Composable
  private fun PostContent(postSnapshot: UnreadWidgetPostSnapshot, index: Int, count: Int) {
    val context = LocalContext.current
    val post = postSnapshot.post

    Column(
      modifier =
        GlanceModifier.fillMaxSize()
          .clickable(actionStartActivity(UnreadWidgetIntents.reader(context, index, post.id)))
    ) {
      // Header
      Row(
        modifier =
          GlanceModifier.fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
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
        Spacer(GlanceModifier.width(16.dp))
        PaginationDots(index = index, count = count)
        Spacer(GlanceModifier.defaultWeight())
        NavigationButtons(index = index, count = count)
      }

      // Card Content
      Row(
        modifier = GlanceModifier.fillMaxWidth().defaultWeight().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(
          modifier =
            GlanceModifier.defaultWeight()
              .fillMaxHeight()
              .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
        ) {
          Spacer(GlanceModifier.defaultWeight())
          Text(
            text = post.title ?: "",
            style =
              TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
              ),
            maxLines = 3,
            modifier = GlanceModifier.fillMaxWidth(),
          )
          Spacer(GlanceModifier.defaultWeight())
          PublisherInfo(postSnapshot = postSnapshot)
        }

        val hasImage = !post.image.isNullOrBlank()
        if (hasImage) {
          PostImage(
            bitmap = postSnapshot.image,
            modifier = GlanceModifier.size(128.dp).fillMaxHeight(),
          )
        }
      }
    }
  }

  @Composable
  private fun PostImage(bitmap: Bitmap?, modifier: GlanceModifier = GlanceModifier) {
    Box(modifier = GlanceModifier.then(modifier).appWidgetInnerCornerRadius(4.dp)) {
      if (bitmap != null) {
        Image(
          provider = ImageProvider(bitmap),
          contentDescription = null,
          contentScale = ContentScale.Crop,
          modifier = GlanceModifier.fillMaxSize(),
        )
      }
    }
  }

  @Composable
  private fun PublisherInfo(postSnapshot: UnreadWidgetPostSnapshot) {
    val context = LocalContext.current
    val post = postSnapshot.post
    Row(verticalAlignment = Alignment.CenterVertically) {
      // Feed Icon
      Box(modifier = GlanceModifier.size(16.dp)) {
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
            fontSize = 12.sp,
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
              fontSize = 12.sp,
              fontWeight = FontWeight.Normal,
            ),
          maxLines = 1,
        )
      }

      if (post.readingTimeEstimate > 0) {
        Text(
          text =
            " • ${context.getString(R.string.unit_minutes, post.readingTimeEstimate)} read",
          style =
            TextStyle(
              color = GlanceTheme.colors.onSurfaceVariant,
              fontSize = 12.sp,
              fontWeight = FontWeight.Normal,
            ),
          maxLines = 1,
        )
      }
    }
  }

  @Composable
  private fun NavigationButtons(index: Int, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      if (index > 0) {
        Box(
          modifier =
            GlanceModifier.cornerRadius(99.dp)
              .size(32.dp)
              .clickable(actionRunCallback<MediumPreviousPostAction>()),
          contentAlignment = Alignment.Center,
        ) {
          Box(
            modifier =
              GlanceModifier.size(32.dp).background(GlanceTheme.colors.surface).cornerRadius(99.dp),
            contentAlignment = Alignment.Center,
          ) {
            Image(
              provider = ImageProvider(R.drawable.ic_chevron_left),
              contentDescription = null,
              colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurface),
              modifier = GlanceModifier.size(16.dp),
            )
          }
        }
      } else {
        Spacer(GlanceModifier.size(32.dp))
      }

      Spacer(GlanceModifier.width(8.dp))

      if (index < count - 1) {
        Box(
          modifier =
            GlanceModifier.cornerRadius(99.dp)
              .size(32.dp)
              .clickable(actionRunCallback<MediumNextPostAction>()),
          contentAlignment = Alignment.Center,
        ) {
          Box(
            modifier =
              GlanceModifier.size(32.dp).background(GlanceTheme.colors.surface).cornerRadius(99.dp),
            contentAlignment = Alignment.Center,
          ) {
            Image(
              provider = ImageProvider(R.drawable.ic_chevron_right),
              contentDescription = null,
              colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurface),
              modifier = GlanceModifier.size(16.dp),
            )
          }
        }
      } else {
        Spacer(GlanceModifier.size(32.dp))
      }
    }
  }

  @Composable
  private fun PaginationDots(index: Int, count: Int) {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
      for (i in 0 until minOf(count, 5)) {
        val isSelected = i == (index % 5)
        val indicatorColor =
          if (isSelected) {
            GlanceTheme.colors.onSurface.getColor(context)
          } else {
            GlanceTheme.colors.onSurface.getColor(context).copy(alpha = 0.4f)
          }

        Box(modifier = GlanceModifier.size(6.dp).background(indicatorColor).cornerRadius(99.dp)) {}
        if (i < minOf(count, 5) - 1) {
          Spacer(GlanceModifier.width(4.dp))
        }
      }
    }
  }

  @Composable
  private fun NoPostsMedium() {
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
    private const val NUMBER_OF_UNREAD_POSTS_IN_WIDGET = 5
    private const val SNAPSHOT_KIND = "medium"
    private const val IMAGE_PX = 300
    private const val ICON_PX = 24
    val CurrentIndexKey = intPreferencesKey("medium_current_index")
    val PostCountKey = intPreferencesKey("medium_post_count")
  }
}

class MediumNextPostAction : ActionCallback {
  override suspend fun onAction(
    context: Context,
    glanceId: GlanceId,
    parameters: ActionParameters,
  ) {
    updateAppWidgetState(context, glanceId) { prefs ->
      val count = prefs[TwineUnreadMediumWidget.PostCountKey] ?: 0
      if (count == 0) return@updateAppWidgetState
      val currentIndex = prefs[TwineUnreadMediumWidget.CurrentIndexKey] ?: 0
      prefs[TwineUnreadMediumWidget.CurrentIndexKey] = (currentIndex + 1) % count
    }
    TwineUnreadMediumWidget().update(context, glanceId)
  }
}

class MediumPreviousPostAction : ActionCallback {
  override suspend fun onAction(
    context: Context,
    glanceId: GlanceId,
    parameters: ActionParameters,
  ) {
    updateAppWidgetState(context, glanceId) { prefs ->
      val currentIndex = prefs[TwineUnreadMediumWidget.CurrentIndexKey] ?: 0
      if (currentIndex > 0) {
        prefs[TwineUnreadMediumWidget.CurrentIndexKey] = currentIndex - 1
      }
    }
    TwineUnreadMediumWidget().update(context, glanceId)
  }
}
