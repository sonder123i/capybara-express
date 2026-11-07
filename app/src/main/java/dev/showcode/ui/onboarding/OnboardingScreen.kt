package dev.showcode.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.showcode.R
import dev.showcode.ui.theme.AccentInk
import dev.showcode.ui.theme.Hairline
import dev.showcode.ui.theme.Ink
import dev.showcode.ui.theme.InkMuted
import dev.showcode.ui.theme.InkSecondary
import dev.showcode.ui.theme.MotionDuration
import dev.showcode.ui.theme.MotionEasing
import dev.showcode.ui.theme.MotionTokens
import dev.showcode.ui.theme.OnsiteSurface
import dev.showcode.ui.theme.ShowCodeShapes
import dev.showcode.ui.theme.ShowCodeText
import dev.showcode.ui.theme.SurfaceCard
import dev.showcode.ui.theme.UrgencyDueSoon
import dev.showcode.ui.theme.UrgencySafe
import dev.showcode.ui.theme.environmentBackground
import dev.showcode.ui.theme.rememberReducedMotion

/**
 * 首启引导。
 *
 * 只做两件事：**让人看见价值**、**让他自己决定接不接平台**。
 * 短信 / 通知 / 桌面组件都不在这里要——它们是补充手段，各自该在第一次真的用得上时
 * 从首页或设置里出现；把四件事塞进一条五步流水线，只会让第一次打开变成一份问卷。
 *
 * 交互借的是多邻国那套「一个人、一步一步、每一步都有回应」：
 * - 卡皮（这个 App 的杜欧）说一句，主标题说事，示例卡把价值直接摆出来；
 * - 一个屏只做一个决定：两个大选择卡，不是三颗文字按钮；
 * - 顶部一条会走的进度条（1/2 → 2/2），不是「第 1 / 5 步」；
 * - 按下有回弹 + 轻触感，绑好平台回来时「已绑定」会浮出来。
 *
 * 不搬的是积分 / 连胜 / 排行榜：这是一个一周用几次的取件工具，
 * 把「打开它」做成要维护的习惯，只会变成骚扰。
 */
enum class OnboardStep { WELCOME, CONNECT }

@Composable
fun OnboardingScreen(
    step: OnboardStep,
    accountCount: Int,
    onStep: (OnboardStep) -> Unit,
    onEnterDemo: () -> Unit,
    onOpenAccounts: () -> Unit,
    onFinish: () -> Unit,
) {
    val reduced = rememberReducedMotion()
    /* 进度条放在两屏之上：换屏时它自己往前走，而不是跟着页面一起滑走。 */
    val target = if (step == OnboardStep.WELCOME) 0.5f else 1f
    val progress by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(MotionDuration.Medium, easing = MotionEasing.FastOutSlowIn),
        label = "onboarding-progress",
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .environmentBackground()
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        StepProgress(progress)
        AnimatedContent(
            targetState = step,
            modifier = Modifier.weight(1f),
            transitionSpec = {
                if (reduced) {
                    fadeIn(tween(MotionDuration.Medium, easing = MotionEasing.Standard)) togetherWith
                        fadeOut(tween(MotionDuration.Fast, easing = MotionEasing.Standard))
                } else {
                    val forward = targetState.ordinal > initialState.ordinal
                    val enter = slideInHorizontally(
                        animationSpec = tween(MotionDuration.Medium, easing = MotionEasing.FastOutSlowIn),
                    ) { width -> if (forward) width / 6 else -width / 6 } +
                        fadeIn(tween(MotionDuration.Medium, easing = MotionEasing.Standard))
                    val exit = slideOutHorizontally(
                        animationSpec = tween(MotionDuration.Medium, easing = MotionEasing.FastOutSlowIn),
                    ) { width -> if (forward) -width / 6 else width / 6 } +
                        fadeOut(tween(MotionDuration.Fast, easing = MotionEasing.Standard))
                    enter togetherWith exit
                }
            },
            label = "onboarding-step",
        ) { current ->
            when (current) {
                OnboardStep.WELCOME -> Welcome(
                    onBind = { onStep(OnboardStep.CONNECT) },
                    onDemo = onEnterDemo,
                    onSkip = onFinish,
                )
                OnboardStep.CONNECT -> Connect(
                    accountCount = accountCount,
                    onBind = onOpenAccounts,
                    onFinish = onFinish,
                )
            }
        }
    }
}

@Composable
private fun StepProgress(fraction: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 10.dp)
            .height(4.dp)
            .background(Hairline, ShowCodeShapes.Pill),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(AccentInk, ShowCodeShapes.Pill),
        )
    }
}

/* ── 第一屏：价值 ───────────────────────────────────────────────────── */

@Composable
private fun Welcome(onBind: () -> Unit, onDemo: () -> Unit, onSkip: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 12.dp),
        ) {
            Brand()
            Spacer(Modifier.height(14.dp))
            MascotLine()
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.ob_headline),
                style = ShowCodeText.EmptyState,
                color = Ink,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.ob_sub),
                style = ShowCodeText.Body,
                color = InkSecondary,
            )
            Spacer(Modifier.height(22.dp))
            SamplePickupCard()
        }
        Column(modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 10.dp)) {
            Row(
                modifier = Modifier.height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ChoiceCard(
                    title = stringResource(R.string.ob_cta_bind),
                    subtitle = stringResource(R.string.ob_choice_bind_sub),
                    primary = true,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    onClick = onBind,
                )
                ChoiceCard(
                    title = stringResource(R.string.ob_cta_demo),
                    subtitle = stringResource(R.string.ob_choice_demo_sub),
                    primary = false,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    onClick = onDemo,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.ob_skip),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onSkip)
                    .padding(vertical = 10.dp),
                style = ShowCodeText.Caption,
                color = InkMuted,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.ob_footer),
                modifier = Modifier.fillMaxWidth(),
                style = ShowCodeText.Micro,
                color = InkMuted,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun Brand() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(R.drawable.ic_splash),
            contentDescription = null,
            modifier = Modifier.size(44.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = stringResource(R.string.app_name),
            style = ShowCodeText.Section,
            color = Ink,
        )
    }
}

