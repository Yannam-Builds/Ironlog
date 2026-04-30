const { getDefaultConfig, mergeConfig } = require('@react-native/metro-config');
const path = require('path');

const defaultConfig = getDefaultConfig(__dirname);

module.exports = mergeConfig(defaultConfig, {
  resolver: {
    extraNodeModules: {
      'expo-screen-orientation': path.resolve(__dirname, 'src/platform/expoScreenOrientationShim.js'),
    },
    resolveRequest: (context, moduleName, platform) => {
      if (moduleName === 'expo-screen-orientation') {
        return {
          filePath: path.resolve(__dirname, 'src/platform/expoScreenOrientationShim.js'),
          type: 'sourceFile',
        };
      }
      return context.resolveRequest(context, moduleName, platform);
    },
  },
});
