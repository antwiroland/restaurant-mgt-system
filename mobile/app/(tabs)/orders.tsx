import { useState, useCallback } from 'react';
import { View, Text, FlatList, TouchableOpacity, StyleSheet, ActivityIndicator, RefreshControl, Alert } from 'react-native';
import { router, useFocusEffect } from 'expo-router';
import { fetchMyOrders } from '../../src/api/orders';
import { useAuthStore } from '../../src/store/auth';
import { useCartStore } from '../../src/store/cart';
import type { Order } from '../../src/types/api';

const STATUS_COLORS: Record<string, string> = {
  PENDING: '#F59E0B',
  CONFIRMED: '#3B82F6',
  PREPARING: '#8B5CF6',
  READY: '#10B981',
  COMPLETED: '#6B7280',
  CANCELLED: '#EF4444',
  VOIDED: '#EF4444',
};

export default function OrdersScreen() {
  const [orders, setOrders] = useState<Order[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const { isAuthenticated, isGuest } = useAuthStore();
  const { hydrateFromOrder } = useCartStore();

  useFocusEffect(useCallback(() => {
    load();
  }, [isAuthenticated]));

  async function load(isRefresh = false) {
    if (!isAuthenticated) {
      setLoading(false);
      return;
    }

    if (isRefresh) {
      setRefreshing(true);
    } else {
      setLoading(true);
    }

    try {
      const data = await fetchMyOrders();
      setOrders(data);
    } catch {
      Alert.alert('Could not load orders', 'Pull to retry or check your connection.');
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }

  function handleReorder(order: Order) {
    if (order.items.length === 0) {
      Alert.alert('Cannot reorder', 'This order has no items to add back to the cart.');
      return;
    }

    hydrateFromOrder(order.items, {
      tableId: order.type === 'DINE_IN' ? order.tableId ?? null : null,
      tableNumber: order.type === 'DINE_IN' ? order.tableNumber ?? null : null,
    });
    router.push('/cart');
  }

  if (!isAuthenticated && !isGuest) {
    return (
      <View style={styles.center}>
        <Text style={styles.emptyText}>Sign in to view your orders</Text>
        <TouchableOpacity style={styles.signInBtn} onPress={() => router.push('/(auth)/login')}>
          <Text style={styles.signInText}>Sign In</Text>
        </TouchableOpacity>
      </View>
    );
  }

  if (!isAuthenticated) {
    return (
      <View style={styles.center}>
        <Text style={styles.emptyText}>Guest orders are not saved.</Text>
        <Text style={styles.subText}>Sign in to track your orders.</Text>
      </View>
    );
  }

  return (
    <View style={styles.container}>
      {loading ? (
        <ActivityIndicator size="large" color="#2563EB" style={{ flex: 1 }} />
      ) : orders.length === 0 ? (
        <View style={styles.center}>
          <Text style={styles.emptyText}>No orders yet</Text>
        </View>
      ) : (
        <FlatList
          data={orders}
          keyExtractor={(order) => order.id}
          refreshControl={<RefreshControl refreshing={refreshing} onRefresh={() => load(true)} />}
          renderItem={({ item: order }) => (
            <TouchableOpacity style={styles.card} onPress={() => router.push(`/order/${order.id}`)}>
              <View style={styles.cardHeader}>
                <Text style={styles.orderId}>#{order.id.slice(0, 8)}</Text>
                <View style={[styles.badge, { backgroundColor: STATUS_COLORS[order.status] ?? '#6B7280' }]}>
                  <Text style={styles.badgeText}>{order.status}</Text>
                </View>
              </View>
              <Text style={styles.orderMeta}>
                {order.type} | {order.items.length} item(s) | GHS {order.total}
              </Text>
              <Text style={styles.orderDate}>{new Date(order.createdAt).toLocaleDateString()}</Text>
              <TouchableOpacity style={styles.reorderBtn} onPress={() => handleReorder(order)}>
                <Text style={styles.reorderText}>Reorder</Text>
              </TouchableOpacity>
            </TouchableOpacity>
          )}
          contentContainerStyle={{ padding: 12 }}
        />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#F9FAFB' },
  center: { flex: 1, justifyContent: 'center', alignItems: 'center', padding: 24 },
  emptyText: { fontSize: 18, fontWeight: '600', color: '#374151', marginBottom: 8 },
  subText: { fontSize: 14, color: '#6B7280' },
  signInBtn: { backgroundColor: '#2563EB', borderRadius: 8, paddingHorizontal: 24, paddingVertical: 12, marginTop: 16 },
  signInText: { color: '#fff', fontWeight: '600' },
  card: {
    backgroundColor: '#fff',
    borderRadius: 12,
    padding: 16,
    marginBottom: 12,
    shadowColor: '#000',
    shadowOpacity: 0.05,
    shadowRadius: 4,
    elevation: 2,
  },
  cardHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 4 },
  orderId: { fontSize: 15, fontWeight: '700', color: '#111827' },
  badge: { paddingHorizontal: 8, paddingVertical: 3, borderRadius: 12 },
  badgeText: { color: '#fff', fontSize: 11, fontWeight: '600' },
  orderMeta: { fontSize: 13, color: '#374151', marginBottom: 4 },
  orderDate: { fontSize: 12, color: '#9CA3AF', marginBottom: 8 },
  reorderBtn: { borderWidth: 1, borderColor: '#2563EB', borderRadius: 6, padding: 8, alignItems: 'center' },
  reorderText: { color: '#2563EB', fontSize: 13, fontWeight: '600' },
});
