import { animate, createTimeline, stagger } from 'animejs'

export const motionDisabled = () => window.matchMedia('(prefers-reduced-motion: reduce)').matches

export function runMotion(callback) {
  if (motionDisabled()) return null
  return callback({ animate, createTimeline, stagger })
}
