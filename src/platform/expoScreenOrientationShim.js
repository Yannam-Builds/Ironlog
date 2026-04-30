import { Dimensions } from 'react-native';

const Orientation = {
  UNKNOWN: 0,
  PORTRAIT_UP: 1,
  PORTRAIT_DOWN: 2,
  LANDSCAPE_LEFT: 3,
  LANDSCAPE_RIGHT: 4,
};

function resolveOrientation() {
  const { width, height } = Dimensions.get('window');
  return width > height ? Orientation.LANDSCAPE_LEFT : Orientation.PORTRAIT_UP;
}

export async function getOrientationAsync() {
  return resolveOrientation();
}

export function addOrientationChangeListener(listener) {
  const subscription = Dimensions.addEventListener('change', ({ window }) => {
    const orientation = window.width > window.height
      ? Orientation.LANDSCAPE_LEFT
      : Orientation.PORTRAIT_UP;
    listener?.({ orientationInfo: { orientation } });
  });
  return subscription;
}

export function removeOrientationChangeListener(subscription) {
  subscription?.remove?.();
}

export { Orientation };

