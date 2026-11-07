import { useEffect } from 'react';
import { Stack, useRouter } from 'expo-router';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { useConnectivity } from '../src/hooks/useConnectivity';
import { useOfflineStore } from '../src/store/offline';
import { useAuthStore } from '../src/store/auth';
import { colors } from '../src/theme';
import { onNotificationOpened, registerForPushNotifications, routeForNotification } from '../src/notifications/push';

export default function RootLayout() {
  const { loadQueue } = useOfflineStore();
  const { restore } = useAuthStore();
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  const router = useRouter();
  useConnectivity();

  useEffect(() => {
    loadQueue();
    restore();
  }, []);

  // Register for push once signed in (customers get order updates, riders get new assignments).
  useEffect(() => {
    if (isAuthenticated) void registerForPushNotifications();
  }, [isAuthenticated]);

  // Tapping a notification opens the order it is about.
  useEffect(
    () =>
      onNotificationOpened((data) => {
        const route = routeForNotification(data, useAuthStore.getState().role);
        if (route) router.push(route as never);
      }),
    [router],
  );

  return (
    <SafeAreaProvider>
      <Stack
        screenOptions={{
          headerShown: false,
          headerStyle: { backgroundColor: colors.canvas },
          headerShadowVisible: false,
          headerTintColor: colors.brand,
          headerTitleStyle: { fontWeight: '800', color: colors.ink },
          contentStyle: { backgroundColor: colors.canvas },
        }}
      >
        <Stack.Screen name="(auth)" />
        <Stack.Screen name="(tabs)" />
        <Stack.Screen name="cart" options={{ presentation: 'modal', headerShown: true, title: 'Your order' }} />
        <Stack.Screen name="checkout" options={{ presentation: 'modal', headerShown: true, title: 'Checkout' }} />
        <Stack.Screen name="qr-scan" options={{ presentation: 'modal', headerShown: true, title: 'Scan QR Code' }} />
        <Stack.Screen name="order/[id]" options={{ headerShown: true, title: 'Track order' }} />
        <Stack.Screen name="track-table/[token]" options={{ headerShown: true, title: 'Your table' }} />
        <Stack.Screen name="reservation" options={{ headerShown: true, title: 'Reservations' }} />
        <Stack.Screen name="group/[code]" options={{ headerShown: true, title: 'Group Order' }} />
        <Stack.Screen name="rider/index" options={{ headerShown: true, title: 'My deliveries' }} />
        <Stack.Screen name="rider/[orderId]" options={{ headerShown: true, title: 'Delivery' }} />
        <Stack.Screen name="favourites" options={{ headerShown: true, title: 'Favourites' }} />
      </Stack>
    </SafeAreaProvider>
  );
}
