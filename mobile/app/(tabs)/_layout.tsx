import { Tabs } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';
import { useCartStore } from '../../src/store/cart';
import { View, Text, StyleSheet } from 'react-native';
import { colors } from '../../src/theme';

function CartBadge() {
  const count = useCartStore((s) => s.itemCount());
  if (count === 0) return null;
  return (
    <View style={badge.container}>
      <Text style={badge.text}>{count > 9 ? '9+' : count}</Text>
    </View>
  );
}

const badge = StyleSheet.create({
  container: {
    position: 'absolute', top: -4, right: -8,
    backgroundColor: colors.brand, borderRadius: 10, borderWidth: 2, borderColor: colors.surface,
    width: 18, height: 18, justifyContent: 'center', alignItems: 'center',
  },
  text: { color: colors.white, fontSize: 10, fontWeight: '700' },
});

export default function TabLayout() {
  return (
    <Tabs
      screenOptions={{
        headerShown: true,
        headerStyle: { backgroundColor: colors.canvas },
        headerShadowVisible: false,
        headerTitleStyle: { fontWeight: '800', color: colors.ink, fontSize: 18 },
        tabBarActiveTintColor: colors.brand,
        tabBarInactiveTintColor: colors.inkMuted,
        tabBarStyle: { backgroundColor: colors.surface, borderTopColor: colors.line, height: 64, paddingTop: 6, paddingBottom: 10 },
        tabBarLabelStyle: { fontSize: 11, fontWeight: '700' },
        sceneStyle: { backgroundColor: colors.canvas },
      }}
    >
      <Tabs.Screen
        name="menu"
        options={{
          title: 'Menu',
          headerShown: false,
          tabBarIcon: ({ color, size }) => <Ionicons name="restaurant-outline" size={size} color={color} />,
        }}
      />
      <Tabs.Screen
        name="orders"
        options={{
          title: 'Orders',
          tabBarIcon: ({ color, size }) => <Ionicons name="list-outline" size={size} color={color} />,
        }}
      />
      <Tabs.Screen
        name="profile"
        options={{
          title: 'Profile',
          tabBarIcon: ({ color, size }) => <Ionicons name="person-outline" size={size} color={color} />,
        }}
      />
    </Tabs>
  );
}
