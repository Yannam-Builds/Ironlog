import { useCallback, useRef, useState } from 'react';
import { InteractionManager } from 'react-native';
import { useFocusEffect } from '@react-navigation/native';

export default function useDeferredScreenReady(options = {}) {
  const { minDelayMs = 0, resetOnBlur = true, stayReady = false } = options;
  const [ready, setReady] = useState(false);
  // When stayReady=true, once we become ready we stay ready through any
  // subsequent focus/blur cycles (prevents flicker when overlaid by another screen).
  const everReady = useRef(false);

  useFocusEffect(
    useCallback(() => {
      let cancelled = false;
      let timeoutId = null;
      let frameId = null;

      if (!stayReady || !everReady.current) {
        setReady(false);
      }

      const task = InteractionManager.runAfterInteractions(() => {
        const markReady = () => {
          frameId = requestAnimationFrame(() => {
            if (!cancelled) {
              everReady.current = true;
              setReady(true);
            }
          });
        };

        if (minDelayMs > 0) {
          timeoutId = setTimeout(markReady, minDelayMs);
        } else {
          markReady();
        }
      });

      return () => {
        cancelled = true;
        if (typeof task?.cancel === 'function') task.cancel();
        if (timeoutId) clearTimeout(timeoutId);
        if (frameId) cancelAnimationFrame(frameId);
        if (resetOnBlur && !stayReady) setReady(false);
      };
    }, [minDelayMs, resetOnBlur, stayReady])
  );

  return ready;
}
