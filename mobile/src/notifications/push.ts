import AsyncStorage from '@react-native-async-storage/async-storage';
import Constants from 'expo-constants';
import * as Device from 'expo-device';
import * as Notifications from 'expo-notifications';
import { Platform } from 'react-native';
import { registerPushDevice, unregisterPushDevice } from '../api/notifications';

const TOKEN_KEY = 'pushToken';

// Show order updates as banners even while the app is open.
Notifications.setNotificationHandler({
  handleNotification: async () => ({
    shouldShowAlert: true,
    shouldPlaySound: true,
    shouldSetBadge: false,
  }),
});

function projectId(): string | undefined {
  const extra = Constants.expoConfig?.extra as { eas?: { projectId?: string } } | undefined;
  return extra?.eas?.projectId ?? Constants.easConfig?.projectId ?? undefined;
}

/**
 * Asks for notification permission, gets this device's Expo push token and registers it
 * with the backend. Safe to call on every sign-in; failures are swallowed (push is optional).
 * Returns the token, or null when push isn't available (simulator, denied, no EAS project).
 */
export async function registerForPushNotifications(): Promise<string | null> {
  try {
    if (!Device.isDevice) return null;
    if (Platform.OS === 'android') {
      await Notifications.setNotificationChannelAsync('orders', {
        name: 'Order updates',
        importance: Notifications.AndroidImportance.HIGH,
      });
    }
    const current = await Notifications.getPermissionsAsync();
    let status = current.status;
    if (status !== 'granted') {
      status = (await Notifications.requestPermissionsAsync()).status;
    }
    if (status !== 'granted') return null;

    const id = projectId();
    const { data: token } = await Notifications.getExpoPushTokenAsync(id ? { projectId: id } : undefined);
    await registerPushDevice(token, Platform.OS);
    await AsyncStorage.setItem(TOKEN_KEY, token);
    return token;
  } catch {
    return null;
  }
}

/** Removes this device from the signed-in account (call before clearing the session). */
export async function unregisterPushNotifications(): Promise<void> {
  try {
    const token = await AsyncStorage.getItem(TOKEN_KEY);
    if (!token) return;
    await unregisterPushDevice(token).catch(() => {});
    await AsyncStorage.removeItem(TOKEN_KEY);
  } catch {
    /* ignore */
  }
}

export type PushData = { type?: string; orderId?: string; status?: string };

/** Route for a tapped notification, or null if it isn't about an order. */
export function routeForNotification(data: PushData | undefined, role: string | null): string | null {
  if (!data?.orderId) return null;
  if (data.type === 'DELIVERY_ASSIGNED' || role === 'RIDER') return `/rider/${data.orderId}`;
  return `/order/${data.orderId}`;
}

/** Calls `onOpen` when the user taps a notification (including the one that launched the app). */
export function onNotificationOpened(onOpen: (data: PushData) => void): () => void {
  Notifications.getLastNotificationResponseAsync()
    .then((response) => {
      if (response) onOpen(response.notification.request.content.data as PushData);
    })
    .catch(() => {});
  const subscription = Notifications.addNotificationResponseReceivedListener((response) => {
    onOpen(response.notification.request.content.data as PushData);
  });
  return () => subscription.remove();
}
