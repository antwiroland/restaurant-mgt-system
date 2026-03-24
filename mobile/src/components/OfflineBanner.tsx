import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { useOfflineStore } from '../store/offline';

export function OfflineBanner() {
  const { isOnline, pendingCount } = useOfflineStore();
  if (isOnline) return null;
  const count = pendingCount();
  return (
    <View style={styles.banner}>
      <Text style={styles.text}>
        {count > 0
          ? `You're offline. ${count} order(s) queued.`
          : "You're offline. Menu shown from last update."}
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  banner: {
    backgroundColor: '#F59E0B',
    padding: 8,
    alignItems: 'center',
  },
  text: {
    color: '#1F2937',
    fontSize: 13,
    fontWeight: '600',
  },
});
