package dev.showcode.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.showcode.R
import dev.showcode.ui.parcels.ThumbnailLoader
import dev.showcode.ui.theme.InkFaint
import dev.showcode.ui.theme.ShowCodeText
import dev.showcode.ui.theme.SurfaceInset

/**
 * 商品缩略图。尺寸由调用方钉死（首页/现场 44–48dp，详情 76dp），
 * 加载中与失败都用同一块浅灰，**尺寸不变**所以不会引起布局跳动。
 *
 * 没有 URL 时调用方不渲染这个组件——「没有商品数据就不显示，也不留空框」。
 */
@Composable
fun GoodsThumbnail(
    url: String,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    /* 演示模式专用：`demo:` 指向打包进 APK 的本地图，同步取图、零加载态，
       录制时任何一帧都不会出现「无图」或闪一下灰底。 */
    demoDrawableOf(url)?.let { res ->
        Box(
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(10.dp))
                .background(SurfaceInset),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(res),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        return
    }
    val state by produceState<ThumbState>(initialValue = ThumbState.Loading, key1 = url) {
        val bitmap = ThumbnailLoader.load(url)
        value = if (bitmap != null) ThumbState.Ready(bitmap) else ThumbState.Failed
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceInset),
        contentAlignment = Alignment.Center,
    ) {
        when (val current = state) {
            is ThumbState.Ready -> Image(
                bitmap = current.bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            ThumbState.Loading -> Unit
            ThumbState.Failed -> Text(
                text = stringResource(R.string.goods_no_image),
                style = ShowCodeText.Caption,
                color = InkFaint,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** 演示素材的本地映射。只有这里认识的 key 才走本地图，其它仍按 URL 加载。 */
private fun demoDrawableOf(url: String): Int? = when (url) {
    "demo:ramen" -> R.drawable.demo_goods_ramen
    "demo:tissues" -> R.drawable.demo_goods_tissues
    "demo:coffee" -> R.drawable.demo_goods_coffee
    "demo:phone_case" -> R.drawable.demo_goods_phone_case
    "demo:snack" -> R.drawable.demo_goods_snack
    "demo:shampoo" -> R.drawable.demo_goods_shampoo
    else -> null
}

private sealed interface ThumbState {
    data object Loading : ThumbState
    data object Failed : ThumbState
    data class Ready(val bitmap: android.graphics.Bitmap) : ThumbState
}
