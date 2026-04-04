import { useEffect, useState } from 'react';
import { View, Text, TouchableOpacity, StyleSheet, Alert, ScrollView, TextInput } from 'react-native';
import { router } from 'expo-router';
import { useAuthStore } from '../../src/store/auth';
import { logoutCustomer } from '../../src/api/auth';
import { fetchLoyaltyBalance, fetchLoyaltyTransactions } from '../../src/api/loyalty';
import { fetchCurrentUser, updateCurrentUser } from '../../src/api/user';
import type { CurrentUser, LoyaltyBalance, LoyaltyTransaction } from '../../src/types/api';

export default function ProfileScreen() {
  const { isAuthenticated, name, phone, email, clear, setUser } = useAuthStore();
  const [balance, setBalance] = useState<LoyaltyBalance | null>(null);
  const [transactions, setTransactions] = useState<LoyaltyTransaction[]>([]);
  const [profile, setProfile] = useState<CurrentUser | null>(null);
  const [editName, setEditName] = useState(name ?? '');
  const [editPhone, setEditPhone] = useState(phone ?? '');
  const [editEmail, setEditEmail] = useState(email ?? '');

  useEffect(() => {
    if (isAuthenticated) {
      fetchCurrentUser().then((user) => {
        setProfile(user);
        setEditName(user.name);
        setEditPhone(user.phone);
        setEditEmail(user.email ?? '');
      }).catch(() => {});
      fetchLoyaltyBalance().then(setBalance).catch(() => {});
      fetchLoyaltyTransactions().then(setTransactions).catch(() => {});
    }
  }, [isAuthenticated]);

  async function handleSaveProfile() {
    try {
      const updated = await updateCurrentUser({
        name: editName.trim(),
        phone: editPhone.trim(),
        email: editEmail.trim() || undefined,
      });
      setProfile(updated);
      setUser(updated.id, updated.name, updated.role, { phone: updated.phone, email: updated.email ?? null });
      Alert.alert('Saved', 'Profile updated.');
    } catch (e: any) {
      Alert.alert('Update failed', e?.response?.data?.message ?? 'Could not update your profile.');
    }
  }

  async function handleLogout() {
    Alert.alert('Logout', 'Are you sure?', [
      { text: 'Cancel', style: 'cancel' },
      {
        text: 'Logout',
        style: 'destructive',
        onPress: async () => {
          await logoutCustomer();
          clear();
          router.replace('/(auth)/login');
        },
      },
    ]);
  }

  if (!isAuthenticated) {
    return (
      <View style={styles.center}>
        <Text style={styles.title}>Not signed in</Text>
        <TouchableOpacity style={styles.btn} onPress={() => router.push('/(auth)/login')}>
          <Text style={styles.btnText}>Sign In</Text>
        </TouchableOpacity>
      </View>
    );
  }

  return (
    <ScrollView style={styles.container}>
      <View style={styles.header}>
        <View style={styles.avatar}>
          <Text style={styles.avatarText}>{profile?.name?.[0]?.toUpperCase() ?? name?.[0]?.toUpperCase() ?? 'U'}</Text>
        </View>
        <Text style={styles.name}>{profile?.name ?? name}</Text>
      </View>

      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Profile</Text>
        <TextInput style={styles.input} placeholder="Full name" value={editName} onChangeText={setEditName} />
        <TextInput style={styles.input} placeholder="Phone" value={editPhone} onChangeText={setEditPhone} keyboardType="phone-pad" />
        <TextInput style={styles.input} placeholder="Email" value={editEmail} onChangeText={setEditEmail} autoCapitalize="none" />
        <TouchableOpacity style={styles.primaryBtn} onPress={handleSaveProfile}>
          <Text style={styles.primaryBtnText}>Save Profile</Text>
        </TouchableOpacity>
      </View>

      {balance && (
        <View style={styles.loyaltyCard}>
          <Text style={styles.loyaltyTitle}>Loyalty Points</Text>
          <Text style={styles.loyaltyPoints}>{balance.points} pts</Text>
        </View>
      )}

      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Quick Actions</Text>
        <TouchableOpacity style={styles.actionRow} onPress={() => router.push('/reservation')}>
          <Text style={styles.actionText}>My Reservations</Text>
        </TouchableOpacity>
        <TouchableOpacity style={styles.actionRow} onPress={() => router.push('/qr-scan')}>
          <Text style={styles.actionText}>Scan Table QR</Text>
        </TouchableOpacity>
      </View>

      {transactions.length > 0 && (
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Recent Points Activity</Text>
          {transactions.slice(0, 5).map((t) => (
            <View key={t.id} style={styles.txRow}>
              <Text style={styles.txType}>{t.type === 'EARN' ? '+' : '-'}{t.points} pts</Text>
              <Text style={styles.txDate}>{new Date(t.createdAt).toLocaleDateString()}</Text>
            </View>
          ))}
        </View>
      )}

      <TouchableOpacity style={styles.logoutBtn} onPress={handleLogout}>
        <Text style={styles.logoutText}>Sign Out</Text>
      </TouchableOpacity>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#F9FAFB' },
  center: { flex: 1, justifyContent: 'center', alignItems: 'center', padding: 24 },
  header: { alignItems: 'center', padding: 24, backgroundColor: '#fff', marginBottom: 12 },
  avatar: {
    width: 72, height: 72, borderRadius: 36, backgroundColor: '#2563EB',
    justifyContent: 'center', alignItems: 'center', marginBottom: 12,
  },
  avatarText: { color: '#fff', fontSize: 28, fontWeight: '700' },
  name: { fontSize: 20, fontWeight: '700', color: '#111827' },
  loyaltyCard: {
    margin: 12, padding: 16, backgroundColor: '#EFF6FF',
    borderRadius: 12, alignItems: 'center',
  },
  loyaltyTitle: { fontSize: 14, color: '#1D4ED8', marginBottom: 4 },
  loyaltyPoints: { fontSize: 36, fontWeight: '800', color: '#1D4ED8' },
  section: { backgroundColor: '#fff', margin: 12, borderRadius: 12, padding: 16 },
  sectionTitle: { fontSize: 14, fontWeight: '700', color: '#374151', marginBottom: 12 },
  input: { borderWidth: 1, borderColor: '#D1D5DB', borderRadius: 8, padding: 12, marginBottom: 10, fontSize: 15 },
  primaryBtn: { backgroundColor: '#2563EB', borderRadius: 10, padding: 14, alignItems: 'center' },
  primaryBtnText: { color: '#fff', fontWeight: '700', fontSize: 15 },
  actionRow: { paddingVertical: 12, borderBottomWidth: 1, borderBottomColor: '#F3F4F6' },
  actionText: { fontSize: 15, color: '#111827' },
  txRow: { flexDirection: 'row', justifyContent: 'space-between', paddingVertical: 8 },
  txType: { fontSize: 14, fontWeight: '600', color: '#111827' },
  txDate: { fontSize: 12, color: '#9CA3AF' },
  title: { fontSize: 20, fontWeight: '700', color: '#374151', marginBottom: 16 },
  btn: { backgroundColor: '#2563EB', borderRadius: 8, paddingHorizontal: 24, paddingVertical: 12 },
  btnText: { color: '#fff', fontWeight: '600' },
  logoutBtn: { margin: 12, padding: 16, backgroundColor: '#FEE2E2', borderRadius: 12, alignItems: 'center' },
  logoutText: { color: '#EF4444', fontWeight: '700', fontSize: 16 },
});
