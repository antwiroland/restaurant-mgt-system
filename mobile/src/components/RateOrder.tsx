import { useEffect, useState } from 'react';
import { View, Text, TextInput, TouchableOpacity, StyleSheet, ActivityIndicator } from 'react-native';
import { fetchOrderFeedback, submitFeedback } from '../api/billing';
import type { Feedback, OrderType } from '../types/api';

type Props = { orderId: string; orderType: OrderType };

function Stars({ label, value, onChange }: { label: string; value: number; onChange: (v: number) => void }) {
  return (
    <View style={styles.starRow} accessibilityRole="adjustable" accessibilityLabel={`${label}: ${value || 'not rated'} out of 5`}>
      <Text style={styles.starLabel}>{label}</Text>
      <View style={styles.stars}>
        {[1, 2, 3, 4, 5].map((star) => (
          <TouchableOpacity
            key={star}
            onPress={() => onChange(star === value ? 0 : star)}
            style={styles.starButton}
            accessibilityRole="button"
            accessibilityLabel={`${label} ${star} star${star === 1 ? '' : 's'}`}
            accessibilityState={{ selected: star <= value }}
          >
            <Text style={[styles.star, star <= value && styles.starOn]}>{star <= value ? '★' : '☆'}</Text>
          </TouchableOpacity>
        ))}
      </View>
    </View>
  );
}

/** Lets a signed-in customer rate a completed or delivered order once, and shows the restaurant's reply. */
export function RateOrder({ orderId, orderType }: Props) {
  const [existing, setExisting] = useState<Feedback | null | undefined>(undefined);
  const [rating, setRating] = useState(0);
  const [food, setFood] = useState(0);
  const [service, setService] = useState(0);
  const [delivery, setDelivery] = useState(0);
  const [comment, setComment] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    fetchOrderFeedback(orderId).then(setExisting);
  }, [orderId]);

  async function submit() {
    if (rating === 0) {
      setError('Choose an overall rating first.');
      return;
    }
    setBusy(true);
    setError('');
    try {
      const saved = await submitFeedback(orderId, {
        rating,
        foodRating: food || undefined,
        serviceRating: orderType === 'DELIVERY' ? undefined : service || undefined,
        deliveryRating: orderType === 'DELIVERY' ? delivery || undefined : undefined,
        comment: comment.trim() || undefined,
      });
      setExisting(saved);
    } catch (e: any) {
      setError(e?.response?.data?.message ?? 'Could not send your rating. Please try again.');
    } finally {
      setBusy(false);
    }
  }

  if (existing === undefined) return null;

  if (existing) {
    return (
      <View style={styles.section}>
        <Text style={styles.title}>Your rating</Text>
        <Text style={[styles.star, styles.starOn]} accessibilityLabel={`${existing.rating} out of 5`}>
          {'★'.repeat(existing.rating)}{'☆'.repeat(5 - existing.rating)}
        </Text>
        {existing.comment ? <Text style={styles.comment}>“{existing.comment}”</Text> : null}
        {existing.response ? (
          <View style={styles.reply}>
            <Text style={styles.replyLabel}>Reply from the restaurant</Text>
            <Text style={styles.comment}>{existing.response}</Text>
          </View>
        ) : (
          <Text style={styles.hint}>Thanks for your feedback!</Text>
        )}
      </View>
    );
  }

  return (
    <View style={styles.section}>
      <Text style={styles.title}>How was your order?</Text>
      <Stars label="Overall" value={rating} onChange={setRating} />
      <Stars label="Food" value={food} onChange={setFood} />
      {orderType === 'DELIVERY' ? (
        <Stars label="Delivery" value={delivery} onChange={setDelivery} />
      ) : (
        <Stars label="Service" value={service} onChange={setService} />
      )}
      <TextInput
        style={styles.input}
        placeholder="Tell us more (optional)"
        value={comment}
        onChangeText={setComment}
        multiline
        maxLength={2000}
        accessibilityLabel="Comment"
      />
      {error ? <Text style={styles.error} accessibilityLiveRegion="polite">{error}</Text> : null}
      <TouchableOpacity style={styles.submit} onPress={submit} disabled={busy} accessibilityRole="button">
        {busy ? <ActivityIndicator color="#fff" /> : <Text style={styles.submitText}>Send rating</Text>}
      </TouchableOpacity>
    </View>
  );
}

const styles = StyleSheet.create({
  section: { backgroundColor: '#fff', margin: 12, borderRadius: 12, padding: 16 },
  title: { fontSize: 15, fontWeight: '700', color: '#111827', marginBottom: 12 },
  starRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: 4 },
  starLabel: { fontSize: 14, color: '#374151' },
  stars: { flexDirection: 'row' },
  starButton: { minWidth: 44, minHeight: 44, alignItems: 'center', justifyContent: 'center' },
  star: { fontSize: 24, color: '#D1D5DB' },
  starOn: { color: '#F59E0B' },
  input: { borderWidth: 1, borderColor: '#D1D5DB', borderRadius: 8, padding: 12, fontSize: 14, minHeight: 72, marginTop: 8 },
  error: { color: '#B91C1C', marginTop: 8, fontSize: 13 },
  submit: { marginTop: 12, backgroundColor: '#2563EB', borderRadius: 10, padding: 14, alignItems: 'center' },
  submitText: { color: '#fff', fontWeight: '700', fontSize: 15 },
  comment: { fontSize: 14, color: '#374151', marginTop: 6 },
  hint: { fontSize: 12, color: '#6B7280', marginTop: 6 },
  reply: { marginTop: 10, backgroundColor: '#F3F4F6', borderRadius: 8, padding: 10 },
  replyLabel: { fontSize: 12, fontWeight: '700', color: '#4B5563' },
});
