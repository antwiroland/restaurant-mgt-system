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
import { colors } from '../src/theme';

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
      const res = await validatePromoCode(promoInput.trim(), subtotal());
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
    } catch (error: any) {
      const message = error?.response?.data?.message ?? (error instanceof Error ? error.message : 'Code not valid or expired');
      Alert.alert('Invalid promo', message);
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
            {promoLoading ? <ActivityIndicator color={colors.white} size="small" /> : <Text style={styles.promoBtnText}>Apply</Text>}
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
            <Text style={[styles.totalLabel, { color: colors.success }]}>Discount</Text>
            <Text style={[styles.totalValue, { color: colors.success }]}>-GHS {discount.toFixed(2)}</Text>
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
  container: { flex: 1, backgroundColor: colors.canvas },
  center: { flex: 1, justifyContent: 'center', alignItems: 'center', padding: 24 },
  emptyText: { fontSize: 18, fontWeight: '600', color: colors.inkSoft, marginBottom: 12 },
  backText: { color: colors.brand, fontSize: 15 },
  tableInfo: { backgroundColor: colors.brandSubtle, padding: 10, alignItems: 'center' },
  tableInfoText: { color: colors.brandPressed, fontWeight: '600' },
  item: {
    backgroundColor: colors.white,
    borderRadius: 10,
    padding: 14,
    marginBottom: 10,
    shadowColor: colors.black,
    shadowOpacity: 0.04,
    shadowRadius: 3,
    elevation: 1,
  },
  itemInfo: { flexDirection: 'row', justifyContent: 'space-between', marginBottom: 8 },
  itemName: { fontSize: 15, fontWeight: '600', color: colors.ink, flex: 1 },
  itemPrice: { fontSize: 15, fontWeight: '700', color: colors.brand },
  modifierText: { fontSize: 12, color: colors.inkMuted, marginBottom: 6 },
  qtyRow: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  qtyBtn: {
    backgroundColor: colors.line,
    borderRadius: 6,
    width: 32,
    height: 32,
    justifyContent: 'center',
    alignItems: 'center',
  },
  qtyBtnText: { fontSize: 18, fontWeight: '700', color: colors.inkSoft },
  qty: { fontSize: 16, fontWeight: '600', minWidth: 24, textAlign: 'center' },
  removeBtn: { marginLeft: 'auto' },
  removeBtnText: { color: colors.danger, fontSize: 13 },
  footer: { padding: 16, backgroundColor: colors.white, borderTopWidth: 1, borderTopColor: colors.line },
  promoRow: { flexDirection: 'row', gap: 8, marginBottom: 8 },
  promoInput: {
    flex: 1,
    borderWidth: 1,
    borderColor: colors.lineStrong,
    borderRadius: 8,
    paddingHorizontal: 12,
    paddingVertical: 10,
    fontSize: 14,
  },
  promoBtn: { backgroundColor: colors.brand, borderRadius: 8, paddingHorizontal: 16, justifyContent: 'center' },
  promoBtnText: { color: colors.white, fontWeight: '600', fontSize: 14 },
  promoApplied: { color: colors.success, fontSize: 12, marginBottom: 8 },
  totalRow: { flexDirection: 'row', justifyContent: 'space-between', marginBottom: 6 },
  totalLabel: { fontSize: 14, color: colors.inkMuted },
  totalValue: { fontSize: 14, color: colors.inkSoft, fontWeight: '600' },
  grandTotalRow: { borderTopWidth: 1, borderTopColor: colors.line, paddingTop: 8, marginTop: 4, marginBottom: 12 },
  grandTotalLabel: { fontSize: 16, fontWeight: '700', color: colors.ink },
  grandTotalValue: { fontSize: 18, fontWeight: '800', color: colors.brand },
  checkoutBtn: { backgroundColor: colors.brand, borderRadius: 10, padding: 16, alignItems: 'center' },
  checkoutBtnText: { color: colors.white, fontSize: 16, fontWeight: '700' },
});
