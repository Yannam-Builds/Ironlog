import Toast from 'react-native-toast-message';

function show(type, title, message, extra = {}) {
  Toast.show({
    type,
    text1: title,
    text2: message,
    position: 'top',
    visibilityTime: extra.visibilityTime || 2200,
    topOffset: extra.topOffset || 60,
  });
}

export function showInfoToast(title, message, extra = {}) {
  show('info', title, message, extra);
}

export function showSuccessToast(title, message, extra = {}) {
  show('success', title, message, extra);
}

export function showErrorToast(title, message, extra = {}) {
  show('error', title, message, extra);
}

