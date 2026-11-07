import { useCallback, useEffect, useRef, useState } from 'react';
import { ActivityIndicator, Alert, Linking, ScrollView, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { useLocalSearchParams } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';
import * as Location from 'expo-location';
import { fetchRiderQueue, markDelivered, markPickedUp, navigationUrl, sendLocation, type RiderDelivery } from '../../src/api/rider';
import { fetchRestaurantProfile, type RestaurantProfile } from '../../src/api/profile';
import { DeliveryMap, type MapPoint } from '../../src/components/DeliveryMap';
import { t } from '../../src/i18n';
import { colors } from '../../src/theme';

const SHARE_EVERY_MS = 20000;

export default function RiderDeliveryScreen() {
  const { orderId } = useLocalSearchParams<{ orderId: string }>();
  const [delivery, setDelivery] = useState<RiderDelivery | null>(null);
  const [profile, setProfile] = useState<RestaurantProfile | null>(null);
  const [me, setMe] = useState<{ latitude: number; longitude: number } | null>(null);
  const [sharing, setSharing] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const watchRef = useRef<Location.LocationSubscription | null>(null);
  const lastSentRef = useRef(0);

  const load = useCallback(async () => {
    try {
      const queue = await fetchRiderQueue();
      setDelivery(queue.find((d) => d.orderId === orderId) ?? null);
    } catch {
      setError('Could not load this delivery.');
    }
  }, [orderId]);

  useEffect(() => {
    void load();
    fetchRestaurantProfile().then(setProfile).catch(() => {});
  }, [load]);

  const stopSharing = useCallback(() => {
    watchRef.current?.remove();
    watchRef.current = null;
    setSharing(false);
  }, []);

  const startSharing = useCallback(async () => {
    const { status } = await Location.requestForegroundPermissionsAsync();
    if (status !== 'granted') {
      setError(t('rider.locationDenied'));
      return;
    }
    setError('');
    watchRef.current?.remove();
    watchRef.current = await Location.watchPositionAsync(
      { accuracy: Location.Accuracy.High, timeInterval: 10000, distanceInterval: 25 },
      (position) => {
        const point = { latitude: position.coords.latitude, longitude: position.coords.longitude };
        setMe(point);
        const now = Date.now();
        if (now - lastSentRef.current >= SHARE_EVERY_MS) {
          lastSentRef.current = now;
          sendLocation(orderId, point.latitude, point.longitude).catch(() => {});
        }
      },
    );
    setSharing(true);
  }, [orderId]);

  // Share automatically while the order is on the way; stop when it's delivered or the screen closes.
  useEffect(() => {
    if (delivery?.status === 'OUT_FOR_DELIVERY' && !watchRef.current) void startSharing();
    if (delivery && delivery.status !== 'OUT_FOR_DELIVERY') stopSharing();
  }, [delivery, startSharing, stopSharing]);

  useEffect(() => stopSharing, [stopSharing]);

  async function act(kind: 'pickup' | 'delivered') {
    setBusy(true);
    try {
      const next = kind === 'pickup' ? await markPickedUp(orderId) : await markDelivered(orderId);
      setDelivery(next);
    } catch (err) {
      const message = (err as { response?: { data?: { message?: string } } })?.response?.data?.message;
      Alert.alert('Could not update', message ?? 'Please try again.');
    } finally {
      setBusy(false);
    }
  }

  function confirmDelivered() {
    Alert.alert(t('rider.confirmDeliveredTitle'), t('rider.confirmDeliveredBody'), [
      { text: t('common.cancel'), style: 'cancel' },
      { text: t('rider.markDelivered'), onPress: () => void act('delivered') },
    ]);
  }

  if (!delivery) {
    return (
      <View style={styles.center}>
        {error ? <Text style={styles.error}>{error}</Text> : <ActivityIndicator color={colors.brand} />}
      </View>
    );
  }

  const points: MapPoint[] = [];
  const riderLat = me?.latitude ?? delivery.riderLatitude ?? null;
  const riderLng = me?.longitude ?? delivery.riderLongitude ?? null;
  if (riderLat != null && riderLng != null) points.push({ latitude: riderLat, longitude: riderLng, title: 'You', color: colors.info });
  if (profile?.latitude != null && profile.longitude != null) points.push({ latitude: profile.latitude, longitude: profile.longitude, title: profile.name, color: colors.brand });

  return (
    <ScrollView style={styles.screen} contentContainerStyle={{ padding: 16, gap: 14 }}>
      <View style={styles.card}>
        <Text style={styles.ref}>#{delivery.orderId.slice(0, 8).toUpperCase()}</Text>
        <Text style={styles.label}>{t('rider.deliverTo')}</Text>
        <Text style={styles.address}>{delivery.deliveryAddress ?? '—'}</Text>
        {delivery.notes ? <Text style={styles.notes}>“{delivery.notes}”</Text> : null}
        <Text style={styles.meta}>{t('rider.collect', { amount: `GHS ${Number(delivery.total).toFixed(2)}` })}</Text>
        <View style={styles.row}>
          {delivery.deliveryAddress ? (
            <TouchableOpacity style={styles.secondary} onPress={() => Linking.openURL(navigationUrl(delivery.deliveryAddress ?? ''))}>
              <Ionicons name="navigate-outline" size={18} color={colors.brand} />
              <Text style={styles.secondaryText}>{t('rider.navigate')}</Text>
            </TouchableOpacity>
          ) : null}
          {delivery.customerPhone ? (
            <TouchableOpacity style={styles.secondary} onPress={() => Linking.openURL(`tel:${delivery.customerPhone}`)}>
              <Ionicons name="call-outline" size={18} color={colors.brand} />
              <Text style={styles.secondaryText}>{t('rider.callCustomer')}</Text>
            </TouchableOpacity>
          ) : null}
        </View>
      </View>

      <DeliveryMap points={points} />
      {points.length === 0 ? <Text style={styles.meta}>{t('rider.mapHint')}</Text> : null}

      {delivery.status === 'OUT_FOR_DELIVERY' ? (
        <View style={[styles.card, { flexDirection: 'row', alignItems: 'center', gap: 10 }]}>
          <View style={[styles.dot, { backgroundColor: sharing ? colors.success : colors.inkMuted }]} />
          <Text style={{ flex: 1, color: colors.ink }}>{sharing ? t('rider.sharing') : t('rider.notSharing')}</Text>
          <TouchableOpacity onPress={sharing ? stopSharing : startSharing}>
            <Text style={styles.link}>{sharing ? t('rider.pause') : t('rider.share')}</Text>
          </TouchableOpacity>
        </View>
      ) : null}
      {error ? <Text style={styles.error}>{error}</Text> : null}

      {delivery.status === 'DELIVERED' ? (
        <View style={[styles.card, { alignItems: 'center' }]}>
          <Ionicons name="checkmark-circle" size={40} color={colors.success} />
          <Text style={styles.done}>{t('rider.delivered')}</Text>
        </View>
      ) : delivery.status === 'OUT_FOR_DELIVERY' ? (
        <TouchableOpacity style={styles.primary} disabled={busy} onPress={confirmDelivered}>
          {busy ? <ActivityIndicator color={colors.white} /> : <Text style={styles.primaryText}>{t('rider.markDelivered')}</Text>}
        </TouchableOpacity>
      ) : (
        <TouchableOpacity style={[styles.primary, delivery.status !== 'READY' && { opacity: 0.5 }]} disabled={busy || delivery.status !== 'READY'} onPress={() => void act('pickup')}>
          {busy ? <ActivityIndicator color={colors.white} /> : <Text style={styles.primaryText}>{delivery.status === 'READY' ? t('rider.pickedUp') : t('rider.waitingKitchen')}</Text>}
        </TouchableOpacity>
      )}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.canvas },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', backgroundColor: colors.canvas, padding: 24 },
  card: { backgroundColor: colors.surface, borderRadius: 14, borderWidth: 1, borderColor: colors.line, padding: 16, gap: 6 },
  ref: { fontSize: 20, fontWeight: '800', color: colors.ink },
  label: { marginTop: 6, fontSize: 12, fontWeight: '700', color: colors.inkMuted, textTransform: 'uppercase', letterSpacing: 1 },
  address: { fontSize: 17, color: colors.ink, fontWeight: '600' },
  notes: { fontStyle: 'italic', color: colors.inkSoft },
  meta: { color: colors.inkSoft, fontSize: 13 },
  row: { flexDirection: 'row', gap: 10, marginTop: 8, flexWrap: 'wrap' },
  secondary: { flexDirection: 'row', alignItems: 'center', gap: 6, borderWidth: 1, borderColor: colors.line, borderRadius: 10, paddingHorizontal: 14, paddingVertical: 10 },
  secondaryText: { color: colors.brand, fontWeight: '700' },
  dot: { width: 10, height: 10, borderRadius: 5 },
  link: { color: colors.brand, fontWeight: '700' },
  primary: { backgroundColor: colors.brand, borderRadius: 14, paddingVertical: 18, alignItems: 'center' },
  primaryText: { color: colors.white, fontWeight: '800', fontSize: 17 },
  done: { fontSize: 18, fontWeight: '800', color: colors.ink, marginTop: 6 },
  error: { color: colors.danger },
});
