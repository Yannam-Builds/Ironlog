import * as Keychain from 'react-native-keychain';

const USERNAME = 'ironlog';

export async function getItemAsync(key) {
  const credentials = await Keychain.getGenericPassword({ service: key });
  return credentials ? credentials.password : null;
}

export async function setItemAsync(key, value) {
  await Keychain.setGenericPassword(USERNAME, String(value ?? ''), {
    service: key,
    accessible: Keychain.ACCESSIBLE.WHEN_UNLOCKED_THIS_DEVICE_ONLY,
  });
}

export async function deleteItemAsync(key) {
  await Keychain.resetGenericPassword({ service: key });
}

export default {
  getItemAsync,
  setItemAsync,
  deleteItemAsync,
};
