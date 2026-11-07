import { View, Text, StyleSheet } from 'react-native';
import { billRows, type BillProps } from '../features/billing/billing';

export function BillLines(props: BillProps) {
  return (
    <View accessibilityLabel="Bill breakdown">
      {billRows(props).map((row) => (
        <View key={row.label} style={styles.row}>
          <Text style={[styles.label, row.muted && styles.muted]}>{row.label}</Text>
          <Text style={[styles.value, row.muted && styles.muted]}>{row.value}</Text>
        </View>
      ))}
      <View style={styles.totalLine}>
        <Text style={styles.totalLabel}>Total</Text>
        <Text style={styles.totalValue}>GHS {(Number.parseFloat(props.total) || 0).toFixed(2)}</Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', justifyContent: 'space-between', marginBottom: 4 },
  label: { fontSize: 13, color: '#4B5563' },
  value: { fontSize: 13, color: '#111827' },
  muted: { color: '#9CA3AF' },
  totalLine: { flexDirection: 'row', justifyContent: 'space-between', borderTopWidth: 1, borderTopColor: '#E5E7EB', paddingTop: 8, marginTop: 4 },
  totalLabel: { fontSize: 15, fontWeight: '700', color: '#111827' },
  totalValue: { fontSize: 16, fontWeight: '800', color: '#2563EB' },
});
