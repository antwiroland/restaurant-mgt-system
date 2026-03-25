import { useState } from 'react';
import {
  View,
  Text,
  FlatList,
  TouchableOpacity,
  TextInput,
  StyleSheet,
  Alert,
  ActivityIndicator,
} from 'react-native';
import { router } from 'expo-router';
import { useCartStore } from '../src/store/cart';
import { validatePromoCode } from '../src/api/loyalty';

export default function CartScreen() {
  const {
    lines,
    removeItem,
    setQty,
    subtotal,
    total,
    discountAmount,
    promoCode,
    promoType,
    promoValue,
    setPromo,
    clearPromo,
    tableNumber,
  } = useCartStore();
  const [promoInput, setPromoInput] = useState('');
  const [promoLoading, setPromoLoading] = useState(false);

  async function applyPromo() {
    if (!promoInput.trim()) return;
    setPromoLoading(true);
    try {
      const res = await validatePromoCode(promoInput.trim());
      if (!res.valid) {
        Alert.alert('Invalid promo', 'Code not valid or expired');
        return;
      }

      setPromo(res);
      setPromoInput('');
      Alert.alert(
        'Promo applied',
        res.discountType === 'PERCENTAGE'
          ? `${res.discountValue}% off your order`
          : `GHS ${res.discountValue} off your order`
      );
    } catch {
      Alert.alert('Invalid promo', 'Code not valid or expired');
    } finally {
      setPromoLoading(false);
    }
  }

  const subtotalAmount = subtotal();
  const discount = discountAmount();
  const grandTotal = total();

  if (lines.length === 0) {
    return (
      <View style={styles.center}>
        <Text style={styles.emptyText}>Your cart is empty</Text>
        <TouchableOpacity onPress={() => router.back()}>
          <Text style={styles.backText}>Browse Menu</Text>
        </TouchableOpacity>
      </View>
    );
  }

  return (
    <View style={styles.container}>
      {tableNumber && (
        <View style={styles.tableInfo}>
          <Text style={styles.tableInfoText}>Dining at Table {tableNumber}</Text>
        </View>
      )}

      <FlatList
        data={lines}
        keyExtractor={(line) => line.key}
        renderItem={({ item: line }) => (
          <View style={styles.item}>
            <View style={styles.itemInfo}>
              <Text style={styles.itemName}>{line.item.name}</Text>
              <Text style={styles.itemPrice}>
                GHS {(parseFloat(line.unitPrice ?? line.item.price) * line.quantity).toFixed(2)}
              </Text>
            </View>
            {line.modifierSummary && line.modifierSummary.length > 0 ? (
              <Text style={styles.modifierText}>{line.modifierSummary.join(', ')}</Text>
            ) : null}
            <View style={styles.qtyRow}>
              <TouchableOpacity style={styles.qtyBtn} onPress={() => setQty(line.key, line.quantity - 1)}>
                <Text style={styles.qtyBtnText}>-</Text>
              </TouchableOpacity>
              <Text style={styles.qty}>{line.quantity}</Text>
              <TouchableOpacity style={styles.qtyBtn} onPress={() => setQty(line.key, line.quantity + 1)}>
                <Text style={styles.qtyBtnText}>+</Text>
              </TouchableOpacity>
              <TouchableOpacity style={styles.removeBtn} onPress={() => removeItem(line.key)}>
                <Text style={styles.removeBtnText}>Remove</Text>
              </TouchableOpacity>
            </View>
          </View>
        )}
        contentContainerStyle={{ padding: 12 }}
      />

      <View style={styles.footer}>
        <View style={styles.promoRow}>
          <TextInput
            style={styles.promoInput}
            placeholder="Promo code"
            value={promoInput}
            onChangeText={setPromoInput}
            autoCapitalize="characters"
          />
          <TouchableOpacity style={styles.promoBtn} onPress={applyPromo} disabled={promoLoading}>
            {promoLoading ? <ActivityIndicator color="#fff" size="small" /> : <Text style={styles.promoBtnText}>Apply</Text>}
          </TouchableOpacity>
        </View>
        {promoCode && (
          <TouchableOpacity onPress={clearPromo}>
            <Text style={styles.promoApplied}>
              Promo "{promoCode}" applied
              {promoType === 'PERCENTAGE' ? ` (${promoValue}%)` : promoValue ? ` (GHS ${promoValue})` : ''}
              {' '}remove
            </Text>
          </TouchableOpacity>
        )}
        <View style={styles.totalRow}>
          <Text style={styles.totalLabel}>Subtotal</Text>
          <Text style={styles.totalValue}>GHS {subtotalAmount.toFixed(2)}</Text>
        </View>
        {discount > 0 && (
          <View style={styles.totalRow}>
            <Text style={[styles.totalLabel, { color: '#10B981' }]}>Discount</Text>
            <Text style={[styles.totalValue, { color: '#10B981' }]}>-GHS {discount.toFixed(2)}</Text>
          </View>
        )}
        <View style={[styles.totalRow, styles.grandTotalRow]}>
          <Text style={styles.grandTotalLabel}>Total</Text>
          <Text style={styles.grandTotalValue}>GHS {grandTotal.toFixed(2)}</Text>
        </View>
        <TouchableOpacity style={styles.checkoutBtn} onPress={() => router.push('/checkout')}>
          <Text style={styles.checkoutBtnText}>Proceed to Checkout</Text>
        </TouchableOpacity>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#F9FAFB' },
  center: { flex: 1, justifyContent: 'center', alignItems: 'center', padding: 24 },
  emptyText: { fontSize: 18, fontWeight: '600', color: '#374151', marginBottom: 12 },
  backText: { color: '#2563EB', fontSize: 15 },
  tableInfo: { backgroundColor: '#EFF6FF', padding: 10, alignItems: 'center' },
  tableInfoText: { color: '#1D4ED8', fontWeight: '600' },
  item: {
    backgroundColor: '#fff',
    borderRadius: 10,
    padding: 14,
    marginBottom: 10,
    shadowColor: '#000',
    shadowOpacity: 0.04,
    shadowRadius: 3,
    elevation: 1,
  },
  itemInfo: { flexDirection: 'row', justifyContent: 'space-between', marginBottom: 8 },
  itemName: { fontSize: 15, fontWeight: '600', color: '#111827', flex: 1 },
  itemPrice: { fontSize: 15, fontWeight: '700', color: '#2563EB' },
  modifierText: { fontSize: 12, color: '#6B7280', marginBottom: 6 },
  qtyRow: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  qtyBtn: {
    backgroundColor: '#E5E7EB',
    borderRadius: 6,
    width: 32,
    height: 32,
    justifyContent: 'center',
    alignItems: 'center',
  },
  qtyBtnText: { fontSize: 18, fontWeight: '700', color: '#374151' },
  qty: { fontSize: 16, fontWeight: '600', minWidth: 24, textAlign: 'center' },
  removeBtn: { marginLeft: 'auto' },
  removeBtnText: { color: '#EF4444', fontSize: 13 },
  footer: { padding: 16, backgroundColor: '#fff', borderTopWidth: 1, borderTopColor: '#E5E7EB' },
  promoRow: { flexDirection: 'row', gap: 8, marginBottom: 8 },
  promoInput: {
    flex: 1,
    borderWidth: 1,
    borderColor: '#D1D5DB',
    borderRadius: 8,
    paddingHorizontal: 12,
    paddingVertical: 10,
    fontSize: 14,
  },
  promoBtn: { backgroundColor: '#2563EB', borderRadius: 8, paddingHorizontal: 16, justifyContent: 'center' },
  promoBtnText: { color: '#fff', fontWeight: '600', fontSize: 14 },
  promoApplied: { color: '#10B981', fontSize: 12, marginBottom: 8 },
  totalRow: { flexDirection: 'row', justifyContent: 'space-between', marginBottom: 6 },
  totalLabel: { fontSize: 14, color: '#6B7280' },
  totalValue: { fontSize: 14, color: '#374151', fontWeight: '600' },
  grandTotalRow: { borderTopWidth: 1, borderTopColor: '#E5E7EB', paddingTop: 8, marginTop: 4, marginBottom: 12 },
  grandTotalLabel: { fontSize: 16, fontWeight: '700', color: '#111827' },
  grandTotalValue: { fontSize: 18, fontWeight: '800', color: '#2563EB' },
  checkoutBtn: { backgroundColor: '#2563EB', borderRadius: 10, padding: 16, alignItems: 'center' },
  checkoutBtnText: { color: '#fff', fontSize: 16, fontWeight: '700' },
});
