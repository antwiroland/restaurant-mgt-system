import { useEffect, useRef, useState } from 'react';
import { View, Text, StyleSheet, ActivityIndicator, ScrollView, TouchableOpacity, Alert, Linking } from 'react-native';
import { useLocalSearchParams, router } from 'expo-router';
import { BillLines } from '../../src/components/BillLines';
import { RateOrder } from '../../src/components/RateOrder';
import { SplitShares } from '../../src/components/SplitShares';
import { useAuthStore } from '../../src/store/auth';
import { fetchOrder, cancelOrder, fetchReceipt } from '../../src/api/orders';
import { fetchPayment, verifyPayment } from '../../src/api/payment';
import type { Order, Receipt } from '../../src/types/api';
import { fallbackPollMs, useRealtimeTopics } from '../../src/realtime/realtime';
import { fetchDeliveryTracking, mapUrl, type DeliveryTracking } from '../../src/api/delivery';

const STATUS_STEPS = ['PENDING', 'CONFIRMED', 'PREPARING', 'READY', 'COMPLETED'];
const DELIVERY_STEPS = ['PENDING', 'CONFIRMED', 'PREPARING', 'READY', 'OUT_FOR_DELIVERY', 'DELIVERED'];
const STATUS_COLORS: Record<string, string> = {
  PENDING: '#F59E0B',
  CONFIRMED: '#3B82F6',
  PREPARING: '#8B5CF6',
  READY: '#10B981',
  OUT_FOR_DELIVERY: '#0EA5E9',
  DELIVERED: '#059669',
  COMPLETED: '#6B7280',
  CANCELLED: '#EF4444',
  VOIDED: '#EF4444',
};

