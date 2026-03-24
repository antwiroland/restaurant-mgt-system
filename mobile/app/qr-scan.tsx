import { useState } from 'react';
import { View, Text, StyleSheet, Alert, TouchableOpacity } from 'react-native';
import { CameraView, useCameraPermissions } from 'expo-camera';
import { router } from 'expo-router';
import { scanQrToken } from '../src/api/tables';
import { useCartStore } from '../src/store/cart';
import { useOfflineStore } from '../src/store/offline';

export default function QrScanScreen() {
  const [permission, requestPermission] = useCameraPermissions();
  const [scanned, setScanned] = useState(false);
  const { setTable } = useCartStore();
  const { isOnline } = useOfflineStore();

  if (!permission) return <View />;

  if (!permission.granted) {
    return (
      <View style={styles.center}>
        <Text style={styles.text}>Camera permission required</Text>
        <TouchableOpacity style={styles.btn} onPress={requestPermission}>
          <Text style={styles.btnText}>Grant Permission</Text>
        </TouchableOpacity>
      </View>
    );
  }

  if (!isOnline) {
    return (
      <View style={styles.center}>
        <Text style={styles.text}>QR scan requires internet connection</Text>
        <TouchableOpacity style={styles.btn} onPress={() => router.back()}>
          <Text style={styles.btnText}>Go Back</Text>
        </TouchableOpacity>
      </View>
    );
  }

  async function handleBarCodeScanned({ data }: { data: string }) {
    if (scanned) return;
    setScanned(true);
    try {
      const result = await scanQrToken(data);
      setTable(result.tableId, result.tableNumber);
      Alert.alert(
        'Table linked',
        `Table ${result.tableNumber} linked to your cart.`,
        [{ text: 'Start Ordering', onPress: () => router.replace('/(tabs)/menu') }]
      );
    } catch {
      Alert.alert('Invalid QR', 'This QR code is not valid.', [
        { text: 'Try again', onPress: () => setScanned(false) },
        { text: 'Cancel', onPress: () => router.back() },
      ]);
    }
  }

  return (
    <View style={styles.container}>
      <CameraView
        style={StyleSheet.absoluteFillObject}
        onBarcodeScanned={scanned ? undefined : handleBarCodeScanned}
        barcodeScannerSettings={{ barcodeTypes: ['qr'] }}
      />
      <View style={styles.overlay}>
        <View style={styles.finder} />
        <Text style={styles.hint}>Point camera at table QR code</Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  center: { flex: 1, justifyContent: 'center', alignItems: 'center', padding: 24 },
  text: { fontSize: 16, color: '#374151', marginBottom: 16, textAlign: 'center' },
  btn: { backgroundColor: '#2563EB', borderRadius: 8, paddingHorizontal: 24, paddingVertical: 12 },
  btnText: { color: '#fff', fontWeight: '600' },
  overlay: { ...StyleSheet.absoluteFillObject, justifyContent: 'center', alignItems: 'center' },
  finder: {
    width: 240, height: 240, borderRadius: 12,
    borderWidth: 2, borderColor: '#fff',
  },
  hint: { color: '#fff', marginTop: 16, fontSize: 14, fontWeight: '600' },
});
