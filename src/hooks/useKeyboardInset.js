import { useEffect, useState } from 'react';
import { Keyboard, Platform } from 'react-native';

export default function useKeyboardInset(baseInset = 0) {
  const [keyboardInset, setKeyboardInset] = useState(0);

  useEffect(() => {
    const showEvent = Platform.OS === 'ios' ? 'keyboardWillShow' : 'keyboardDidShow';
    const hideEvent = Platform.OS === 'ios' ? 'keyboardWillHide' : 'keyboardDidHide';

    const onShow = Keyboard.addListener(showEvent, (event) => {
      const raw = Number(event?.endCoordinates?.height || 0);
      const next = Math.max(0, raw - Number(baseInset || 0));
      setKeyboardInset(next);
    });
    const onHide = Keyboard.addListener(hideEvent, () => setKeyboardInset(0));

    return () => {
      onShow.remove();
      onHide.remove();
    };
  }, [baseInset]);

  return keyboardInset;
}

