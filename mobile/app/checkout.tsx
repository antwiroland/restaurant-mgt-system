import { useState } from 'react';
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  StyleSheet,
  Alert,
  ActivityIndicator,
  ScrollView,
} from 'react-native';
import { router } from 'expo-router';
import { useCartStore } from '../src/store/cart';
import { useOfflineStore } from '../src/store/offline';
import { createOrder } from '../src/api/orders';
import { initiatePayment } from '../src/api/payment';
import type { PaymentMethod, OrderType } from '../src/types/api';

export default function CheckoutScreen() {
  const { lines, tableId, promoCode, total, clear } = useCartStore();
  const { isOnline, enqueue } = useOfflineStore();
  const [method, setMethod] = useState<PaymentMethod>('MOBILE_MONEY');
  const [momoPhone, setMomoPhone] = useState('');
  const [orderType, setOrderType] = useState<OrderType>('DINE_IN');
  const [deliveryAddress, setDeliveryAddress] = useState('');
  const [loading, setLoading] = useState(false);

  const effectiveOrderType = tableId ? 'DINE_IN' : orderType;
  const orderPayload = {
    type: effectiveOrderType as OrderType,
    tableId: tableId ?? undefined,
    items: lines.map((line) => ({ menuItemId: line.item.id, quantity: line.quantity, notes: line.notes })),
    promoCode: promoCode ?? undefined,
    deliveryAddress: effectiveOrderType === 'DELIVERY' ? deliveryAddress.trim() : undefined,
  };

  async function handlePlaceOrder() {
    if (lines.length === 0) {
      Alert.alert('Cart empty', 'Add at least one item before checkout.');
      return;
    }

    if (effectiveOrderType === 'DELIVERY' && !deliveryAddress.trim()) {
      Alert.alert('Missing address', 'Enter a delivery address before placing the order.');
      return;
    }

    if (!isOnline) {
      await enqueue('CREATE_ORDER', orderPayload);
      Alert.alert('Order queued', 'Order saved. It will be placed when you reconnect.', [
        { text: 'OK', onPress: () => { clear(); router.replace('/(tabs)/orders'); } },
      ]);
      return;
    }

    if (method === 'MOBILE_MONEY' && !momoPhone.trim()) {
      Alert.alert('Missing phone number', 'Enter your MoMo phone number.');
      return;
    }

    setLoading(true);
    try {
      const order = await createOrder(orderPayload);
      const payment = await initiatePayment(
        order.id,
        method,
        method === 'MOBILE_MONEY' ? momoPhone.trim() : undefined
      );
      clear();
      Alert.alert('Payment initiated', payment.message, [
        { text: 'Track Order', onPress: () => router.replace(`/order/${order.id}`) },
      ]);
    } catch (e: any) {
      Alert.alert('Error', e?.response?.data?.message ?? 'Could not place order. Please try again.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <ScrollView style={styles.container}>
      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Order Type</Text>
        {(['DINE_IN', 'PICKUP', 'DELIVERY'] as OrderType[]).map((type) => (
          <TouchableOpacity
            key={type}
            style={[styles.typeOption, orderType === type && styles.typeOptionActive]}
            onPress={() => !tableId && setOrderType(type)}
          >
            <Text style={[styles.typeText, orderType === type && styles.typeTextActive]}>
              {type.replace('_', ' ')}
            </Text>
          </TouchableOpacity>
        ))}
        {tableId && <Text style={styles.hint}>Table linked, so dine-in is selected.</Text>}
      </View>

      {effectiveOrderType === 'DELIVERY' && (
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Delivery Address</Text>
          <TextInput
            style={styles.input}
            placeholder="Enter delivery address"
            value={deliveryAddress}
            onChangeText={setDeliveryAddress}
            multiline
          />
        </View>
      )}

      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Payment Method</Text>
        {(['MOBILE_MONEY', 'CARD'] as PaymentMethod[]).map((paymentMethod) => (
          <TouchableOpacity
            key={paymentMethod}
            style={[styles.typeOption, method === paymentMethod && styles.typeOptionActive]}
            onPress={() => setMethod(paymentMethod)}
          >
            <Text style={[styles.typeText, method === paymentMethod && styles.typeTextActive]}>
              {paymentMethod === 'MOBILE_MONEY' ? 'Mobile Money (MoMo)' : 'Card'}
            </Text>
          </TouchableOpacity>
        ))}
      </View>

      {method === 'MOBILE_MONEY' && (
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>MoMo Phone Number</Text>
          <TextInput
            style={styles.input}
            placeholder="+233201234567"
            value={momoPhone}
            onChangeText={setMomoPhone}
            keyboardType="phone-pad"
          />
        </View>
      )}

      <View style={styles.section}>
        <View style={styles.summaryRow}>
          <Text style={styles.summaryLabel}>Items</Text>
          <Text style={styles.summaryValue}>{lines.length}</Text>
        </View>
        <View style={styles.summaryRow}>
          <Text style={styles.summaryLabelBold}>Total</Text>
          <Text style={styles.summaryValueBold}>GHS {total().toFixed(2)}</Text>
        </View>
      </View>

      {!isOnline && (
        <View style={styles.offlineWarning}>
          <Text style={styles.offlineText}>You're offline. Order will be queued and submitted when reconnected.</Text>
        </View>
      )}

      <TouchableOpacity style={styles.placeBtn} onPress={handlePlaceOrder} disabled={loading}>
        {loading ? <ActivityIndicator color="#fff" /> : <Text style={styles.placeBtnText}>Place Order</Text>}
      </TouchableOpacity>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#F9FAFB' },
  section: {
    backgroundColor: '#fff',
    margin: 12,
    borderRadius: 12,
    padding: 16,
    shadowColor: '#000',
    shadowOpacity: 0.04,
    shadowRadius: 3,
    elevation: 1,
  },
  sectionTitle: { fontSize: 15, fontWeight: '700', color: '#111827', marginBottom: 12 },
  typeOption: {
    borderWidth: 1,
    borderColor: '#E5E7EB',
    borderRadius: 8,
    padding: 12,
    marginBottom: 8,
  },
  typeOptionActive: { borderColor: '#2563EB', backgroundColor: '#EFF6FF' },
  typeText: { fontSize: 14, color: '#374151' },
  typeTextActive: { color: '#1D4ED8', fontWeight: '600' },
  hint: { fontSize: 12, color: '#6B7280', marginTop: 4 },
  input: {
    borderWidth: 1,
    borderColor: '#D1D5DB',
    borderRadius: 8,
    padding: 12,
    fontSize: 15,
  },
  summaryRow: { flexDirection: 'row', justifyContent: 'space-between', marginBottom: 6 },
  summaryLabel: { fontSize: 14, color: '#6B7280' },
  summaryValue: { fontSize: 14, color: '#374151' },
  summaryLabelBold: { fontSize: 16, fontWeight: '700', color: '#111827' },
  summaryValueBold: { fontSize: 18, fontWeight: '800', color: '#2563EB' },
  offlineWarning: { margin: 12, backgroundColor: '#FEF3C7', borderRadius: 8, padding: 12 },
  offlineText: { color: '#92400E', fontSize: 13 },
  placeBtn: {
    margin: 12,
    backgroundColor: '#2563EB',
    borderRadius: 12,
    padding: 18,
    alignItems: 'center',
  },
  placeBtnText: { color: '#fff', fontSize: 17, fontWeight: '700' },
});
