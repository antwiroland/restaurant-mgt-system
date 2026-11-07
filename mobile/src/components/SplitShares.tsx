import { useCallback, useEffect, useState } from 'react';
import { View, Text, TextInput, TouchableOpacity, StyleSheet, ActivityIndicator } from 'react-native';
import { fetchSplits, splitByItems } from '../api/billing';
import { initiatePayment } from '../api/payment';
import { canSplitByItems, myPendingShare } from '../features/billing/billing';
import type { Order, PaymentSplit } from '../types/api';

type Props = {
  order: Order;
  userId: string | null;
  /** Called after a share payment starts, with its payment ID, so the screen can track it. */
  onPaymentStarted: (paymentId: string) => void;
};

/**
 * Group orders: each participant pays only for their own items. Shows every share, who has paid, and lets
 * this customer pay their own share by MoMo.
 */
export function SplitShares({ order, userId, onPaymentStarted }: Props) {
  const [splits, setSplits] = useState<PaymentSplit[]>([]);
  const [phone, setPhone] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');

  const load = useCallback(() => {
    fetchSplits(order.id)
      .then((next) => setSplits(next.filter((split) => split.status !== 'CANCELLED')))
      .catch(() => setSplits([]));
  }, [order.id]);

  useEffect(() => {
    load();
  }, [load, order.status]);

  const isGroup = order.items.some((item) => !!item.participantId);
  if (!isGroup) return null;

  const mine = myPendingShare(splits, userId);
  const paidCount = splits.filter((split) => split.status === 'PAID').length;

  async function split() {
    setBusy(true);
    setError('');
    try {
      const next = await splitByItems(order.id);
      setSplits(next);
      setMessage('Bill split. Everyone can now pay their own share.');
    } catch (e: any) {
      setError(e?.response?.data?.message ?? 'Could not split the bill.');
    } finally {
      setBusy(false);
    }
  }

  async function payMine() {
    if (!mine) return;
    if (!phone.trim()) {
      setError('Enter your MoMo number.');
      return;
    }
    setBusy(true);
    setError('');
    try {
      const payment = await initiatePayment(order.id, 'MOBILE_MONEY', phone.trim(), mine.id);
      setMessage(payment.message || 'Approve the MoMo prompt on your phone.');
      onPaymentStarted(payment.paymentId);
      load();
    } catch (e: any) {
      setError(e?.response?.data?.message ?? 'Could not start the payment.');
    } finally {
      setBusy(false);
    }
  }

  return (
    <View style={styles.section}>
      <Text style={styles.title}>Split bill</Text>
      {splits.length === 0 ? (
        <>
          <Text style={styles.hint}>
            {order.customerUserId === userId
              ? 'Pay separately: everyone pays for their own items, plus a fair part of fees and taxes.'
              : 'The person who placed the order can split the bill so everyone pays for their own items.'}
          </Text>
          {canSplitByItems(order, splits) && !!userId && order.customerUserId === userId ? (
            <TouchableOpacity style={styles.secondary} onPress={split} disabled={busy} accessibilityRole="button">
              <Text style={styles.secondaryText}>Split by what each person ordered</Text>
            </TouchableOpacity>
          ) : null}
        </>
      ) : (
        <>
          {splits.map((share) => (
            <View key={share.id} style={styles.row}>
              <Text style={styles.name}>
                {share.displayName}
                {share.userId && share.userId === userId ? ' (you)' : ''}
              </Text>
              <Text style={styles.amount}>GHS {Number(share.amount).toFixed(2)}</Text>
              <Text style={[styles.badge, share.status === 'PAID' ? styles.paid : styles.pending]}>
                {share.status === 'PAID' ? 'Paid' : 'Pending'}
              </Text>
            </View>
          ))}
          <Text style={styles.hint}>{paidCount} of {splits.length} paid</Text>
          {mine ? (
            <View style={{ marginTop: 10 }}>
              <TextInput
                style={styles.input}
                placeholder="MoMo number for your share"
                value={phone}
                onChangeText={setPhone}
                keyboardType="phone-pad"
                accessibilityLabel="MoMo number for your share"
              />
              <TouchableOpacity style={styles.primary} onPress={payMine} disabled={busy} accessibilityRole="button">
                {busy ? <ActivityIndicator color="#fff" /> : <Text style={styles.primaryText}>Pay my share · GHS {Number(mine.amount).toFixed(2)}</Text>}
              </TouchableOpacity>
            </View>
          ) : null}
        </>
      )}
      {error ? <Text style={styles.error} accessibilityLiveRegion="polite">{error}</Text> : null}
      {message ? <Text style={styles.message} accessibilityLiveRegion="polite">{message}</Text> : null}
    </View>
  );
}

const styles = StyleSheet.create({
  section: { backgroundColor: '#fff', margin: 12, borderRadius: 12, padding: 16 },
  title: { fontSize: 15, fontWeight: '700', color: '#111827', marginBottom: 8 },
  hint: { fontSize: 12, color: '#6B7280', marginTop: 4 },
  row: { flexDirection: 'row', alignItems: 'center', gap: 8, paddingVertical: 6, borderBottomWidth: 1, borderBottomColor: '#F3F4F6' },
  name: { flex: 1, fontSize: 14, color: '#111827' },
  amount: { fontSize: 14, fontWeight: '600', color: '#111827' },
  badge: { fontSize: 11, fontWeight: '700', paddingHorizontal: 8, paddingVertical: 3, borderRadius: 10, overflow: 'hidden' },
  paid: { backgroundColor: '#D1FAE5', color: '#065F46' },
  pending: { backgroundColor: '#FEF3C7', color: '#92400E' },
  input: { borderWidth: 1, borderColor: '#D1D5DB', borderRadius: 8, padding: 12, fontSize: 15 },
  primary: { marginTop: 8, backgroundColor: '#2563EB', borderRadius: 10, padding: 14, alignItems: 'center', minHeight: 44 },
  primaryText: { color: '#fff', fontWeight: '700', fontSize: 15 },
  secondary: { marginTop: 10, backgroundColor: '#EFF6FF', borderRadius: 10, padding: 14, alignItems: 'center', minHeight: 44 },
  secondaryText: { color: '#1D4ED8', fontWeight: '700' },
  error: { color: '#B91C1C', marginTop: 8, fontSize: 13 },
  message: { color: '#065F46', marginTop: 8, fontSize: 13 },
});
