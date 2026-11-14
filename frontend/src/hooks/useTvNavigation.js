import { useCallback, useEffect, useRef } from 'react'
import { getTvKeyAction } from '../utils/tvNavigation'

const FOCUSABLE_SELECTOR = [
  'button:not([disabled]):not([tabindex="-1"])',
  'a[href]:not([tabindex="-1"])',
  'input:not([disabled]):not([tabindex="-1"])',
  'select:not([disabled]):not([tabindex="-1"])',
  'textarea:not([disabled]):not([tabindex="-1"])',
  '[tabindex]:not([tabindex="-1"])',
].join(',')

function isTextInput(target) {
  return (target instanceof HTMLInputElement && target.type !== 'range')
    || target instanceof HTMLTextAreaElement
    || target instanceof HTMLSelectElement
    || target.isContentEditable
}

function focusInDirection(direction) {
  const active = document.activeElement
  if (!(active instanceof HTMLElement) || isTextInput(active)) {
    return false
  }

  const source = active.getBoundingClientRect()
  const sourceX = source.left + source.width / 2
  const sourceY = source.top + source.height / 2
  const candidates = [...document.querySelectorAll(FOCUSABLE_SELECTOR)]
    .filter((element) => element instanceof HTMLElement
      && element !== active
      && element.tabIndex >= 0
      && element.offsetWidth > 0
      && element.offsetHeight > 0)
    .map((element) => {
      const rect = element.getBoundingClientRect()
      const centerX = rect.left + rect.width / 2
      const centerY = rect.top + rect.height / 2
      const deltaX = centerX - sourceX
      const deltaY = centerY - sourceY
      const primary = direction === 'left' || direction === 'right'
        ? deltaX
        : deltaY
      const cross = direction === 'left' || direction === 'right'
        ? Math.abs(deltaY)
        : Math.abs(deltaX)
      const pointsForward = direction === 'left' || direction === 'up'
        ? primary < -1
        : primary > 1
      return pointsForward
        ? {element, score: Math.abs(primary) + cross * 2}
        : null
    })
    .filter(Boolean)
    .sort((first, second) => first.score - second.score)

  const next = candidates[0]?.element
  if (!next) {
    return false
  }

  next.focus()
  next.scrollIntoView({block: 'nearest', inline: 'nearest'})
  return true
}

export function useTvNavigation({onBack, onPlayPause, onSeek, onDirection}) {
  const lastFocused = useRef(null)

  useEffect(() => {
    const handleFocusIn = (event) => {
      const target = event.target
      if (!(target instanceof HTMLElement)) {
        return
      }

      lastFocused.current = target
      target.scrollIntoView({ block: 'nearest', inline: 'nearest' })
    }

    const handleKeyDown = (event) => {
      const action = getTvKeyAction(event.key)
      if (action === 'up' || action === 'down'
          || action === 'left' || action === 'right') {
        if (onDirection?.(action)) {
          event.preventDefault()
          return
        }
        if (focusInDirection(action)) {
          event.preventDefault()
        }
        return
      }

      if (action === 'back') {
        event.preventDefault()
        onBack()
        return
      }

      if (action === 'playPause') {
        event.preventDefault()
        onPlayPause()
        return
      }

      if (action === 'fastForward' || action === 'rewind') {
        event.preventDefault()
        onSeek(action === 'fastForward' ? 30 : -10)
      }
    }

    document.addEventListener('focusin', handleFocusIn)
    document.addEventListener('keydown', handleKeyDown)
    return () => {
      document.removeEventListener('focusin', handleFocusIn)
      document.removeEventListener('keydown', handleKeyDown)
    }
  }, [onBack, onDirection, onPlayPause, onSeek])

  const restoreFocus = useCallback(() => {
    if (lastFocused.current?.isConnected) {
      lastFocused.current.focus()
    }
  }, [])

  return {restoreFocus}
}
