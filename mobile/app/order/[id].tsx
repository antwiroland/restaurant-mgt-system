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
import { colors } from '../../src/theme';

const STATUS_STEPS = ['PENDING', 'CONFIRMED', 'PREPARING', 'READY', 'COMPLETED'];
const DELIVERY_STEPS = ['PENDING', 'CONFIRMED', 'PREPARING', 'READY', 'OUT_FOR_DELIVERY', 'DELIVERED'];
const STATUS_COLORS: Record<string, string> = {
  PENDING: colors.warning,
  CONFIRMED: colors.brand,
  PREPARING: colors.gold,
  READY: colors.success,
  OUT_FOR_DELIVERY: colors.info,
  DELIVERED: colors.success,
  COMPLETED: colors.inkMuted,
  CANCELLED: colors.danger,
  VOIDED: colors.danger,
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
    return <ActivityIndicator size="large" color={colors.brand} style={{ flex: 1, marginTop: 40 }} />;
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
        <View style={[styles.statusBadge, { backgroundColor: STATUS_COLORS[order.status] ?? colors.inkMuted }]}>
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

      {order.type === 'PICKUP' && order.pickupCode && !['PENDING', 'CANCELLED', 'VOIDED'].includes(order.status) ? (
        <View style={styles.section} accessibilityLabel={`Pickup code ${order.pickupCode.split('').join(' ')}`}>
          <Text style={styles.sectionTitle}>Pickup code</Text>
          <Text style={styles.pickupCode} selectable>{order.pickupCode}</Text>
          <Text style={styles.deliveryHint}>
            {order.status === 'COMPLETED'
              ? 'Collected.'
              : 'Show this code at the counter to collect your order. Keep it to yourself: it is your proof of order.'}
          </Text>
        </View>
      ) : null}

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
  container: { flex: 1, backgroundColor: colors.canvas },
  center: { flex: 1, justifyContent: 'center', alignItems: 'center' },
  error: { fontSize: 16, color: colors.inkSoft },
  header: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', padding: 16, backgroundColor: colors.white },
  orderId: { fontSize: 18, fontWeight: '700', color: colors.ink },
  statusBadge: { paddingHorizontal: 12, paddingVertical: 6, borderRadius: 20 },
  statusText: { color: colors.white, fontWeight: '600', fontSize: 13 },
  progressContainer: {
    flexDirection: 'row',
    backgroundColor: colors.white,
    paddingHorizontal: 16,
    paddingVertical: 12,
    marginVertical: 8,
    justifyContent: 'space-between',
  },
  progressStep: { alignItems: 'center', flex: 1 },
  dot: { width: 14, height: 14, borderRadius: 7, backgroundColor: colors.line, marginBottom: 4 },
  dotActive: { backgroundColor: colors.brand },
  stepLabel: { fontSize: 9, color: colors.inkMuted, textAlign: 'center' },
  stepLabelActive: { color: colors.brand, fontWeight: '600' },
  section: { backgroundColor: colors.white, margin: 12, borderRadius: 12, padding: 16 },
  sectionTitle: { fontSize: 15, fontWeight: '700', color: colors.ink, marginBottom: 12 },
  pickupCode: { fontSize: 32, fontWeight: '800', letterSpacing: 4, color: colors.ink, fontFamily: 'monospace', marginBottom: 8 },
  itemRow: { flexDirection: 'row', justifyContent: 'space-between', marginBottom: 8 },
  itemName: { fontSize: 14, color: colors.inkSoft },
  itemModifier: { fontSize: 11, color: colors.inkMuted, marginTop: 2 },
  itemPrice: { fontSize: 14, fontWeight: '600', color: colors.ink },
  totalLine: { flexDirection: 'row', justifyContent: 'space-between', borderTopWidth: 1, borderTopColor: colors.line, paddingTop: 8, marginTop: 4 },
  totalLabel: { fontSize: 15, fontWeight: '700', color: colors.ink },
  totalValue: { fontSize: 16, fontWeight: '800', color: colors.brand },
  receiptNum: { fontSize: 14, fontWeight: '600', color: colors.ink, marginBottom: 4 },
  receiptMeta: { fontSize: 12, color: colors.inkMuted },
  cancelBtn: { margin: 12, padding: 14, backgroundColor: colors.dangerSubtle, borderRadius: 10, alignItems: 'center' },
  cancelText: { color: colors.danger, fontWeight: '700', fontSize: 15 },
  verifyBtn: { marginHorizontal: 12, padding: 14, backgroundColor: colors.brandSoft, borderRadius: 10, alignItems: 'center' },
  verifyText: { color: colors.brandPressed, fontWeight: '700', fontSize: 15 },
  backBtn: { margin: 12, padding: 14, backgroundColor: colors.line, borderRadius: 10, alignItems: 'center' },
  backBtnText: { color: colors.inkSoft, fontWeight: '600', fontSize: 15 },
  deliveryLine: { fontSize: 14, color: colors.inkSoft, marginBottom: 4 },
  deliveryHint: { fontSize: 12, color: colors.inkMuted, marginBottom: 8 },
  deliveryActions: { flexDirection: 'row', flexWrap: 'wrap', gap: 8, marginTop: 4 },
  deliveryButton: { backgroundColor: colors.brandSubtle, borderRadius: 8, paddingVertical: 12, paddingHorizontal: 14, minHeight: 44 },
  deliveryButtonText: { color: colors.brandPressed, fontWeight: '600' },
});
