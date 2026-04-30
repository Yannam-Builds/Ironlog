import React from 'react';
import PremiumFrostedTabBar from './PremiumFrostedTabBar';

/**
 * FluidTabBar — always uses the premium frosted tab bar.
 * Apple Glass has been removed.
 */
export default function FluidTabBar({
  state,
  descriptors,
  navigation,
  scrollX,
}) {
  return (
    <PremiumFrostedTabBar
      state={state}
      descriptors={descriptors}
      navigation={navigation}
      scrollX={scrollX}
    />
  );
}
