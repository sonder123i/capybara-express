package dev.showcode.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import dev.showcode.R
import dev.showcode.ui.theme.InkFaint
import dev.showcode.ui.theme.ShowCodeShapes
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
    var failed by remember(url) { mutableStateOf(false) }
    /* 演示模式专用：`demo:` 指向打包进 APK 的本地图，同步取图、零加载态，
       录制时任何一帧都不会出现「无图」或闪一下灰底。 */
    demoDrawableOf(url)?.let { res ->
        Box(
            modifier = modifier
                .size(size)
                .clip(ShowCodeShapes.Thumb)
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
    /* 交给 Coil 管：它自带内存 + 磁盘两级缓存、同一 URL 的请求去重、
       以及「滚出屏幕就取消」。这三件事自写的 ThumbnailLoader 都没做——
       同一张图每次冷启动都要重下，列表快滚时旧请求还在跑。 */
    Box(
        modifier = modifier
            .size(size)
            .clip(ShowCodeShapes.Thumb)
            .background(SurfaceInset),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(url)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            /* 失败时不画破图标——破图标会让人以为是加载坏了。占位文字单独挂一层，
               所以这里只记状态、不画东西。 */
            onState = { state ->
                failed = state is coil3.compose.AsyncImagePainter.State.Error
            },
        )
        if (failed) {
            Text(
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
