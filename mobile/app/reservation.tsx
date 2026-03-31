import { useEffect, useState } from 'react';
import {
  View, Text, FlatList, TouchableOpacity, StyleSheet,
  TextInput, Modal, Alert, ActivityIndicator, ScrollView,
} from 'react-native';
import { createReservation as apiCreate, fetchMyReservations, cancelReservation as apiCancel } from '../src/api/reservation';
import { useAuthStore } from '../src/store/auth';
import type { Reservation } from '../src/types/api';

export default function ReservationScreen() {
  const { isAuthenticated } = useAuthStore();
  const [reservations, setReservations] = useState<Reservation[]>([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState({
    tableId: '', customerName: '', customerPhone: '',
    partySize: '2', reservedAt: '', durationMins: '90', notes: '',
  });
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (isAuthenticated) {
      load();
    } else {
      setLoading(false);
    }
  }, [isAuthenticated]);

  async function load() {
    setLoading(true);
    try {
      const data = await fetchMyReservations();
      setReservations(data);
    } catch {}
    setLoading(false);
  }

  async function handleCreate() {
    if (!form.tableId || !form.customerName || !form.reservedAt) {
      Alert.alert('Error', 'Fill in all required fields');
      return;
    }
    setSubmitting(true);
    try {
      await apiCreate({
        tableId: form.tableId,
        customerName: form.customerName,
        customerPhone: form.customerPhone,
        partySize: parseInt(form.partySize, 10),
        reservedAt: form.reservedAt,
        durationMins: parseInt(form.durationMins, 10),
        notes: form.notes,
      });
      setShowForm(false);
      void load();
      Alert.alert('Confirmed', 'Your reservation has been made!');
    } catch (e: any) {
      Alert.alert('Error', e?.response?.data?.message ?? 'Could not make reservation');
    } finally {
      setSubmitting(false);
    }
  }

  async function handleCancel(id: string) {
    Alert.alert('Cancel', 'Cancel this reservation?', [
      { text: 'No', style: 'cancel' },
      {
        text: 'Yes', style: 'destructive',
        onPress: async () => {
          try {
            await apiCancel(id);
            void load();
          } catch (e: any) {
            Alert.alert('Error', e?.response?.data?.message ?? 'Cannot cancel');
          }
        },
      },
    ]);
  }

  return (
    <View style={styles.container}>
      <TouchableOpacity style={styles.addBtn} onPress={() => setShowForm(true)}>
        <Text style={styles.addBtnText}>+ New Reservation</Text>
      </TouchableOpacity>

      {loading ? (
        <ActivityIndicator size="large" color="#2563EB" style={{ flex: 1 }} />
      ) : reservations.length === 0 ? (
        <View style={styles.center}>
          <Text style={styles.empty}>No reservations</Text>
        </View>
      ) : (
        <FlatList
          data={reservations}
          keyExtractor={(reservation) => reservation.id}
          renderItem={({ item: reservation }) => (
            <View style={styles.card}>
              <Text style={styles.cardTitle}>
                Table {reservation.tableNumber} | {new Date(reservation.reservedAt).toLocaleString()}
              </Text>
              <Text style={styles.cardMeta}>
                Party of {reservation.partySize} | {reservation.durationMins} mins | {reservation.status}
              </Text>
              {reservation.notes ? <Text style={styles.cardNotes}>{reservation.notes}</Text> : null}
              {reservation.status !== 'CANCELLED' ? (
                <TouchableOpacity onPress={() => void handleCancel(reservation.id)}>
                  <Text style={styles.cancelLink}>Cancel reservation</Text>
                </TouchableOpacity>
              ) : null}
            </View>
          )}
          contentContainerStyle={{ padding: 12 }}
        />
      )}

      <Modal visible={showForm} animationType="slide" presentationStyle="pageSheet">
        <ScrollView style={styles.modal}>
          <Text style={styles.modalTitle}>New Reservation</Text>
          {[
            { label: 'Table ID *', key: 'tableId', placeholder: 'uuid' },
            { label: 'Your Name *', key: 'customerName', placeholder: 'Kofi Mensah' },
            { label: 'Your Phone', key: 'customerPhone', placeholder: '+233...' },
            { label: 'Party Size', key: 'partySize', placeholder: '2' },
            { label: 'Date & Time * (ISO)', key: 'reservedAt', placeholder: '2026-03-25T19:00:00Z' },
            { label: 'Duration (mins)', key: 'durationMins', placeholder: '90' },
            { label: 'Notes', key: 'notes', placeholder: 'Anniversary dinner' },
          ].map(({ label, key, placeholder }) => (
            <View key={key} style={styles.field}>
              <Text style={styles.fieldLabel}>{label}</Text>
              <TextInput
                style={styles.fieldInput}
                placeholder={placeholder}
                value={(form as Record<string, string>)[key]}
                onChangeText={(value) => setForm((current) => ({ ...current, [key]: value }))}
              />
            </View>
          ))}

          <TouchableOpacity style={styles.submitBtn} onPress={handleCreate} disabled={submitting}>
            {submitting ? <ActivityIndicator color="#fff" /> : <Text style={styles.submitBtnText}>Reserve</Text>}
          </TouchableOpacity>

          <TouchableOpacity style={styles.closeBtn} onPress={() => setShowForm(false)}>
            <Text style={styles.closeBtnText}>Cancel</Text>
          </TouchableOpacity>
        </ScrollView>
      </Modal>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#F9FAFB' },
  addBtn: { margin: 12, backgroundColor: '#2563EB', borderRadius: 10, padding: 14, alignItems: 'center' },
  addBtnText: { color: '#fff', fontWeight: '700', fontSize: 15 },
  center: { flex: 1, justifyContent: 'center', alignItems: 'center' },
  empty: { color: '#6B7280', fontSize: 16 },
  card: {
    backgroundColor: '#fff', borderRadius: 12, padding: 14, marginBottom: 10,
    shadowColor: '#000', shadowOpacity: 0.05, shadowRadius: 3, elevation: 1,
  },
  cardTitle: { fontSize: 15, fontWeight: '700', color: '#111827', marginBottom: 4 },
  cardMeta: { fontSize: 13, color: '#6B7280', marginBottom: 4 },
  cardNotes: { fontSize: 13, color: '#374151', marginBottom: 4, fontStyle: 'italic' },
  cancelLink: { color: '#EF4444', fontSize: 13, marginTop: 4 },
  modal: { flex: 1, padding: 20, backgroundColor: '#fff' },
  modalTitle: { fontSize: 22, fontWeight: '700', color: '#111827', marginBottom: 20, marginTop: 12 },
  field: { marginBottom: 16 },
  fieldLabel: { fontSize: 13, fontWeight: '600', color: '#374151', marginBottom: 6 },
  fieldInput: {
    borderWidth: 1, borderColor: '#D1D5DB', borderRadius: 8,
    padding: 12, fontSize: 15,
  },
  submitBtn: { backgroundColor: '#2563EB', borderRadius: 10, padding: 16, alignItems: 'center', marginBottom: 12 },
  submitBtnText: { color: '#fff', fontWeight: '700', fontSize: 16 },
  closeBtn: { borderWidth: 1, borderColor: '#D1D5DB', borderRadius: 10, padding: 14, alignItems: 'center' },
  closeBtnText: { color: '#374151', fontWeight: '600', fontSize: 15 },
});
