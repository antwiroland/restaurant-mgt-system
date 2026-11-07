import { useEffect, useState } from 'react';
import { View, Text, StyleSheet, FlatList, TouchableOpacity, Alert, ActivityIndicator } from 'react-native';
import { useLocalSearchParams, router } from 'expo-router';
import { fetchGroupSession, finalizeGroupSession } from '../../src/api/group';
import { useCartStore } from '../../src/store/cart';
import { fallbackPollMs, useRealtimeTopics } from '../../src/realtime/realtime';
import type { GroupSessionDetail } from '../../src/types/api';
import { colors } from '../../src/theme';

export default function GroupSessionScreen() {
  const { code } = useLocalSearchParams<{ code: string }>();
  const [session, setSession] = useState<GroupSessionDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [finalizing, setFinalizing] = useState(false);
  const { tableId } = useCartStore();

  const live = useRealtimeTopics(code ? [`/topic/group.${code}`] : [], () => void load());

  useEffect(() => {
    void load();
    const interval = setInterval(() => {
      void load();
    }, fallbackPollMs(live, 5000));
    return () => clearInterval(interval);
  }, [code, live]);

  async function load() {
    try {
      const nextSession = await fetchGroupSession(code);
      setSession(nextSession);
    } catch {}
    setLoading(false);
  }

  async function handleFinalize() {
    if (!session) return;
    Alert.alert('Finalize Order', 'Place the combined group order?', [
      { text: 'Cancel', style: 'cancel' },
      {
        text: 'Place Order',
        onPress: async () => {
          setFinalizing(true);
          try {
            const order = await finalizeGroupSession(code, {
              type: tableId ? 'DINE_IN' : 'PICKUP',
              tableId: tableId ?? undefined,
            });
            router.replace(`/order/${order.id}`);
          } catch (e: any) {
            Alert.alert('Error', e?.response?.data?.message ?? 'Could not finalize');
          } finally {
            setFinalizing(false);
          }
        },
      },
    ]);
  }

  if (loading) {
    return <ActivityIndicator size="large" color={colors.brand} style={{ flex: 1, marginTop: 40 }} />;
  }

  if (!session) {
    return (
      <View style={styles.center}>
        <Text style={styles.error}>Session not found</Text>
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <Text style={styles.code}>Group Code: {session.sessionCode}</Text>
        <Text style={styles.status}>{session.status}</Text>
      </View>

      <FlatList
        data={session.participants}
        keyExtractor={(participant) => participant.participantId}
        renderItem={({ item: participant }) => (
          <View style={styles.participantCard}>
            <Text style={styles.participantName}>{participant.displayName}</Text>
            <Text style={styles.participantTotal}>GHS {participant.subtotal}</Text>
            {participant.items.map((item) => (
              <Text key={item.id} style={styles.participantItem}>
                - {item.menuItemName} x{item.quantity}
              </Text>
            ))}
          </View>
        )}
        contentContainerStyle={{ padding: 12 }}
        ListFooterComponent={
          <View style={styles.totalCard}>
            <Text style={styles.totalLabel}>Group Total</Text>
            <Text style={styles.totalValue}>GHS {session.groupTotal}</Text>
          </View>
        }
      />

      {session.status === 'OPEN' ? (
        <TouchableOpacity style={styles.finalizeBtn} onPress={handleFinalize} disabled={finalizing}>
          {finalizing ? <ActivityIndicator color={colors.white} /> : <Text style={styles.finalizeBtnText}>Finalize & Place Order</Text>}
        </TouchableOpacity>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.canvas },
  center: { flex: 1, justifyContent: 'center', alignItems: 'center' },
  error: { fontSize: 16, color: colors.inkSoft },
  header: { backgroundColor: colors.white, padding: 16, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  code: { fontSize: 18, fontWeight: '700', color: colors.ink },
  status: { fontSize: 13, color: colors.brand, fontWeight: '600' },
  participantCard: {
    backgroundColor: colors.white, borderRadius: 12, padding: 14, marginBottom: 10,
    shadowColor: colors.black, shadowOpacity: 0.04, shadowRadius: 3, elevation: 1,
  },
  participantName: { fontSize: 15, fontWeight: '700', color: colors.ink, marginBottom: 4 },
  participantTotal: { fontSize: 14, fontWeight: '600', color: colors.brand, marginBottom: 8 },
  participantItem: { fontSize: 13, color: colors.inkMuted, marginBottom: 2 },
  totalCard: {
    backgroundColor: colors.brandSubtle, borderRadius: 12, padding: 16, margin: 12,
    flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center',
  },
  totalLabel: { fontSize: 16, fontWeight: '700', color: colors.brandPressed },
  totalValue: { fontSize: 22, fontWeight: '800', color: colors.brandPressed },
  finalizeBtn: {
    margin: 12, backgroundColor: colors.brand, borderRadius: 12,
    padding: 18, alignItems: 'center',
  },
  finalizeBtnText: { color: colors.white, fontSize: 17, fontWeight: '700' },
});
