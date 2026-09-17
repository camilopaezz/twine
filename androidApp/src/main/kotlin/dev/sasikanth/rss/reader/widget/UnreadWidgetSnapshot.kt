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
import coil3.ImageLoader
import coil3.request.CachePolicy
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import dev.sasikanth.rss.reader.R
import dev.sasikanth.rss.reader.billing.BillingHandler
import dev.sasikanth.rss.reader.core.model.local.WidgetPost
import dev.sasikanth.rss.reader.data.repository.WidgetDataRepository
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

data class UnreadWidgetPostSnapshot(val post: WidgetPost, val image: Bitmap?, val feedIcon: Bitmap?)

data class UnreadWidgetSnapshot(
  val isSubscribed: Boolean,
  val posts: List<UnreadWidgetPostSnapshot>,
)

object UnreadWidgetSnapshotCache {
  private data class Key(val kind: String, val postIds: List<String>)

  private val lock = Any()
  private val snapshots = mutableMapOf<Key, UnreadWidgetSnapshot>()

  fun get(kind: String, postIds: List<String>): UnreadWidgetSnapshot? {
    synchronized(lock) {
      return snapshots[Key(kind, postIds)]
    }
  }

  fun put(kind: String, postIds: List<String>, snapshot: UnreadWidgetSnapshot) {
    synchronized(lock) { snapshots[Key(kind, postIds.toList())] = snapshot }
  }

  fun invalidate() {
    synchronized(lock) { snapshots.clear() }
  }
}

@Volatile private var lastKnownSubscribed: Boolean? = null

suspend fun loadUnreadWidgetSnapshot(
  context: Context,
  widgetDataRepository: WidgetDataRepository,
  imageLoader: ImageLoader,
  billingHandler: BillingHandler,
  postCount: Int,
  imagePx: Int,
  iconPx: Int,
): UnreadWidgetSnapshot {
  return coroutineScope {
    val subscribedDeferred = async { resolveIsSubscribed(billingHandler) }
    val posts = widgetDataRepository.unreadPostsBlocking(postCount)

    val imageUrls = LinkedHashSet<String>()
    val iconUrls = LinkedHashSet<String>()
    for (post in posts) {
      val image = post.image
      if (!image.isNullOrBlank()) {
        imageUrls.add(image)
      }
      val icon = post.feedIcon
      if (!icon.isNullOrBlank()) {
        iconUrls.add(icon)
      }
    }

    val imagesDeferred =
      imageUrls.associateWith { url -> async { loadBitmap(context, imageLoader, url, imagePx) } }
    val iconsDeferred =
      iconUrls.associateWith { url -> async { loadBitmap(context, imageLoader, url, iconPx) } }

    val images = imagesDeferred.mapValues { it.value.await() }
    val icons = iconsDeferred.mapValues { it.value.await() }

    UnreadWidgetSnapshot(
      isSubscribed = subscribedDeferred.await(),
      posts =
        posts.map { post ->
          UnreadWidgetPostSnapshot(
            post = post,
            image = post.image?.let { images[it] },
            feedIcon = post.feedIcon?.let { icons[it] },
          )
        },
    )
  }
}

internal fun Instant.formatRelativeTime(context: Context): String {
  val now = Clock.System.now()
  val duration = now - this
  val seconds = duration.inWholeSeconds
  val days = duration.inWholeDays

  return when {
    seconds < 60 -> context.getString(R.string.unit_seconds)
    seconds < 3600 -> context.getString(R.string.unit_minutes, duration.inWholeMinutes.toInt())
    seconds < 86400 -> context.getString(R.string.unit_hours, duration.inWholeHours.toInt())
    days < 7 -> context.getString(R.string.unit_days, days.toInt())
    else -> ""
  }
}

private suspend fun resolveIsSubscribed(billingHandler: BillingHandler): Boolean {
  return try {
    val subscribed = withTimeoutOrNull(SUBSCRIPTION_TIMEOUT_MS) { billingHandler.isSubscribed() }
    if (subscribed != null) {
      lastKnownSubscribed = subscribed
      subscribed
    } else {
      lastKnownSubscribed ?: true
    }
  } catch (e: CancellationException) {
    throw e
  } catch (e: Exception) {
    lastKnownSubscribed ?: true
  }
}

private suspend fun loadBitmap(
  context: Context,
  imageLoader: ImageLoader,
  url: String,
  sizePx: Int,
): Bitmap? {
  return withContext(Dispatchers.IO) {
    try {
      val request =
        ImageRequest.Builder(context)
          .size(sizePx)
          .data(url)
          .memoryCachePolicy(CachePolicy.ENABLED)
          .diskCachePolicy(CachePolicy.ENABLED)
          .build()
      when (val result = imageLoader.execute(request)) {
        is ErrorResult -> null
        is SuccessResult -> result.image.toBitmap()
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      null
    }
  }
}

private const val SUBSCRIPTION_TIMEOUT_MS = 1_000L
