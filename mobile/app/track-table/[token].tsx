import { useEffect, useState } from 'react';
import { ActivityIndicator, RefreshControl, ScrollView, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { router, useLocalSearchParams } from 'expo-router';
import { fetchPublicTableBill, fetchPublicTableTracking } from '../../src/api/orders';
import type { PublicOrderTracking, TableBill } from '../../src/types/api';

const STATUS_COLORS: Record<string, string> = {
  PENDING: '#F59E0B',
  CONFIRMED: '#3B82F6',
  PREPARING: '#8B5CF6',
  READY: '#10B981',
  COMPLETED: '#6B7280',
  CANCELLED: '#EF4444',
  VOIDED: '#EF4444',
};

export default function PublicTableTrackingScreen() {
  const { token } = useLocalSearchParams<{ token: string }>();
  const [orders, setOrders] = useState<PublicOrderTracking[]>([]);
  const [bill, setBill] = useState<TableBill | null>(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);

  useEffect(() => {
    void load();
    const interval = setInterval(() => void load(true), 15000);
    return () => clearInterval(interval);
  }, [token]);

  async function load(silent = false) {
    if (!token) {
      return;
    }
    if (!silent) {
      setLoading(true);
    }
    try {
      const [nextOrders, nextBill] = await Promise.all([
        fetchPublicTableTracking(token),
        fetchPublicTableBill(token),
      ]);
      setOrders(nextOrders);
      setBill(nextBill);
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }

  if (loading) {
    return <ActivityIndicator size="large" color="#2563EB" style={{ flex: 1, marginTop: 40 }} />;
  }

  return (
    <ScrollView
      style={styles.container}
      refreshControl={<RefreshControl refreshing={refreshing} onRefresh={() => { setRefreshing(true); void load(); }} />}
    >
      <View style={styles.section}>
        <Text style={styles.heading}>Table {bill?.tableNumber ?? 'Tracking'}</Text>
        <Text style={styles.meta}>Live guest dine-in tracking and running bill.</Text>
      </View>

      {bill && (
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Running Bill</Text>
          <View style={styles.row}><Text style={styles.label}>Status</Text><Text style={styles.value}>{bill.tableStatus}</Text></View>
          <View style={styles.row}><Text style={styles.label}>Active orders</Text><Text style={styles.value}>{bill.activeOrderCount}</Text></View>
          <View style={styles.row}><Text style={styles.label}>Ordered</Text><Text style={styles.value}>GHS {bill.totalOrdered}</Text></View>
          <View style={styles.row}><Text style={styles.label}>Paid</Text><Text style={styles.value}>GHS {bill.totalPaid}</Text></View>
          <View style={styles.row}><Text style={styles.labelStrong}>Outstanding</Text><Text style={styles.valueStrong}>GHS {bill.outstandingTotal}</Text></View>
        </View>
      )}

      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Table Orders</Text>
        {orders.length === 0 ? (
          <Text style={styles.empty}>No active dine-in orders for this table yet.</Text>
        ) : (
          orders.map((order) => (
            <View key={order.orderId} style={styles.orderCard}>
              <View style={styles.orderHeader}>
                <Text style={styles.orderId}>#{order.orderId.slice(0, 8)}</Text>
                <View style={[styles.badge, { backgroundColor: STATUS_COLORS[order.status] ?? '#6B7280' }]}>
                  <Text style={styles.badgeText}>{order.status}</Text>
                </View>
              </View>
              {order.notes ? <Text style={styles.notes}>Notes: {order.notes}</Text> : null}
              {order.cancelReason ? <Text style={styles.cancel}>Cancel reason: {order.cancelReason}</Text> : null}
              <Text style={styles.time}>Updated {new Date(order.updatedAt).toLocaleTimeString()}</Text>
            </View>
          ))
        )}
      </View>

      <TouchableOpacity style={styles.cta} onPress={() => router.replace('/(tabs)/menu')}>
        <Text style={styles.ctaText}>Back to Menu</Text>
      </TouchableOpacity>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#F9FAFB' },
  section: { backgroundColor: '#fff', margin: 12, borderRadius: 12, padding: 16 },
  heading: { fontSize: 22, fontWeight: '800', color: '#111827' },
  meta: { fontSize: 13, color: '#6B7280', marginTop: 4 },
  sectionTitle: { fontSize: 15, fontWeight: '700', color: '#111827', marginBottom: 12 },
  row: { flexDirection: 'row', justifyContent: 'space-between', marginBottom: 8 },
  label: { fontSize: 14, color: '#6B7280' },
  value: { fontSize: 14, color: '#111827', fontWeight: '600' },
  labelStrong: { fontSize: 16, color: '#111827', fontWeight: '700' },
  valueStrong: { fontSize: 18, color: '#2563EB', fontWeight: '800' },
  empty: { fontSize: 14, color: '#6B7280' },
  orderCard: { borderTopWidth: 1, borderTopColor: '#E5E7EB', paddingTop: 12, marginTop: 12 },
  orderHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 6 },
  orderId: { fontSize: 14, fontWeight: '700', color: '#111827' },
  badge: { paddingHorizontal: 10, paddingVertical: 4, borderRadius: 999 },
  badgeText: { color: '#fff', fontSize: 11, fontWeight: '700' },
  notes: { fontSize: 13, color: '#374151', marginBottom: 4 },
  cancel: { fontSize: 13, color: '#EF4444', marginBottom: 4 },
  time: { fontSize: 12, color: '#9CA3AF' },
  cta: { margin: 12, backgroundColor: '#2563EB', borderRadius: 12, padding: 16, alignItems: 'center' },
  ctaText: { color: '#fff', fontWeight: '700', fontSize: 16 },
});
