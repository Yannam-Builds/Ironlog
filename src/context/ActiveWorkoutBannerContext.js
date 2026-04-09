
import React, { createContext, useContext, useState } from 'react';

const ActiveWorkoutBannerContext = createContext(null);

// banner shape: { planIndex, dayIndex, dayName, startTime, isDeload } | null
export function ActiveWorkoutBannerProvider({ children }) {
  const [banner, setBanner] = useState(null);
  return (
    <ActiveWorkoutBannerContext.Provider value={{ banner, setBanner }}>
      {children}
    </ActiveWorkoutBannerContext.Provider>
  );
}

export const useActiveBanner = () => useContext(ActiveWorkoutBannerContext);
