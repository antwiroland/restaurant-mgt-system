import { useEffect, useState } from 'react';
import { View, Text, StyleSheet, FlatList, TouchableOpacity, Alert, ActivityIndicator } from 'react-native';
import { useLocalSearchParams, router } from 'expo-router';
import {
  cancelGroupSession,
  fetchGroupSession,
  finalizeGroupSession,
  leaveGroupSession,
  removeGroupItem,
  updateGroupItem,
} from '../../src/api/group';
import { useCartStore } from '../../src/store/cart';
import { useAuthStore } from '../../src/store/auth';
import { fallbackPollMs, useRealtimeTopics } from '../../src/realtime/realtime';
import type { GroupSessionDetail, GroupSessionItem } from '../../src/types/api';
import { colors } from '../../src/theme';

function errorMessage(e: any, fallback: string): string {
  return e?.response?.data?.message ?? fallback;
}

export default function GroupSessionScreen() {
  const { code } = useLocalSearchParams<{ code: string }>();
  const [session, setSession] = useState<GroupSessionDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [finalizing, setFinalizing] = useState(false);
  const [busy, setBusy] = useState(false);
  const { tableId } = useCartStore();
  const userId = useAuthStore((state) => state.userId);

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

  const isHost = !!session && !!userId && session.hostUserId === userId;
  const isOpen = session?.status === 'OPEN';
  const isMember = !!session?.participants.some((participant) => participant.userId === userId);

  async function mutate(action: () => Promise<GroupSessionDetail | void>, fallback: string) {
    setBusy(true);
    try {
      const next = await action();
      if (next) setSession(next);
    } catch (e: any) {
      Alert.alert('Group order', errorMessage(e, fallback));
    } finally {
      setBusy(false);
    }
  }

  function changeQuantity(item: GroupSessionItem, quantity: number) {
    if (quantity < 1) {
      removeItem(item);
      return;
    }
    void mutate(() => updateGroupItem(code, item.id, quantity, item.notes), 'Could not change the item');
  }

  function removeItem(item: GroupSessionItem) {
    void mutate(() => removeGroupItem(code, item.id), 'Could not remove the item');
  }

  function handleLeave() {
    Alert.alert('Leave group', 'Leave this group? Your items will be removed from the cart.', [
      { text: 'Stay', style: 'cancel' },
      {
        text: 'Leave',
        style: 'destructive',
        onPress: () =>
          void mutate(async () => {
            await leaveGroupSession(code);
            router.back();
          }, 'Could not leave the group'),
      },
    ]);
  }

  function handleCancel() {
    Alert.alert('Cancel group order', 'Cancel this group order for everyone? Nothing will be ordered.', [
      { text: 'Keep it', style: 'cancel' },
      {
        text: 'Cancel order',
        style: 'destructive',
        onPress: () => void mutate(() => cancelGroupSession(code), 'Could not cancel the group'),
      },
    ]);
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
            Alert.alert('Error', errorMessage(e, 'Could not finalize'));
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
        <Text style={styles.error}>Session not found, or you have not joined it</Text>
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <Text style={styles.code}>Group Code: {session.sessionCode}</Text>
        <Text style={[styles.status, session.status === 'CANCELLED' && styles.statusCancelled]}>{session.status}</Text>
      </View>

      <FlatList
        data={session.participants}
        keyExtractor={(participant) => participant.participantId}
        renderItem={({ item: participant }) => {
          const mine = participant.userId === userId;
          return (
            <View style={styles.participantCard}>
              <Text style={styles.participantName}>
                {participant.displayName}
                {participant.userId === session.hostUserId ? ' (host)' : ''}
                {mine ? ' · you' : ''}
              </Text>
              <Text style={styles.participantTotal}>GHS {participant.subtotal}</Text>
              {participant.items.map((item) => (
                <View key={item.id} style={styles.itemRow}>
                  <View style={styles.itemText}>
                    <Text style={styles.participantItem}>
                      {item.menuItemName} x{item.quantity} · GHS {item.unitPrice}
                    </Text>
                    {item.modifiers.length > 0 ? (
                      <Text style={styles.itemDetail}>
                        {item.modifiers.map((m) => `${m.groupName}: ${m.optionName}`).join(', ')}
                      </Text>
                    ) : null}
                    {item.notes ? <Text style={styles.itemDetail}>Note: {item.notes}</Text> : null}
                  </View>
                  {isOpen && (mine || isHost) ? (
                    <View style={styles.itemActions}>
                      {mine ? (
                        <>
                          <TouchableOpacity
                            style={styles.smallBtn}
                            disabled={busy}
                            onPress={() => changeQuantity(item, item.quantity - 1)}
                            accessibilityLabel={`One less ${item.menuItemName}`}
                          >
                            <Text style={styles.smallBtnText}>−</Text>
                          </TouchableOpacity>
                          <TouchableOpacity
                            style={styles.smallBtn}
                            disabled={busy}
                            onPress={() => changeQuantity(item, item.quantity + 1)}
                            accessibilityLabel={`One more ${item.menuItemName}`}
                          >
                            <Text style={styles.smallBtnText}>+</Text>
                          </TouchableOpacity>
                        </>
                      ) : null}
                      <TouchableOpacity
                        style={styles.smallBtn}
                        disabled={busy}
                        onPress={() => removeItem(item)}
                        accessibilityLabel={`Remove ${item.menuItemName}`}
                      >
                        <Text style={styles.removeText}>Remove</Text>
                      </TouchableOpacity>
                    </View>
                  ) : null}
                </View>
              ))}
            </View>
          );
        }}
        contentContainerStyle={{ padding: 12 }}
        ListFooterComponent={
          <View style={styles.totalCard}>
            <Text style={styles.totalLabel}>Group Total</Text>
            <Text style={styles.totalValue}>GHS {session.groupTotal}</Text>
          </View>
        }
      />

      {session.status === 'CANCELLED' ? (
        <Text style={styles.placedText}>This group order was cancelled. Nothing was ordered.</Text>
      ) : null}

      {session.orderId ? (
        <View style={styles.placedCard}>
          <Text style={styles.placedText}>
            The group order has been placed. Open it to track it and, if the bill is split, pay your share.
          </Text>
          <TouchableOpacity
            style={styles.finalizeBtn}
            onPress={() => router.push(`/order/${session.orderId}`)}
            accessibilityRole="button"
          >
            <Text style={styles.finalizeBtnText}>View order</Text>
          </TouchableOpacity>
        </View>
      ) : null}

      {isOpen ? (
        <View style={styles.footerActions}>
          {isHost ? (
            <TouchableOpacity style={styles.secondaryBtn} onPress={handleCancel} disabled={busy}>
              <Text style={styles.secondaryBtnText}>Cancel group</Text>
            </TouchableOpacity>
          ) : isMember ? (
            <TouchableOpacity style={styles.secondaryBtn} onPress={handleLeave} disabled={busy}>
              <Text style={styles.secondaryBtnText}>Leave group</Text>
            </TouchableOpacity>
          ) : null}
          {isHost ? (
            <TouchableOpacity style={[styles.finalizeBtn, styles.flexBtn]} onPress={handleFinalize} disabled={finalizing || busy}>
              {finalizing ? <ActivityIndicator color={colors.white} /> : <Text style={styles.finalizeBtnText}>Place Order</Text>}
            </TouchableOpacity>
          ) : null}
        </View>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.canvas },
  placedCard: { paddingHorizontal: 12, paddingTop: 8 },
  placedText: { fontSize: 13, color: colors.inkSoft, marginHorizontal: 12 },
  center: { flex: 1, justifyContent: 'center', alignItems: 'center', padding: 24 },
  error: { fontSize: 16, color: colors.inkSoft, textAlign: 'center' },
  header: { backgroundColor: colors.white, padding: 16, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  code: { fontSize: 18, fontWeight: '700', color: colors.ink },
  status: { fontSize: 13, color: colors.brand, fontWeight: '600' },
  statusCancelled: { color: colors.inkSoft },
  participantCard: {
    backgroundColor: colors.white, borderRadius: 12, padding: 14, marginBottom: 10,
    shadowColor: colors.black, shadowOpacity: 0.04, shadowRadius: 3, elevation: 1,
  },
  participantName: { fontSize: 15, fontWeight: '700', color: colors.ink, marginBottom: 4 },
  participantTotal: { fontSize: 14, fontWeight: '600', color: colors.brand, marginBottom: 8 },
  itemRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: 6, gap: 8 },
  itemText: { flex: 1 },
  participantItem: { fontSize: 13, color: colors.inkMuted },
  itemDetail: { fontSize: 12, color: colors.inkSoft, marginTop: 1 },
  itemActions: { flexDirection: 'row', alignItems: 'center', gap: 4 },
  smallBtn: {
    minWidth: 36, minHeight: 36, paddingHorizontal: 8, borderRadius: 8,
    borderWidth: 1, borderColor: colors.brandSubtle, alignItems: 'center', justifyContent: 'center',
  },
  smallBtnText: { fontSize: 16, fontWeight: '700', color: colors.ink },
  removeText: { fontSize: 12, fontWeight: '600', color: colors.brandPressed },
  totalCard: {
    backgroundColor: colors.brandSubtle, borderRadius: 12, padding: 16, margin: 12,
    flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center',
  },
  totalLabel: { fontSize: 16, fontWeight: '700', color: colors.brandPressed },
  totalValue: { fontSize: 22, fontWeight: '800', color: colors.brandPressed },
  footerActions: { flexDirection: 'row', alignItems: 'center', paddingHorizontal: 4 },
  flexBtn: { flex: 1 },
  secondaryBtn: {
    margin: 12, borderRadius: 12, padding: 18, alignItems: 'center',
    borderWidth: 1, borderColor: colors.brand, backgroundColor: colors.white,
  },
  secondaryBtnText: { color: colors.brand, fontSize: 15, fontWeight: '700' },
  finalizeBtn: {
    margin: 12, backgroundColor: colors.brand, borderRadius: 12,
    padding: 18, alignItems: 'center',
  },
  finalizeBtnText: { color: colors.white, fontSize: 17, fontWeight: '700' },
});
