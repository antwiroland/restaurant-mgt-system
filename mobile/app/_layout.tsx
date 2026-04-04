import { useEffect } from 'react';
import { Stack } from 'expo-router';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { useConnectivity } from '../src/hooks/useConnectivity';
import { useOfflineStore } from '../src/store/offline';
import { useAuthStore } from '../src/store/auth';

export default function RootLayout() {
  const { loadQueue } = useOfflineStore();
  const { restore } = useAuthStore();
  useConnectivity();

  useEffect(() => {
    loadQueue();
    restore();
  }, []);

  return (
    <SafeAreaProvider>
      <Stack screenOptions={{ headerShown: false }}>
        <Stack.Screen name="(auth)" />
        <Stack.Screen name="(tabs)" />
        <Stack.Screen name="cart" options={{ presentation: 'modal', headerShown: true, title: 'Cart' }} />
        <Stack.Screen name="checkout" options={{ presentation: 'modal', headerShown: true, title: 'Checkout' }} />
        <Stack.Screen name="qr-scan" options={{ presentation: 'modal', headerShown: true, title: 'Scan QR Code' }} />
        <Stack.Screen name="order/[id]" options={{ headerShown: true, title: 'Order Tracking' }} />
        <Stack.Screen name="track-table/[token]" options={{ headerShown: true, title: 'Table Tracking' }} />
        <Stack.Screen name="reservation" options={{ headerShown: true, title: 'Reservations' }} />
        <Stack.Screen name="group/[code]" options={{ headerShown: true, title: 'Group Order' }} />
      </Stack>
    </SafeAreaProvider>
  );
}
