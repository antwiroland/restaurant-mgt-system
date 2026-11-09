import { StyleSheet, View } from 'react-native';
import MapView, { Marker, type Region } from 'react-native-maps';
import { colors } from '../theme';

export type MapPoint = { latitude: number; longitude: number; title: string; color?: string };

function regionFor(points: MapPoint[]): Region {
  const lats = points.map((p) => p.latitude);
  const lngs = points.map((p) => p.longitude);
  const minLat = Math.min(...lats);
  const maxLat = Math.max(...lats);
  const minLng = Math.min(...lngs);
  const maxLng = Math.max(...lngs);
  return {
    latitude: (minLat + maxLat) / 2,
    longitude: (minLng + maxLng) / 2,
    latitudeDelta: Math.max(0.01, (maxLat - minLat) * 1.6),
    longitudeDelta: Math.max(0.01, (maxLng - minLng) * 1.6),
  };
}

/**
 * In-app map showing the rider and the restaurant. Renders nothing until at least one
 * real coordinate exists (no placeholder locations).
 */
export function DeliveryMap({ points, height = 220 }: { points: MapPoint[]; height?: number }) {
  const valid = points.filter((p) => Number.isFinite(p.latitude) && Number.isFinite(p.longitude));
  if (valid.length === 0) return null;
  return (
    <View style={[styles.wrap, { height }]}>
      <MapView style={StyleSheet.absoluteFill} region={regionFor(valid)} toolbarEnabled={false}>
        {valid.map((point) => (
          <Marker
            key={`${point.title}-${point.latitude}-${point.longitude}`}
            coordinate={{ latitude: point.latitude, longitude: point.longitude }}
            title={point.title}
            pinColor={point.color ?? colors.brand}
          />
        ))}
      </MapView>
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: { borderRadius: 14, overflow: 'hidden', borderWidth: 1, borderColor: colors.line, backgroundColor: colors.sunken },
});
