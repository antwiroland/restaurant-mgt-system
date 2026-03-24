import { useEffect, useState } from 'react';
import {
  View,
  Text,
  FlatList,
  TouchableOpacity,
  TextInput,
  StyleSheet,
  ActivityIndicator,
  Image,
} from 'react-native';
import { router } from 'expo-router';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { fetchCategories } from '../../src/api/menu';
import { useCartStore } from '../../src/store/cart';
import { useOfflineStore } from '../../src/store/offline';
import { OfflineBanner } from '../../src/components/OfflineBanner';
import type { MenuCategory, MenuItem } from '../../src/types/api';

const MENU_CACHE_KEY = 'menu_cache';
const MENU_CACHE_TS_KEY = 'menu_cache_ts';
type CategoryTab = { id: string | null; name: string };

export default function MenuScreen() {
  const [categories, setCategories] = useState<MenuCategory[]>([]);
  const [selectedCat, setSelectedCat] = useState<string | null>(null);
  const [search, setSearch] = useState('');
  const [loading, setLoading] = useState(true);
  const [stale, setStale] = useState(false);
  const { addItem, itemCount } = useCartStore();
  const { isOnline } = useOfflineStore();

  useEffect(() => {
    loadMenu();
  }, [isOnline]);

  async function loadMenu() {
    setLoading(true);

    if (isOnline) {
      try {
        const cats = await fetchCategories();
        setCategories(cats);
        setStale(false);
        await AsyncStorage.setItem(MENU_CACHE_KEY, JSON.stringify(cats));
        await AsyncStorage.setItem(MENU_CACHE_TS_KEY, Date.now().toString());
      } catch {
        await loadFromCache();
      }
    } else {
      await loadFromCache();
    }

    setLoading(false);
  }

  async function loadFromCache() {
    const raw = await AsyncStorage.getItem(MENU_CACHE_KEY);
    if (raw) {
      setCategories(JSON.parse(raw));
      setStale(true);
    }
  }

  const items: MenuItem[] = categories
    .filter((category) => !selectedCat || category.id === selectedCat)
    .flatMap((category) => category.items)
    .filter((item) => !search || item.name.toLowerCase().includes(search.toLowerCase()))
    .filter((item) => item.available);
  const categoryTabs: CategoryTab[] = [{ id: null, name: 'All' }, ...categories.map((category) => ({ id: category.id, name: category.name }))];

  return (
    <View style={styles.container}>
      <OfflineBanner />
      {stale && (
        <View style={styles.staleBanner}>
          <Text style={styles.staleText}>Menu shown from last update (offline)</Text>
        </View>
      )}

      <TextInput
        style={styles.search}
        placeholder="Search menu..."
        value={search}
        onChangeText={setSearch}
      />

      <FlatList
        horizontal
        data={categoryTabs}
        keyExtractor={(category) => category.id ?? 'all'}
        renderItem={({ item: category }) => (
          <TouchableOpacity
            style={[styles.catChip, selectedCat === category.id && styles.catChipActive]}
            onPress={() => setSelectedCat(category.id)}
          >
            <Text style={[styles.catText, selectedCat === category.id && styles.catTextActive]}>
              {category.name}
            </Text>
          </TouchableOpacity>
        )}
        style={styles.cats}
        showsHorizontalScrollIndicator={false}
      />

      {loading ? (
        <ActivityIndicator size="large" color="#2563EB" style={{ flex: 1 }} />
      ) : (
        <FlatList
          data={items}
          keyExtractor={(item) => item.id}
          numColumns={2}
          renderItem={({ item }) => (
            <TouchableOpacity style={styles.card} onPress={() => addItem(item)}>
              {item.imageUrl ? (
                <Image source={{ uri: item.imageUrl }} style={styles.cardImage} />
              ) : (
                <View style={[styles.cardImage, styles.cardImagePlaceholder]}>
                  <Text style={styles.cardImageText}>Plate</Text>
                </View>
              )}
              <View style={styles.cardBody}>
                <Text style={styles.cardName} numberOfLines={2}>{item.name}</Text>
                <Text style={styles.cardPrice}>GHS {item.price}</Text>
                <TouchableOpacity style={styles.addBtn} onPress={() => addItem(item)}>
                  <Text style={styles.addBtnText}>Add</Text>
                </TouchableOpacity>
              </View>
            </TouchableOpacity>
          )}
          contentContainerStyle={{ padding: 8 }}
        />
      )}

      {itemCount() > 0 && (
        <TouchableOpacity style={styles.cartFab} onPress={() => router.push('/cart')}>
          <Text style={styles.cartFabText}>View Cart ({itemCount()})</Text>
        </TouchableOpacity>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#F9FAFB' },
  staleBanner: { backgroundColor: '#FEF3C7', padding: 6, alignItems: 'center' },
  staleText: { color: '#92400E', fontSize: 12 },
  search: {
    margin: 12,
    padding: 12,
    borderRadius: 8,
    backgroundColor: '#fff',
    borderWidth: 1,
    borderColor: '#E5E7EB',
  },
  cats: { paddingHorizontal: 12, marginBottom: 8, maxHeight: 40 },
  catChip: {
    paddingHorizontal: 14,
    paddingVertical: 6,
    marginRight: 8,
    borderRadius: 20,
    backgroundColor: '#E5E7EB',
  },
  catChipActive: { backgroundColor: '#2563EB' },
  catText: { color: '#374151', fontSize: 13 },
  catTextActive: { color: '#fff' },
  card: {
    flex: 1,
    margin: 6,
    backgroundColor: '#fff',
    borderRadius: 12,
    overflow: 'hidden',
    shadowColor: '#000',
    shadowOpacity: 0.06,
    shadowRadius: 4,
    elevation: 2,
  },
  cardImage: { width: '100%', height: 110 },
  cardImagePlaceholder: { backgroundColor: '#F3F4F6', alignItems: 'center', justifyContent: 'center' },
  cardImageText: { fontSize: 18, color: '#6B7280', fontWeight: '600' },
  cardBody: { padding: 10 },
  cardName: { fontSize: 13, fontWeight: '600', color: '#111827', marginBottom: 4 },
  cardPrice: { fontSize: 14, color: '#2563EB', fontWeight: '700', marginBottom: 8 },
  addBtn: {
    backgroundColor: '#2563EB',
    borderRadius: 6,
    padding: 6,
    alignItems: 'center',
  },
  addBtnText: { color: '#fff', fontSize: 12, fontWeight: '600' },
  cartFab: {
    position: 'absolute',
    bottom: 20,
    left: 24,
    right: 24,
    backgroundColor: '#2563EB',
    borderRadius: 12,
    padding: 16,
    alignItems: 'center',
    shadowColor: '#2563EB',
    shadowOpacity: 0.4,
    shadowRadius: 8,
    elevation: 6,
  },
  cartFabText: { color: '#fff', fontSize: 16, fontWeight: '700' },
});
