import { useEffect, useState } from 'react';
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
import { fetchMenuItem, fetchMenuItemModifiers } from '../src/api/menu';
import { quoteCart, type CartQuote } from '../src/api/quote';
import { useAuthStore } from '../src/store/auth';
import { ServerTotals } from '../src/features/billing/ServerTotals';
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
    tableToken,
    addItem,
  } = useCartStore();
  const [promoInput, setPromoInput] = useState('');
  const [promoLoading, setPromoLoading] = useState(false);
  const [offerQuote, setOfferQuote] = useState<CartQuote | null>(null);
  const signedInCustomer = useAuthStore((st) => st.isAuthenticated && st.role === 'CUSTOMER');

  // Price the cart on the server (offers, then promo, then charges and tax), and get suggestions to complete a deal.
  const cartKey = lines.map((line) => `${line.key}x${line.quantity}`).join('|');
  useEffect(() => {
    if (lines.length === 0) {
      setOfferQuote(null);
      return;
    }
    let cancelled = false;
    const timer = setTimeout(() => {
      quoteCart(
        {
          type: tableToken ? 'DINE_IN' : 'PICKUP',
          tableToken: tableToken ?? undefined,
          items: lines.map((line) => ({ menuItemId: line.item.id, quantity: line.quantity, modifierOptionIds: line.modifierOptionIds })),
          promoCode: promoCode ?? undefined,
        },
        signedInCustomer,
      )
        .then((quote) => { if (!cancelled) setOfferQuote(quote); })
        .catch(() => { if (!cancelled) setOfferQuote(null); });
    }, 300);
    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, [cartKey, tableToken, promoCode, signedInCustomer]);

  /** Add what a deal needs: more of a line already in the cart, or a dish without required choices. */
  async function addForHint(menuItemId: string, quantity: number) {
    const existing = lines.find((line) => line.item.id === menuItemId);
    if (existing) {
      for (let i = 0; i < quantity; i++) {
        addItem(existing.item, {
          notes: existing.notes,
          modifierOptionIds: existing.modifierOptionIds,
          modifierSummary: existing.modifierSummary,
          unitPrice: existing.unitPrice,
        });
      }
      return;
    }
    try {
      const [item, groups] = await Promise.all([fetchMenuItem(menuItemId), fetchMenuItemModifiers(menuItemId)]);
      if (groups.some((group) => group.required)) {
        Alert.alert('Choose your options', `Add ${item.name} from the menu to pick its options.`);
        router.push('/(tabs)/menu');
        return;
      }
      for (let i = 0; i < quantity; i++) addItem(item);
    } catch {
      Alert.alert('Could not add', 'Try adding it from the menu.');
    }
  }

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
  // The server's figure when we have it; otherwise the phone's own estimate.
  const grandTotal = offerQuote ? offerQuote.total : total();

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
        {offerQuote?.offerHints.map((hint) => (
          <View key={`${hint.offerId}-${hint.menuItemId}`} style={styles.hint}>
            <Text style={styles.hintText}>{hint.message}</Text>
            <TouchableOpacity
              style={styles.hintBtn}
              onPress={() => void addForHint(hint.menuItemId, hint.quantity)}
              accessibilityRole="button"
              accessibilityLabel={hint.message}
            >
              <Text style={styles.hintBtnText}>Add</Text>
            </TouchableOpacity>
          </View>
        ))}
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
        {offerQuote?.promoRejected ? (
          <Text style={styles.promoRejected}>Promo {promoCode} can&apos;t be used on this order.</Text>
        ) : null}
        {offerQuote ? (
          <ServerTotals
            quote={offerQuote}
            rowStyle={styles.totalRow}
            labelStyle={styles.totalLabel}
            valueStyle={styles.totalValue}
            discountColor={colors.success}
          />
        ) : null}
        {!offerQuote && discount > 0 && (
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
  hint: {
    flexDirection: 'row', alignItems: 'center', gap: 8, backgroundColor: colors.brandSubtle,
    borderRadius: 10, padding: 10, marginBottom: 8,
  },
  hintText: { flex: 1, fontSize: 13, fontWeight: '600', color: colors.ink },
  hintBtn: { backgroundColor: colors.brand, borderRadius: 8, paddingHorizontal: 14, paddingVertical: 8, minHeight: 36, justifyContent: 'center' },
  hintBtnText: { color: colors.white, fontWeight: '700', fontSize: 13 },
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
  promoRejected: { color: colors.danger, fontSize: 12, marginBottom: 8 },
  totalRow: { flexDirection: 'row', justifyContent: 'space-between', marginBottom: 6 },
  totalLabel: { fontSize: 14, color: colors.inkMuted },
  totalValue: { fontSize: 14, color: colors.inkSoft, fontWeight: '600' },
  grandTotalRow: { borderTopWidth: 1, borderTopColor: colors.line, paddingTop: 8, marginTop: 4, marginBottom: 12 },
  grandTotalLabel: { fontSize: 16, fontWeight: '700', color: colors.ink },
  grandTotalValue: { fontSize: 18, fontWeight: '800', color: colors.brand },
  checkoutBtn: { backgroundColor: colors.brand, borderRadius: 10, padding: 16, alignItems: 'center' },
  checkoutBtnText: { color: colors.white, fontSize: 16, fontWeight: '700' },
});
