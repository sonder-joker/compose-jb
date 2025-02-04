package org.jetbrains.compose.web.dom

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeCompilerApi
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.DisposableEffectResult
import androidx.compose.runtime.DisposableEffectScope
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.remember
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement

interface DOMScope<out TElement : Element>

/**
 * ElementScope allows adding effects to the Composable representing html element.
 * Also see a tutorial: https://github.com/JetBrains/compose-jb/tree/master/tutorials/Web/Using_Effects
 *
 * Example:
 * ```
 * Div {
 *   DisposableRefEffect { htmlDivElement ->
 *      onDispose {}
 *   }
 * }
 * ```
 */
interface ElementScope<out TElement : Element> : DOMScope<TElement> {

    /**
     * A side effect of composition that must run for any new unique value of [key]
     * and must be reversed or cleaned up if [key] changes or if the DisposableRefEffect leaves the composition.
     * [effect] lambda provides a reference to a native element represented by Composable.
     * Adding [DisposableEffectScope.onDispose] to [effect] is mandatory.
     */
    @Composable
    @NonRestartableComposable
    fun DisposableRefEffect(
        key: Any?,
        effect: DisposableEffectScope.(TElement) -> DisposableEffectResult
    )

    /**
     * A side effect of composition that must run once an element enters composition
     * and must be reversed or cleaned up if element or the DisposableRefEffect leaves the composition.
     * [effect] lambda provides a reference to a native element represented by Composable.
     * Adding [DisposableEffectScope.onDispose] to [effect] is mandatory.
     */
    @Composable
    @NonRestartableComposable
    fun DisposableRefEffect(
        effect: DisposableEffectScope.(TElement) -> DisposableEffectResult
    ) {
        DisposableRefEffect(null, effect)
    }

    /**
     * A side effect of composition that runs on every successful recomposition if [key] changes.
     * Also see [SideEffect].
     * Same as other effects in [ElementScope], it provides a reference to a native element in [effect] lambda.
     */
    @Composable
    @NonRestartableComposable
    fun DomSideEffect(key: Any?, effect: DomEffectScope.(TElement) -> Unit)

    /**
     * A side effect of composition that runs on every successful recomposition.
     * Also see [SideEffect].
     * Same as other effects in [ElementScope], it provides a reference to a native element in [effect] lambda.
     */
    @Composable
    @NonRestartableComposable
    fun DomSideEffect(effect: DomEffectScope.(TElement) -> Unit)
}

abstract class ElementScopeBase<out TElement : Element> : ElementScope<TElement> {
    abstract val element: TElement

    private var nextDisposableDomEffectKey = 0

    @Composable
    @NonRestartableComposable
    override fun DisposableRefEffect(
        key: Any?,
        effect: DisposableEffectScope.(TElement) -> DisposableEffectResult
    ) {
        DisposableEffect(key) { effect(element) }
    }

    @Composable
    @NonRestartableComposable
    @OptIn(ComposeCompilerApi::class)
    override fun DomSideEffect(
        key: Any?,
        effect: DomEffectScope.(TElement) -> Unit
    ) {
        val changed = currentComposer.changed(key)
        val effectHolder = remember(key) {
            DomDisposableEffectHolder(this)
        }
        SideEffect {
            if (changed) effectHolder.effect(element)
        }
    }

    @Composable
    @NonRestartableComposable
    override fun DomSideEffect(effect: DomEffectScope.(TElement) -> Unit) {
        DomSideEffect(nextDisposableDomEffectKey++, effect)
    }
}

open class ElementScopeImpl<TElement : Element> : ElementScopeBase<TElement>() {
    public override lateinit var element: TElement
}

interface DomEffectScope {
    fun onDispose(disposeEffect: (Element) -> Unit)
}

private class DomDisposableEffectHolder(
    val elementScope: ElementScopeBase<Element>
) : RememberObserver, DomEffectScope {
    var onDispose: ((Element) -> Unit)? = null

    override fun onRemembered() {}

    override fun onForgotten() {
        onDispose?.invoke(elementScope.element)
    }

    override fun onAbandoned() {}

    override fun onDispose(disposeEffect: (Element) -> Unit) {
        onDispose = disposeEffect
    }
}