/** 卡皮说一句话。整屏只有这一句是「角色」的声音，别让它变成第二段说明。 */
@Composable
private fun MascotLine() {
    Text(
        text = stringResource(R.string.ob_mascot_line),
        modifier = Modifier
            .padding(start = 54.dp)
            .background(SurfaceCard, ShowCodeShapes.Md)
            .border(1.dp, Hairline, ShowCodeShapes.Md)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        style = ShowCodeText.Meta,
        color = Ink,
    )
}

/**
 * 一张「看起来就是首页那张卡」的示例。
 *
 * 价值不需要用一句话解释——它就在这串码和「还剩 5 小时」里。
 * 标了「示例」是为了不让人把它读成自己的件。
 */
@Composable
private fun SamplePickupCard() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceCard, ShowCodeShapes.Md)
            .border(1.dp, Hairline, ShowCodeShapes.Md)
            .padding(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .width(6.dp)
                .height(64.dp)
                .background(UrgencySafe, ShowCodeShapes.Pill),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.ob_sample_point),
                    modifier = Modifier.weight(1f),
                    style = ShowCodeText.Section,
                    color = Ink,
                )
                Text(
                    text = stringResource(R.string.ob_sample_badge),
                    style = ShowCodeText.Micro,
                    color = InkMuted,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.ob_sample_code),
                style = ShowCodeText.Count,
                color = Ink,
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.ob_sample_locker),
                    style = ShowCodeText.CodeSmall,
                    color = InkSecondary,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.ob_sample_eta),
                    style = ShowCodeText.Micro,
                    color = UrgencyDueSoon,
                )
            }
        }
    }
}

/**
 * 一个大选择卡：标题 + 一行后果。
 *
 * 这是这一屏唯一的决定，所以它足够大、按下去会回弹、还会轻震一下——
 * 多邻国那种「按一下像按到实体」的感觉，靠的就是这三点，不是靠彩色插画。
 */
@Composable
private fun ChoiceCard(
    title: String,
    subtitle: String,
    primary: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = MotionTokens.NavRelease,
        label = "onboarding-choice-press",
    )
    val haptics = LocalHapticFeedback.current
    Column(
        modifier = modifier
            .heightIn(min = 88.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .background(if (primary) Ink else SurfaceCard, ShowCodeShapes.Md)
            .border(1.dp, if (primary) Ink else Hairline, ShowCodeShapes.Md)
            .clickable(
                interactionSource = interaction,
                indication = null,
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = title,
            style = ShowCodeText.Item,
            color = if (primary) OnsiteSurface else Ink,
            maxLines = 2,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = subtitle,
            style = ShowCodeText.Caption,
            color = if (primary) OnsiteSurface else InkMuted,
            maxLines = 2,
        )
    }
}

/* ── 第二屏：接平台 ─────────────────────────────────────────────────── */

@Composable
private fun Connect(
    accountCount: Int,
    onBind: () -> Unit,
    onFinish: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
    ) {
        /* 内容在可用高度里居中：两行字贴顶、按钮贴底，中间空一大块会像没画完。 */
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.ob_connect_title),
                style = ShowCodeText.EmptyState,
                color = Ink,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.ob_connect_body),
                style = ShowCodeText.Body,
                color = InkSecondary,
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.ob_connect_platforms),
                style = ShowCodeText.Item,
                color = Ink,
            )
            if (accountCount > 0) {
                Spacer(Modifier.height(14.dp))
                /* 从登录页回来时不再问「去绑定」：把已经发生的事说出来，主 CTA 换成开始使用。 */
                Text(
                    text = stringResource(R.string.ob_connect_bound, accountCount),
                    modifier = Modifier
                        .background(SurfaceCard, ShowCodeShapes.Md)
                        .border(1.dp, Hairline, ShowCodeShapes.Md)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    style = ShowCodeText.Meta,
                    color = Ink,
                )
            } else {
                Spacer(Modifier.height(12.dp))
                /* 不绑也进得去，但先说清绑了会得到什么——这一屏只有一个选择要讲。 */
                Text(
                    text = stringResource(R.string.ob_connect_outcome),
                    style = ShowCodeText.Meta,
                    color = InkMuted,
                )
            }
        }
        Column(modifier = Modifier.padding(bottom = 10.dp)) {
            if (accountCount > 0) {
                PrimaryButton(text = stringResource(R.string.ob_connect_done), onClick = onFinish)
                Spacer(Modifier.height(8.dp))
                GhostButton(text = stringResource(R.string.ob_connect_again), onClick = onBind)
            } else {
                PrimaryButton(text = stringResource(R.string.ob_connect_bind), onClick = onBind)
                Spacer(Modifier.height(8.dp))
                GhostButton(text = stringResource(R.string.ob_connect_skip), onClick = onFinish)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.ob_connect_note),
                modifier = Modifier.fillMaxWidth(),
                style = ShowCodeText.Caption,
                color = InkMuted,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/* ── 按钮 ───────────────────────────────────────────────────────────── */

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(Ink, ShowCodeShapes.Md)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = ShowCodeText.Section, color = OnsiteSurface)
    }
}

@Composable
private fun GhostButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = ShowCodeText.Body, color = InkSecondary)
    }
}
