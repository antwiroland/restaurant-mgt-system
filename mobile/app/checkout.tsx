import { useEffect, useMemo, useState } from 'react';
import {
  Switch,
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
import { createOrder, createPublicTableOrder } from '../src/api/orders';
import { initiatePayment } from '../src/api/payment';
import { useAuthStore } from '../src/store/auth';
import { fetchMyAddresses, saveMyAddress } from '../src/api/user';
import { fetchLoyaltyBalance } from '../src/api/loyalty';
import { quoteCart, type CartQuote } from '../src/api/quote';
import { ServerTotals } from '../src/features/billing/ServerTotals';
import { fetchDeliveryZones, type DeliveryZone } from '../src/api/delivery';
import { fetchBillingConfig } from '../src/api/billing';
import { billingNote, TIP_CHOICES, tipAmountFor, type TipChoice } from '../src/features/billing/billing';
import type { BillingConfig, CustomerAddress, LoyaltyBalance, PaymentMethod, OrderType } from '../src/types/api';
import { colors } from '../src/theme';

export default function CheckoutScreen() {
  const { lines, tableId, tableToken, tableStatus, promoCode, total, clear } = useCartStore();
  const { isOnline, enqueue } = useOfflineStore();
  const { isGuest, isAuthenticated } = useAuthStore();
  const [method, setMethod] = useState<PaymentMethod>('MOBILE_MONEY');
  const [momoPhone, setMomoPhone] = useState('');
  const [orderType, setOrderType] = useState<OrderType>('DINE_IN');
  const [deliveryAddress, setDeliveryAddress] = useState('');
  const [addresses, setAddresses] = useState<CustomerAddress[]>([]);
  const [selectedAddressId, setSelectedAddressId] = useState<string | null>(null);
  const [loyaltyBalance, setLoyaltyBalance] = useState<LoyaltyBalance | null>(null);
  const [usePoints, setUsePoints] = useState(false);
  const [zones, setZones] = useState<DeliveryZone[]>([]);
  const [zoneId, setZoneId] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [billing, setBilling] = useState<BillingConfig | null>(null);
  const [tipChoice, setTipChoice] = useState<TipChoice>('NONE');
  const [customTip, setCustomTip] = useState('');

  useEffect(() => {
    fetchBillingConfig().then(setBilling).catch(() => setBilling(null));
  }, []);

  const [offerQuote, setOfferQuote] = useState<CartQuote | null>(null);

  const selectedAddress = useMemo(
    () => addresses.find((address) => address.id === selectedAddressId) ?? null,
    [addresses, selectedAddressId]
  );

  const effectiveOrderType = tableId ? 'DINE_IN' : orderType;
  // Guest table orders use the public endpoint, which does not take tips.
  const tipsAvailable = !!billing?.tipsEnabled && !isGuest;
  const tipAmount = tipsAvailable ? tipAmountFor(tipChoice, total(), customTip) : 0;
  const chargesNote = billingNote(billing, effectiveOrderType as OrderType);
  const orderPayload = {
    type: effectiveOrderType as OrderType,
    tableId: tableId ?? undefined,
    tableToken: tableToken ?? undefined,
    items: lines.map((line) => ({
      menuItemId: line.item.id,
      quantity: line.quantity,
      notes: line.notes,
      modifierOptionIds: line.modifierOptionIds,
    })),
    promoCode: promoCode ?? undefined,
    loyaltyPointsToRedeem: usePoints && loyaltyBalance && loyaltyBalance.points > 0 ? loyaltyBalance.points : undefined,
    deliveryAddress: effectiveOrderType === 'DELIVERY' ? deliveryAddress.trim() : undefined,
    deliveryZoneId: effectiveOrderType === 'DELIVERY' && zoneId ? zoneId : undefined,
    tipAmount: tipAmount > 0 ? tipAmount : undefined,
  };
  // Price the order exactly as the server will charge it (offers, promo, points, delivery, charges, tax, tip).
  const quoteKey = JSON.stringify(orderPayload);
  useEffect(() => {
    if (orderPayload.items.length === 0 || (orderPayload.type === 'DELIVERY' && !orderPayload.deliveryZoneId && zones.length > 0)) {
      setOfferQuote(null);
      return;
    }
    let cancelled = false;
    const timer = setTimeout(() => {
      quoteCart(orderPayload, isAuthenticated && !isGuest)
        .then((quote) => { if (!cancelled) setOfferQuote(quote); })
        .catch(() => { if (!cancelled) setOfferQuote(null); });
    }, 300);
    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
    // quoteKey captures everything the quote depends on.
  }, [quoteKey, isAuthenticated, isGuest, zones.length]);
  const selectedZone = zones.find((zone) => zone.id === zoneId) ?? null;
  const deliveryFee = effectiveOrderType === 'DELIVERY' && selectedZone ? Number(selectedZone.fee) : 0;

  useEffect(() => {
    if (!isAuthenticated) {
      setAddresses([]);
      setSelectedAddressId(null);
      setLoyaltyBalance(null);
      return;
    }

    fetchLoyaltyBalance().then(setLoyaltyBalance).catch(() => {});
  }, [isAuthenticated]);

  useEffect(() => {
    if (effectiveOrderType !== 'DELIVERY') return;
    fetchDeliveryZones().then(setZones).catch(() => setZones([]));
  }, [effectiveOrderType]);

  useEffect(() => {
    if (!isAuthenticated || effectiveOrderType !== 'DELIVERY') {
      return;
    }

    fetchMyAddresses()
      .then((items) => {
        setAddresses(items);
        const preferred = items.find((item) => item.isDefault) ?? items[0] ?? null;
        if (preferred) {
          setSelectedAddressId(preferred.id);
          setDeliveryAddress(formatAddress(preferred));
        }
      })
      .catch(() => {});
  }, [effectiveOrderType, isAuthenticated]);

  async function handlePlaceOrder() {
    if (lines.length === 0) {
      Alert.alert('Cart empty', 'Add at least one item before checkout.');
      return;
    }

    if (effectiveOrderType === 'DELIVERY' && !deliveryAddress.trim()) {
      Alert.alert('Missing address', 'Enter a delivery address before placing the order.');
      return;
    }

    if (effectiveOrderType === 'DELIVERY' && zones.length > 0 && !zoneId) {
      Alert.alert('Choose your area', 'Select the delivery zone closest to your address.');
      return;
    }

    if (!isOnline) {
      const isGuestTableOrder = isGuest && effectiveOrderType === 'DINE_IN' && !!tableToken;
      await enqueue(
        isGuestTableOrder ? 'CREATE_PUBLIC_TABLE_ORDER' : 'CREATE_ORDER',
        isGuestTableOrder
          ? { tableToken: tableToken ?? '', items: orderPayload.items }
          : orderPayload,
        effectiveOrderType === 'DINE_IN'
          ? {
              tableToken: tableToken ?? undefined,
              expectedTableStatus: tableStatus ?? undefined,
              expectedTableId: tableId ?? undefined,
            }
          : undefined
      );
      Alert.alert('Order queued', 'Order saved. It will be placed when you reconnect.', [
        { text: 'OK', onPress: () => { clear(); router.replace('/(tabs)/orders'); } },
      ]);
      return;
    }

    if (isGuest && effectiveOrderType === 'DINE_IN' && !tableToken) {
      Alert.alert('Missing table QR', 'Scan the table QR code before placing a guest dine-in order.');
      return;
    }

    if (method === 'MOBILE_MONEY' && !momoPhone.trim()) {
      Alert.alert('Missing phone number', 'Enter your MoMo phone number.');
      return;
    }

    setLoading(true);
    try {
      const order = isGuest && effectiveOrderType === 'DINE_IN'
        ? await createPublicTableOrder({
            tableToken: tableToken ?? '',
            items: orderPayload.items,
            promoCode: orderPayload.promoCode,
          })
        : await createOrder(orderPayload);

      if (isAuthenticated && effectiveOrderType === 'DELIVERY' && deliveryAddress.trim()) {
        const normalizedAddress = deliveryAddress.trim().toLowerCase();
        const exists = addresses.some((address) => formatAddress(address).trim().toLowerCase() === normalizedAddress);
        if (!exists) {
          const [addressLine, city] = splitAddress(deliveryAddress);
          const saved = await saveMyAddress({
            label: selectedAddress ? selectedAddress.label : 'Delivery Address',
            addressLine,
            city,
            landmark: undefined,
            isDefault: addresses.length === 0,
          }).catch(() => null);
          if (saved) {
            setAddresses((current) => [...current, saved]);
          }
        }
      }

      if (!isGuest) {
        const payment = await initiatePayment(
          order.id,
          method,
          method === 'MOBILE_MONEY' ? momoPhone.trim() : undefined
        );
        clear();
        const savings = Number(order.discountTotal ?? 0);
        const charges = [
          Number(order.serviceCharge ?? 0) > 0 ? `service charge GHS ${Number(order.serviceCharge).toFixed(2)}` : '',
          Number(order.taxTotal ?? 0) > 0 ? `tax GHS ${Number(order.taxTotal).toFixed(2)}${order.taxInclusive ? ' (included)' : ''}` : '',
          Number(order.tipAmount ?? 0) > 0 ? `tip GHS ${Number(order.tipAmount).toFixed(2)}` : '',
        ].filter(Boolean);
        const summary = [
          savings > 0 ? `You saved GHS ${savings.toFixed(2)}.` : '',
          `Total GHS ${Number(order.total).toFixed(2)}${charges.length ? ` incl. ${charges.join(', ')}` : ''}.`,
          payment.message,
        ].filter(Boolean).join('\n');
        Alert.alert('Payment initiated', summary, [
          {
            text: 'Track Order',
            onPress: () =>
              router.replace({
                pathname: '/order/[id]',
                params: { id: order.id, paymentId: payment.paymentId },
              }),
          },
        ]);
        return;
      } else {
        clear();
        Alert.alert('Order placed', `Your order for table ${order.tableNumber ?? ''} was sent to the kitchen.`, [
          {
            text: 'Track Table',
            onPress: () =>
              router.replace({
                pathname: '/track-table/[token]',
                params: { token: tableToken ?? '' },
              }),
          },
        ]);
        return;
      }
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
          {addresses.length > 0 && (
            <View style={styles.addressList}>
              {addresses.map((address) => {
                const active = selectedAddressId === address.id;
                return (
                  <TouchableOpacity
                    key={address.id}
                    style={[styles.addressCard, active && styles.addressCardActive]}
                    onPress={() => {
                      setSelectedAddressId(address.id);
                      setDeliveryAddress(formatAddress(address));
                    }}
                  >
                    <Text style={[styles.addressLabel, active && styles.addressLabelActive]}>{address.label}</Text>
                    <Text style={[styles.addressText, active && styles.addressTextActive]}>{formatAddress(address)}</Text>
                  </TouchableOpacity>
                );
              })}
            </View>
          )}
          <TextInput
            style={styles.input}
            placeholder="Enter delivery address"
            value={deliveryAddress}
            onChangeText={setDeliveryAddress}
            multiline
          />
          {zones.length > 0 && (
            <View style={styles.addressList}>
              <Text style={[styles.sectionTitle, { marginTop: 12 }]}>Delivery area</Text>
              {zones.map((zone) => {
                const active = zoneId === zone.id;
                return (
                  <TouchableOpacity
                    key={zone.id}
                    style={[styles.addressCard, active && styles.addressCardActive]}
                    onPress={() => setZoneId(zone.id)}
                    accessibilityRole="radio"
                    accessibilityState={{ selected: active }}
                  >
                    <Text style={[styles.addressLabel, active && styles.addressLabelActive]}>
                      {zone.name} · GHS {Number(zone.fee).toFixed(2)}
                    </Text>
                    <Text style={[styles.addressText, active && styles.addressTextActive]}>
                      About {zone.estimatedMinutes} min{Number(zone.minOrderAmount) > 0 ? ` · min order GHS ${Number(zone.minOrderAmount).toFixed(2)}` : ''}
                    </Text>
                  </TouchableOpacity>
                );
              })}
            </View>
          )}
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

      {tipsAvailable && (
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Add a tip</Text>
          <View style={styles.tipRow} accessibilityRole="radiogroup">
            {TIP_CHOICES.map((choice) => {
              const active = tipChoice === choice;
              return (
                <TouchableOpacity
                  key={choice}
                  style={[styles.tipChip, active && styles.typeOptionActive]}
                  onPress={() => setTipChoice(choice)}
                  accessibilityRole="radio"
                  accessibilityState={{ selected: active }}
                >
                  <Text style={[styles.typeText, active && styles.typeTextActive]}>
                    {choice === 'NONE' ? 'No tip' : choice === 'CUSTOM' ? 'Other' : `${choice}%`}
                  </Text>
                </TouchableOpacity>
              );
            })}
          </View>
          {tipChoice === 'CUSTOM' && (
            <TextInput
              style={styles.input}
              placeholder="Tip amount in GHS"
              value={customTip}
              onChangeText={setCustomTip}
              keyboardType="decimal-pad"
              accessibilityLabel="Custom tip amount in Ghana cedis"
            />
          )}
          <Text style={styles.hint}>Tips are never taxed.</Text>
        </View>
      )}

      <View style={styles.section}>
        {loyaltyBalance && isAuthenticated && (
          <View style={styles.loyaltyRow}>
            <Text style={styles.loyaltyHint}>
              Use {loyaltyBalance.points} loyalty points (up to half of the order)
            </Text>
            <Switch
              value={usePoints}
              onValueChange={setUsePoints}
              disabled={loyaltyBalance.points <= 0}
              accessibilityLabel="Redeem loyalty points"
            />
          </View>
        )}
        {promoCode ? (
          <Text style={styles.hint}>Promo {promoCode} will be applied when the order is placed.</Text>
        ) : null}
        <View style={styles.summaryRow}>
          <Text style={styles.summaryLabel}>Items</Text>
          <Text style={styles.summaryValue}>{lines.length}</Text>
        </View>
        {offerQuote ? (
          <>
            {offerQuote.promoRejected ? <Text style={styles.hint}>Promo {promoCode} can&apos;t be used on this order.</Text> : null}
            <ServerTotals
              quote={offerQuote}
              rowStyle={styles.summaryRow}
              labelStyle={styles.summaryLabel}
              valueStyle={styles.summaryValue}
              discountColor={colors.success}
            />
            <View style={styles.summaryRow}>
              <Text style={styles.summaryLabelBold}>Total</Text>
              <Text style={styles.summaryValueBold}>GHS {offerQuote.total.toFixed(2)}</Text>
            </View>
          </>
        ) : (
          <>
            {deliveryFee > 0 && (
              <View style={styles.summaryRow}>
                <Text style={styles.summaryLabel}>Delivery fee</Text>
                <Text style={styles.summaryValue}>GHS {deliveryFee.toFixed(2)}</Text>
              </View>
            )}
            {tipAmount > 0 && (
              <View style={styles.summaryRow}>
                <Text style={styles.summaryLabel}>Tip</Text>
                <Text style={styles.summaryValue}>GHS {tipAmount.toFixed(2)}</Text>
              </View>
            )}
            <View style={styles.summaryRow}>
              <Text style={styles.summaryLabelBold}>{chargesNote ? 'Subtotal' : 'Total'}</Text>
              <Text style={styles.summaryValueBold}>GHS {(total() + deliveryFee + tipAmount).toFixed(2)}</Text>
            </View>
            {chargesNote ? <Text style={styles.hint}>{chargesNote}</Text> : null}
          </>
        )}
      </View>

      {!isOnline && (
        <View style={styles.offlineWarning}>
          <Text style={styles.offlineText}>You're offline. Order will be queued and submitted when reconnected.</Text>
        </View>
      )}

      <TouchableOpacity style={styles.placeBtn} onPress={handlePlaceOrder} disabled={loading}>
        {loading ? <ActivityIndicator color={colors.white} /> : <Text style={styles.placeBtnText}>Place Order</Text>}
      </TouchableOpacity>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.canvas },
  section: {
    backgroundColor: colors.white,
    margin: 12,
    borderRadius: 12,
    padding: 16,
    shadowColor: colors.black,
    shadowOpacity: 0.04,
    shadowRadius: 3,
    elevation: 1,
  },
  sectionTitle: { fontSize: 15, fontWeight: '700', color: colors.ink, marginBottom: 12 },
  typeOption: {
    borderWidth: 1,
    borderColor: colors.line,
    borderRadius: 8,
    padding: 12,
    marginBottom: 8,
  },
  typeOptionActive: { borderColor: colors.brand, backgroundColor: colors.brandSubtle },
  typeText: { fontSize: 14, color: colors.inkSoft },
  typeTextActive: { color: colors.brandPressed, fontWeight: '600' },
  hint: { fontSize: 12, color: colors.inkMuted, marginTop: 4 },
  input: {
    borderWidth: 1,
    borderColor: colors.lineStrong,
    borderRadius: 8,
    padding: 12,
    fontSize: 15,
  },
  summaryRow: { flexDirection: 'row', justifyContent: 'space-between', marginBottom: 6 },
  summaryLabel: { fontSize: 14, color: colors.inkMuted },
  summaryValue: { fontSize: 14, color: colors.inkSoft },
  summaryLabelBold: { fontSize: 16, fontWeight: '700', color: colors.ink },
  summaryValueBold: { fontSize: 18, fontWeight: '800', color: colors.brand },
  offlineWarning: { margin: 12, backgroundColor: colors.warningSubtle, borderRadius: 8, padding: 12 },
  offlineText: { color: colors.warningOn, fontSize: 13 },
  placeBtn: {
    margin: 12,
    backgroundColor: colors.brand,
    borderRadius: 12,
    padding: 18,
    alignItems: 'center',
  },
  placeBtnText: { color: colors.white, fontSize: 17, fontWeight: '700' },
  addressList: { gap: 8, marginBottom: 12 },
  addressCard: { borderWidth: 1, borderColor: colors.line, borderRadius: 10, padding: 12 },
  addressCardActive: { borderColor: colors.brand, backgroundColor: colors.brandSubtle },
  addressLabel: { fontSize: 13, fontWeight: '700', color: colors.ink, marginBottom: 4 },
  addressLabelActive: { color: colors.brandPressed },
  addressText: { fontSize: 13, color: colors.inkSoft },
  addressTextActive: { color: colors.brandPressed },
  loyaltyHint: { fontSize: 13, color: colors.brandPressed, marginBottom: 8, fontWeight: '600', flex: 1 },
  tipRow: { flexDirection: 'row', flexWrap: 'wrap', gap: 8, marginBottom: 8 },
  tipChip: { borderWidth: 1, borderColor: colors.line, borderRadius: 8, paddingVertical: 12, paddingHorizontal: 14, minHeight: 44 },
  loyaltyRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 8 },
});

function formatAddress(address: CustomerAddress): string {
  return [address.addressLine, address.city, address.landmark].filter(Boolean).join(', ');
}

function splitAddress(address: string): [string, string | undefined] {
  const parts = address.split(',').map((part) => part.trim()).filter(Boolean);
  const [addressLine = address.trim(), city] = parts;
  return [addressLine, city];
}
