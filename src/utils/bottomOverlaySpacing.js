import { Platform } from 'react-native';

export const NAV_PILL_VISUAL_HEIGHT = 72;
export const ACTIVE_WORKOUT_PILL_HEIGHT = 42;

export function getBottomOverlaySpacing({
  safeAreaBottom = 0,
  includeWorkoutPill = false,
  includeSearchPill = false,
  searchPillHeight = 56,
  extra = 0,
} = {}) {
  const baseInset = Math.max(safeAreaBottom, Platform.OS === 'ios' ? 12 : 10);
  let height = NAV_PILL_VISUAL_HEIGHT + baseInset + 10 + extra;
  if (includeWorkoutPill) {
    height += ACTIVE_WORKOUT_PILL_HEIGHT + 8;
  }
  if (includeSearchPill) {
    height += searchPillHeight + 12;
  }
  return height;
}

