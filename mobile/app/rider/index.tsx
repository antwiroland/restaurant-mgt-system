import { useCallback, useEffect, useState } from 'react';
import { ActivityIndicator, FlatList, RefreshControl, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { router } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';
import { fetchRiderQueue, type RiderDelivery } from '../../src/api/rider';
import { logoutCustomer } from '../../src/api/auth';
import { useAuthStore } from '../../src/store/auth';
import { t } from '../../src/i18n';
import { colors } from '../../src/theme';

const STATUS_LABEL: Record<string, string> = {
  READY: 'Ready to collect',
  OUT_FOR_DELIVERY: 'On the way',
  DELIVERED: 'Delivered',
  CONFIRMED: 'Being prepared',
  PREPARING: 'Being prepared',
};

export default function RiderQueueScreen() {
  const { name, clear } = useAuthStore();
  const [items, setItems] = useState<RiderDelivery[] | null>(null);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    try {
      setItems(await fetchRiderQueue());
      setError('');
    } catch {
      setError('Could not load your deliveries. Pull to retry.');
    }
  }, []);

  useEffect(() => {
    void load();
    const timer = setInterval(() => void load(), 30000);
    return () => clearInterval(timer);
  }, [load]);

  async function signOut() {
    await logoutCustomer();
    clear();
    router.replace('/(auth)/login');
  }

  const active = (items ?? []).filter((d) => d.status !== 'DELIVERED');
  const done = (items ?? []).filter((d) => d.status === 'DELIVERED');

  return (
    <View style={styles.screen}>
      <View style={styles.header}>
        <View style={{ flex: 1 }}>
          <Text style={styles.kicker}>{t('rider.greeting', { name: name?.split(' ')[0] ?? '' })}</Text>
          <Text style={styles.title}>{t('rider.activeCount', { count: active.length })}</Text>
        </View>
        <TouchableOpacity onPress={signOut} style={styles.signOut} accessibilityRole="button">
          <Ionicons name="log-out-outline" size={18} color={colors.inkSoft} />
          <Text style={styles.signOutText}>{t('common.signOut')}</Text>
        </TouchableOpacity>
      </View>

      {items === null && !error ? (
        <ActivityIndicator color={colors.brand} style={{ marginTop: 40 }} />
      ) : (
        <FlatList
          data={[...active, ...done]}
          keyExtractor={(d) => d.orderId}
          contentContainerStyle={{ padding: 16, gap: 12 }}
          refreshControl={<RefreshControl refreshing={refreshing} onRefresh={async () => { setRefreshing(true); await load(); setRefreshing(false); }} />}
          ListHeaderComponent={error ? <Text style={styles.error}>{error}</Text> : null}
          ListEmptyComponent={
            <View style={styles.empty}>
              <Ionicons name="bicycle-outline" size={40} color={colors.inkMuted} />
              <Text style={styles.emptyText}>{t('rider.empty')}</Text>
            </View>
          }
          renderItem={({ item }) => (
            <TouchableOpacity
              style={[styles.card, item.status === 'DELIVERED' && { opacity: 0.6 }]}
              onPress={() => router.push(`/rider/${item.orderId}`)}
              accessibilityRole="button"
            >
              <View style={styles.cardTop}>
                <Text style={styles.ref}>#{item.orderId.slice(0, 8).toUpperCase()}</Text>
                <Text style={[styles.badge, item.status === 'OUT_FOR_DELIVERY' ? styles.badgeLive : null]}>{STATUS_LABEL[item.status] ?? item.status}</Text>
              </View>
              <Text style={styles.address} numberOfLines={2}>{item.deliveryAddress ?? 'No address given'}</Text>
              <Text style={styles.meta}>GHS {Number(item.total).toFixed(2)} · {new Date(item.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</Text>
            </TouchableOpacity>
          )}
        />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.canvas },
  header: { flexDirection: 'row', alignItems: 'center', padding: 16, paddingBottom: 4 },
  kicker: { color: colors.inkSoft, fontSize: 13, fontWeight: '600' },
  title: { color: colors.ink, fontSize: 22, fontWeight: '800', marginTop: 2 },
  signOut: { flexDirection: 'row', alignItems: 'center', gap: 6, padding: 10 },
  signOutText: { color: colors.inkSoft, fontWeight: '700' },
  error: { color: colors.danger, marginBottom: 8 },
  empty: { alignItems: 'center', padding: 40, gap: 10 },
  emptyText: { color: colors.inkSoft, textAlign: 'center' },
  card: { backgroundColor: colors.surface, borderRadius: 14, borderWidth: 1, borderColor: colors.line, padding: 16, gap: 6 },
  cardTop: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  ref: { fontWeight: '800', color: colors.ink, fontSize: 16 },
  badge: { fontSize: 12, fontWeight: '700', color: colors.warningOn, backgroundColor: colors.warningSubtle, paddingHorizontal: 10, paddingVertical: 4, borderRadius: 999, overflow: 'hidden' },
  badgeLive: { color: colors.info, backgroundColor: colors.infoSubtle },
  address: { color: colors.ink, fontSize: 15 },
  meta: { color: colors.inkMuted, fontSize: 13 },
});
