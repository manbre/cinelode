import { useCallback, useEffect, useRef } from 'react'
import {
  getTvKeyAction,
  isBlockedVerticalNavigation,
  isTextCursorAtBoundary,
  isWriterRightNavigation,
  isWriterUpNavigation,
  isRightmostInRow,
  isTopmostInColumn,
} from '../utils/tvNavigation'

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

function focusInDirection(direction, scope = document, allowTextInput = false) {
  const active = document.activeElement
  if (!(active instanceof HTMLElement) || (!allowTextInput && isTextInput(active))) {
    return false
  }

  const source = active.getBoundingClientRect()
  const sourceX = source.left + source.width / 2
  const sourceY = source.top + source.height / 2
  const candidates = [...scope.querySelectorAll(FOCUSABLE_SELECTOR)]
    .filter((element) => element instanceof HTMLElement
      && element !== active
      && element.tabIndex >= 0
      && !isBlockedVerticalNavigation(
        direction,
        active.dataset.navigationRole,
        element.dataset.navigationRole,
      )
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

function focusNearestGenreChip(sourceElement) {
  const source = sourceElement.getBoundingClientRect()
  const sourceX = source.left + source.width / 2
  const chip = [...document.querySelectorAll('[data-genre-chip="true"]')]
    .filter((element) => element instanceof HTMLElement
      && element.tabIndex >= 0
      && element.offsetWidth > 0
      && element.offsetHeight > 0)
    .map((element) => {
      const rect = element.getBoundingClientRect()
      return {element, distance: Math.abs(rect.left + rect.width / 2 - sourceX)}
    })
    .sort((first, second) => first.distance - second.distance)[0]?.element

  if (!(chip instanceof HTMLElement)) {
    return false
  }

  chip.focus()
  chip.scrollIntoView({block: 'nearest', inline: 'nearest'})
  return true
}

function focusGenreFromCatalogColumnTop(direction) {
  const active = document.activeElement
  if (direction !== 'up'
      || !(active instanceof HTMLElement)
      || !active.matches('[data-catalog-card="true"]')) {
    return false
  }

  const source = active.getBoundingClientRect()
  const cards = [...document.querySelectorAll('[data-catalog-card="true"]')]
    .filter((card) => card instanceof HTMLElement && card !== active)
    .map((card) => card.getBoundingClientRect())
  if (!isTopmostInColumn(source, cards)) {
    return false
  }

  return focusNearestGenreChip(active)
}

function focusPlayFromCatalogRowEnd(direction) {
  const active = document.activeElement
  if (direction !== 'right'
      || !window.matchMedia('(min-width: 1024px)').matches
      || !(active instanceof HTMLElement)
      || !active.matches('[data-catalog-card="true"]')) {
    return false
  }

  const source = active.getBoundingClientRect()
  const cards = [...document.querySelectorAll('[data-catalog-card="true"]')]
    .filter((card) => card instanceof HTMLElement && card !== active)
    .map((card) => card.getBoundingClientRect())
  if (!isRightmostInRow(source, cards)) {
    return false
  }

  const playButton = document.querySelector('[data-primary-play="true"]')
  if (!(playButton instanceof HTMLElement)
      || playButton.offsetWidth === 0
      || playButton.offsetHeight === 0
      || playButton.getBoundingClientRect().left < source.right - 1) {
    return false
  }

  playButton.focus()
  playButton.scrollIntoView({block: 'nearest', inline: 'nearest'})
  return true
}

function focusCastChipFromWriter(direction) {
  if (direction !== 'right') {
    return false
  }

  const active = document.activeElement
  if (!(active instanceof HTMLElement) || active.dataset.personRole !== 'writer') {
    return false
  }

  const source = active.getBoundingClientRect()
  const sourceX = source.left + source.width / 2
  const sourceY = source.top + source.height / 2
  const target = [...document.querySelectorAll('button[data-person-role="actors"]')]
    .filter((element) => element instanceof HTMLElement
      && element.tabIndex >= 0
      && element.offsetWidth > 0
      && element.offsetHeight > 0)
    .map((element) => {
      const rect = element.getBoundingClientRect()
      const deltaX = rect.left + rect.width / 2 - sourceX
      if (deltaX <= 1) {
        return null
      }
      const deltaY = Math.abs(rect.top + rect.height / 2 - sourceY)
      return {element, score: deltaX + deltaY * 2}
    })
    .filter(Boolean)
    .sort((first, second) => first.score - second.score)[0]?.element

  if (!(target instanceof HTMLElement)) {
    return false
  }

  target.focus()
  target.scrollIntoView({block: 'nearest', inline: 'nearest'})
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
        const active = document.activeElement
        if (active instanceof HTMLElement
            && isWriterRightNavigation(action, active.dataset.personRole)
            && focusCastChipFromWriter(action)) {
          event.preventDefault()
          return
        }
        if (active instanceof HTMLElement
            && isWriterUpNavigation(action, active.dataset.personRole)) {
          const playButton = document.querySelector('[data-primary-play="true"]')
          if (playButton instanceof HTMLElement
              && playButton.offsetWidth > 0
              && playButton.offsetHeight > 0) {
            playButton.focus()
            playButton.scrollIntoView({block: 'nearest', inline: 'nearest'})
            event.preventDefault()
            return
          }
        }
        if (active instanceof HTMLInputElement
            && active.matches('[aria-label="Search movies"]')) {
          if (action === 'left' || action === 'right') {
            if (!isTextCursorAtBoundary(
              action,
              active.selectionStart,
              active.selectionEnd,
              active.value.length,
            )) {
              return
            }
            const toolbar = active.closest('[data-catalog-toolbar="true"]')
            if (!(toolbar instanceof HTMLElement)
                || !focusInDirection(action, toolbar, true)) {
              event.preventDefault()
              return
            }
            event.preventDefault()
            return
          }
          if (action === 'down') {
            if (!focusNearestGenreChip(active)) {
              focusInDirection(action, document, true)
            }
            event.preventDefault()
            return
          }
          if (action === 'up') {
            const toolbar = active.closest('[data-catalog-toolbar="true"]')
            if (toolbar instanceof HTMLElement) {
              focusInDirection(action, toolbar, true)
            }
            event.preventDefault()
            return
          }
        }
        if (active instanceof HTMLElement && active.matches('[data-catalog-card="true"]')) {
          const catalogGrid = active.closest('[data-catalog-grid="true"]')
          if (action === 'up' && focusGenreFromCatalogColumnTop(action)) {
            event.preventDefault()
            return
          }
          if (action === 'right' && focusPlayFromCatalogRowEnd(action)) {
            event.preventDefault()
            return
          }
          if (catalogGrid instanceof HTMLElement && focusInDirection(action, catalogGrid)) {
            event.preventDefault()
            return
          }
          event.preventDefault()
          return
        }
        const detailsPanel = active instanceof HTMLElement
          ? active.closest('[data-details-panel="true"]')
          : null
        if (detailsPanel instanceof HTMLElement) {
          if (action === 'left') {
            if (focusInDirection(action, detailsPanel)) {
              event.preventDefault()
              return
            }
            const catalogGrid = document.querySelector('[data-catalog-grid="true"]')
            if (catalogGrid instanceof HTMLElement && focusInDirection(action, catalogGrid)) {
              event.preventDefault()
              return
            }
            event.preventDefault()
            return
          }
          if (focusInDirection(action, detailsPanel)) {
            event.preventDefault()
            return
          }
          event.preventDefault()
          return
        }
        if (active instanceof HTMLElement
            && !isTextInput(active)
            && (action === 'left' || action === 'right')) {
          const genreRow = active.closest('[data-genre-row="true"]')
          if (genreRow instanceof HTMLElement) {
            if (focusInDirection(action, genreRow)) {
              event.preventDefault()
              return
            }
            event.preventDefault()
            return
          }
          const catalogToolbar = active.closest('[data-catalog-toolbar="true"]')
          if (catalogToolbar instanceof HTMLElement) {
            if (focusInDirection(action, catalogToolbar)) {
              event.preventDefault()
              return
            }
            event.preventDefault()
            return
          }
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