export default function OrderTrackingScreen() {
  const { id, paymentId: initialPaymentId } = useLocalSearchParams<{ id: string; paymentId?: string }>();
  const [paymentId, setPaymentId] = useState<string | undefined>(initialPaymentId);
  const { userId, isAuthenticated } = useAuthStore();
  const [order, setOrder] = useState<Order | null>(null);
  const [receipt, setReceipt] = useState<Receipt | null>(null);
  const [loading, setLoading] = useState(true);
  const pendingPaymentPollsRef = useRef(0);
  const [delivery, setDelivery] = useState<DeliveryTracking | null>(null);

  // Live order and payment updates; polling remains as a slower fallback.
  const live = useRealtimeTopics(
    id ? [`/topic/orders.${id}`, `/topic/orders.${id}.payments`, `/topic/orders.${id}.delivery`] : [],
    () => void load(),
  );

  useEffect(() => {
    load();
    const interval = setInterval(load, fallbackPollMs(live, 15000));
    return () => clearInterval(interval);
  }, [id, paymentId, live]);

  async function load() {
    try {
      const nextOrder = await fetchOrder(id);
      setOrder(nextOrder);
      if (nextOrder.type === 'DELIVERY') {
        fetchDeliveryTracking(nextOrder.id).then(setDelivery).catch(() => undefined);
      }
      if (paymentId && ['PENDING', 'CONFIRMED', 'PREPARING'].includes(nextOrder.status)) {
        const payment = await fetchPayment(paymentId).catch(() => null);
        if (payment?.status === 'PENDING') {
          const nextPendingPolls = pendingPaymentPollsRef.current + 1;
          pendingPaymentPollsRef.current = nextPendingPolls;
          if (nextPendingPolls >= 2) {
            await verifyPayment(paymentId).catch(() => null);
            pendingPaymentPollsRef.current = 0;
          }
        } else {
          pendingPaymentPollsRef.current = 0;
        }
      }
      if (nextOrder.status === 'COMPLETED' || nextOrder.status === 'DELIVERED') {
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

  const steps = order.type === 'DELIVERY' ? DELIVERY_STEPS : STATUS_STEPS;
  const stepIdx = steps.indexOf(order.status);
  const riderLocation = delivery?.riderLatitude != null && delivery?.riderLongitude != null
    ? { lat: delivery.riderLatitude, lng: delivery.riderLongitude }
    : null;

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
          {steps.map((step, index) => (
            <View key={step} style={styles.progressStep}>
              <View style={[styles.dot, index <= stepIdx && styles.dotActive]} />
              <Text style={[styles.stepLabel, index <= stepIdx && styles.stepLabelActive]}>
                {step.replace(/_/g, ' ')}
              </Text>
            </View>
          ))}
        </View>
      )}

      {order.type === 'DELIVERY' && delivery ? (
        <View style={styles.section} accessibilityLiveRegion="polite">
          <Text style={styles.sectionTitle}>Delivery</Text>
          {delivery.deliveryAddress ? <Text style={styles.deliveryLine}>To: {delivery.deliveryAddress}</Text> : null}
          {delivery.estimatedDeliveryTime ? (
            <Text style={styles.deliveryLine}>
              Estimated arrival: {new Date(delivery.estimatedDeliveryTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
            </Text>
          ) : null}
          {delivery.riderName ? (
            <Text style={styles.deliveryLine}>Rider: {delivery.riderName}</Text>
          ) : (
            <Text style={styles.deliveryLine}>A rider will be assigned soon.</Text>
          )}
          {riderLocation ? (
            <Text style={styles.deliveryHint}>
              Rider location updated {delivery.riderLocationAt ? new Date(delivery.riderLocationAt).toLocaleTimeString() : 'recently'}
            </Text>
          ) : null}
          <View style={styles.deliveryActions}>
            {riderLocation ? (
              <TouchableOpacity
                style={styles.deliveryButton}
                accessibilityRole="link"
                onPress={() => void Linking.openURL(mapUrl(riderLocation.lat, riderLocation.lng))}
              >
                <Text style={styles.deliveryButtonText}>Track rider on map</Text>
              </TouchableOpacity>
            ) : null}
            {delivery.riderPhone ? (
              <TouchableOpacity
                style={styles.deliveryButton}
                accessibilityRole="button"
                onPress={() => void Linking.openURL(`tel:${delivery.riderPhone}`)}
              >
                <Text style={styles.deliveryButtonText}>Call rider</Text>
              </TouchableOpacity>
            ) : null}
          </View>
        </View>
      ) : null}

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
        <View style={{ marginTop: 8 }}>
          <BillLines
            subtotal={order.subtotal}
            discountTotal={order.discountTotal}
            deliveryFee={order.deliveryFee}
            serviceCharge={order.serviceCharge}
            taxLines={order.taxLines}
            taxTotal={order.taxTotal}
            taxInclusive={order.taxInclusive}
            tipAmount={order.tipAmount}
            total={order.total}
          />
        </View>
      </View>

      {isAuthenticated ? (
        <SplitShares order={order} userId={userId} onPaymentStarted={(nextPaymentId) => setPaymentId(nextPaymentId)} />
      ) : null}

      {isAuthenticated && (order.status === 'COMPLETED' || order.status === 'DELIVERED') ? (
        <RateOrder orderId={order.id} orderType={order.type} />
      ) : null}

      {receipt && (
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Receipt</Text>
          <Text style={styles.receiptNum}>{receipt.receiptNumber}</Text>
          <Text style={styles.receiptMeta}>
            Paid via {receipt.paymentMethod} on {new Date(receipt.paidAt).toLocaleString()}
          </Text>
        </View>
      )}

      {paymentId && order.status !== 'COMPLETED' && (
        <TouchableOpacity style={styles.verifyBtn} onPress={() => verifyPayment(paymentId).then(() => load()).catch(() => {})}>
          <Text style={styles.verifyText}>Verify Payment Status</Text>
        </TouchableOpacity>
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
  verifyBtn: { marginHorizontal: 12, padding: 14, backgroundColor: '#DBEAFE', borderRadius: 10, alignItems: 'center' },
  verifyText: { color: '#1D4ED8', fontWeight: '700', fontSize: 15 },
  backBtn: { margin: 12, padding: 14, backgroundColor: '#E5E7EB', borderRadius: 10, alignItems: 'center' },
  backBtnText: { color: '#374151', fontWeight: '600', fontSize: 15 },
  deliveryLine: { fontSize: 14, color: '#374151', marginBottom: 4 },
  deliveryHint: { fontSize: 12, color: '#6B7280', marginBottom: 8 },
  deliveryActions: { flexDirection: 'row', flexWrap: 'wrap', gap: 8, marginTop: 4 },
  deliveryButton: { backgroundColor: '#EFF6FF', borderRadius: 8, paddingVertical: 12, paddingHorizontal: 14, minHeight: 44 },
  deliveryButtonText: { color: '#1D4ED8', fontWeight: '600' },
});
