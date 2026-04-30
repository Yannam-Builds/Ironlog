import { useEffect } from 'react';
import { activateKeepAwake, deactivateKeepAwake } from '@sayem314/react-native-keep-awake';

export function useKeepAwake(tag = 'ironlog') {
  useEffect(() => {
    activateKeepAwake(tag);
    return () => {
      deactivateKeepAwake(tag);
    };
  }, [tag]);
}

export default {
  useKeepAwake,
};
