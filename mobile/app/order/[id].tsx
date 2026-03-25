import { useEffect, useState } from 'react';
import { View, Text, StyleSheet, ActivityIndicator, ScrollView, TouchableOpacity, Alert } from 'react-native';
import { useLocalSearchParams, router } from 'expo-router';
import { fetchOrder, cancelOrder, fetchReceipt } from '../../src/api/orders';
import type { Order, Receipt } from '../../src/types/api';

const STATUS_STEPS = ['PENDING', 'CONFIRMED', 'PREPARING', 'READY', 'COMPLETED'];
const STATUS_COLORS: Record<string, string> = {
  PENDING: '#F59E0B',
  CONFIRMED: '#3B82F6',
  PREPARING: '#8B5CF6',
  READY: '#10B981',
  COMPLETED: '#6B7280',
  CANCELLED: '#EF4444',
  VOIDED: '#EF4444',
};

export default function OrderTrackingScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const [order, setOrder] = useState<Order | null>(null);
  const [receipt, setReceipt] = useState<Receipt | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    load();
    const interval = setInterval(load, 15000);
    return () => clearInterval(interval);
  }, [id]);

  async function load() {
    try {
      const nextOrder = await fetchOrder(id);
      setOrder(nextOrder);
      if (nextOrder.status === 'COMPLETED') {
        fetchReceipt(id).then(setReceipt).catch(() => {});
      }
    } finally {
      setLoading(false);
    }
  }

  async function handleCancel() {
    Alert.alert('Cancel Order', 'Cancel this order?', [
      { text: 'No', style: 'cancel' },
      {
        text: 'Yes',
        style: 'destructive',
        onPress: async () => {
          try {
            await cancelOrder(id);
            load();
          } catch (e: any) {
            Alert.alert('Error', e?.response?.data?.message ?? 'Cannot cancel');
          }
        },
      },
    ]);
  }

  if (loading) {
    return <ActivityIndicator size="large" color="#2563EB" style={{ flex: 1, marginTop: 40 }} />;
  }

  if (!order) {
    return (
      <View style={styles.center}>
        <Text style={styles.error}>Order not found</Text>
      </View>
    );
  }

  const stepIdx = STATUS_STEPS.indexOf(order.status);

  return (
    <ScrollView style={styles.container}>
      <View style={styles.header}>
        <Text style={styles.orderId}>Order #{order.id.slice(0, 8)}</Text>
        <View style={[styles.statusBadge, { backgroundColor: STATUS_COLORS[order.status] ?? '#6B7280' }]}>
          <Text style={styles.statusText}>{order.status}</Text>
        </View>
      </View>

      {stepIdx >= 0 && (
        <View style={styles.progressContainer}>
          {STATUS_STEPS.map((step, index) => (
            <View key={step} style={styles.progressStep}>
              <View style={[styles.dot, index <= stepIdx && styles.dotActive]} />
              <Text style={[styles.stepLabel, index <= stepIdx && styles.stepLabelActive]}>
                {step}
              </Text>
            </View>
          ))}
        </View>
      )}

      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Items</Text>
        {order.items.map((item) => (
          <View key={item.id} style={styles.itemRow}>
            <View style={{ flex: 1 }}>
              <Text style={styles.itemName}>{item.menuItemName} x{item.quantity}</Text>
              {item.modifiers && item.modifiers.length > 0 ? (
                <Text style={styles.itemModifier}>
                  {item.modifiers.map((modifier) => `${modifier.groupName}: ${modifier.optionName}`).join(', ')}
                </Text>
              ) : null}
            </View>
            <Text style={styles.itemPrice}>GHS {item.totalPrice}</Text>
          </View>
        ))}
        <View style={styles.totalLine}>
          <Text style={styles.totalLabel}>Total</Text>
          <Text style={styles.totalValue}>GHS {order.total}</Text>
        </View>
      </View>

      {receipt && (
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Receipt</Text>
          <Text style={styles.receiptNum}>{receipt.receiptNumber}</Text>
          <Text style={styles.receiptMeta}>
            Paid via {receipt.paymentMethod} on {new Date(receipt.paidAt).toLocaleString()}
          </Text>
        </View>
      )}

      {order.status === 'PENDING' && (
        <TouchableOpacity style={styles.cancelBtn} onPress={handleCancel}>
          <Text style={styles.cancelText}>Cancel Order</Text>
        </TouchableOpacity>
      )}

      <TouchableOpacity style={styles.backBtn} onPress={() => router.replace('/(tabs)/orders')}>
        <Text style={styles.backBtnText}>Back to Orders</Text>
      </TouchableOpacity>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#F9FAFB' },
  center: { flex: 1, justifyContent: 'center', alignItems: 'center' },
  error: { fontSize: 16, color: '#374151' },
  header: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', padding: 16, backgroundColor: '#fff' },
  orderId: { fontSize: 18, fontWeight: '700', color: '#111827' },
  statusBadge: { paddingHorizontal: 12, paddingVertical: 6, borderRadius: 20 },
  statusText: { color: '#fff', fontWeight: '600', fontSize: 13 },
  progressContainer: {
    flexDirection: 'row',
    backgroundColor: '#fff',
    paddingHorizontal: 16,
    paddingVertical: 12,
    marginVertical: 8,
    justifyContent: 'space-between',
  },
  progressStep: { alignItems: 'center', flex: 1 },
  dot: { width: 14, height: 14, borderRadius: 7, backgroundColor: '#E5E7EB', marginBottom: 4 },
  dotActive: { backgroundColor: '#2563EB' },
  stepLabel: { fontSize: 9, color: '#9CA3AF', textAlign: 'center' },
  stepLabelActive: { color: '#2563EB', fontWeight: '600' },
  section: { backgroundColor: '#fff', margin: 12, borderRadius: 12, padding: 16 },
  sectionTitle: { fontSize: 15, fontWeight: '700', color: '#111827', marginBottom: 12 },
  itemRow: { flexDirection: 'row', justifyContent: 'space-between', marginBottom: 8 },
  itemName: { fontSize: 14, color: '#374151' },
  itemModifier: { fontSize: 11, color: '#6B7280', marginTop: 2 },
  itemPrice: { fontSize: 14, fontWeight: '600', color: '#111827' },
  totalLine: { flexDirection: 'row', justifyContent: 'space-between', borderTopWidth: 1, borderTopColor: '#E5E7EB', paddingTop: 8, marginTop: 4 },
  totalLabel: { fontSize: 15, fontWeight: '700', color: '#111827' },
  totalValue: { fontSize: 16, fontWeight: '800', color: '#2563EB' },
  receiptNum: { fontSize: 14, fontWeight: '600', color: '#111827', marginBottom: 4 },
  receiptMeta: { fontSize: 12, color: '#6B7280' },
  cancelBtn: { margin: 12, padding: 14, backgroundColor: '#FEE2E2', borderRadius: 10, alignItems: 'center' },
  cancelText: { color: '#EF4444', fontWeight: '700', fontSize: 15 },
  backBtn: { margin: 12, padding: 14, backgroundColor: '#E5E7EB', borderRadius: 10, alignItems: 'center' },
  backBtnText: { color: '#374151', fontWeight: '600', fontSize: 15 },
});
