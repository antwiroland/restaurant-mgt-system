import { useEffect, useMemo, useState } from 'react';
import {
  View,
  Text,
  FlatList,
  TouchableOpacity,
  TextInput,
  StyleSheet,
  ActivityIndicator,
  Image,
  Modal,
  ScrollView,
} from 'react-native';
import { router } from 'expo-router';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { fetchCategories, fetchMenuItemModifiers } from '../../src/api/menu';
import { useCartStore } from '../../src/store/cart';
import { useOfflineStore } from '../../src/store/offline';
import { OfflineBanner } from '../../src/components/OfflineBanner';
import type { MenuCategory, MenuItem, MenuModifierGroup } from '../../src/types/api';

const MENU_CACHE_KEY = 'menu_cache';
const MENU_CACHE_TS_KEY = 'menu_cache_ts';
type CategoryTab = { id: string | null; name: string };

function toNumber(value: string): number {
  const parsed = Number.parseFloat(value);
  return Number.isFinite(parsed) ? parsed : 0;
}

type ModifierSelection = Record<string, string[]>;

function initialSelection(groups: MenuModifierGroup[]): ModifierSelection {
  const selection: ModifierSelection = {};
  for (const group of groups) {
    if (group.required && group.options.length > 0) {
      if (group.selectionType === 'SINGLE') {
        selection[group.id] = [group.options[0].id];
      } else {
        const min = Math.max(group.minSelect ?? 1, 1);
        selection[group.id] = group.options.slice(0, min).map((option) => option.id);
      }
    } else {
      selection[group.id] = [];
    }
  }
  return selection;
}

export default function MenuScreen() {
  const [categories, setCategories] = useState<MenuCategory[]>([]);
  const [selectedCat, setSelectedCat] = useState<string | null>(null);
  const [search, setSearch] = useState('');
  const [loading, setLoading] = useState(true);
  const [stale, setStale] = useState(false);
  const [modifierItem, setModifierItem] = useState<MenuItem | null>(null);
  const [modifierGroupsByItemId, setModifierGroupsByItemId] = useState<Record<string, MenuModifierGroup[]>>({});
  const [selection, setSelection] = useState<ModifierSelection>({});
  const [modalError, setModalError] = useState('');
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

  async function beginAdd(item: MenuItem) {
    setModalError('');

    if (!isOnline && !modifierGroupsByItemId[item.id]) {
      addItem(item);
      return;
    }

    try {
      let groups = modifierGroupsByItemId[item.id];
      if (!groups) {
        groups = await fetchMenuItemModifiers(item.id);
        setModifierGroupsByItemId((current) => ({ ...current, [item.id]: groups ?? [] }));
      }

      if (!groups || groups.length === 0) {
        addItem(item);
        return;
      }

      setSelection(initialSelection(groups));
      setModifierItem(item);
    } catch {
      addItem(item);
    }
  }

  function toggleMulti(group: MenuModifierGroup, optionId: string) {
    const existing = selection[group.id] ?? [];
    const has = existing.includes(optionId);
    const next = has ? existing.filter((id) => id !== optionId) : [...existing, optionId];

    if (!has && group.maxSelect != null && next.length > group.maxSelect) {
      setModalError(`You can choose up to ${group.maxSelect} options for ${group.name}.`);
      return;
    }

    setModalError('');
    setSelection((current) => ({ ...current, [group.id]: next }));
  }

  function confirmModifiers() {
    if (!modifierItem) return;
    const groups = modifierGroupsByItemId[modifierItem.id] ?? [];

    const optionIds: string[] = [];
    const summary: string[] = [];
    let delta = 0;

    for (const group of groups) {
      const selected = selection[group.id] ?? [];
      const min = group.minSelect ?? (group.required ? 1 : 0);
      const max = group.maxSelect;

      if (group.required && selected.length === 0) {
        setModalError(`Please choose ${group.name}.`);
        return;
      }
      if (selected.length < min) {
        setModalError(`Choose at least ${min} option(s) for ${group.name}.`);
        return;
      }
      if (max != null && selected.length > max) {
        setModalError(`Choose at most ${max} option(s) for ${group.name}.`);
        return;
      }

      for (const selectedId of selected) {
        const option = group.options.find((entry) => entry.id === selectedId);
        if (!option) continue;
        optionIds.push(option.id);
        summary.push(`${group.name}: ${option.name}`);
        delta += toNumber(option.priceDelta);
      }
    }

    addItem(modifierItem, {
      modifierOptionIds: optionIds,
      modifierSummary: summary,
      unitPrice: (toNumber(modifierItem.price) + delta).toFixed(2),
    });

    setModifierItem(null);
    setSelection({});
    setModalError('');
  }

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
            <TouchableOpacity style={styles.card} onPress={() => void beginAdd(item)}>
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
                <TouchableOpacity style={styles.addBtn} onPress={() => void beginAdd(item)}>
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

      <Modal visible={!!modifierItem} transparent animationType="slide" onRequestClose={() => setModifierItem(null)}>
        <View style={styles.modalBackdrop}>
          <View style={styles.modalCard}>
            <Text style={styles.modalTitle}>{modifierItem?.name}</Text>
            <ScrollView style={{ maxHeight: 360 }}>
              {(modifierItem ? modifierGroupsByItemId[modifierItem.id] : [])?.map((group) => (
                <View key={group.id} style={styles.groupBlock}>
                  <Text style={styles.groupTitle}>{group.name}</Text>
                  <Text style={styles.groupHint}>
                    {group.selectionType === 'SINGLE' ? 'Pick one' : `Pick ${group.minSelect ?? 0}-${group.maxSelect ?? group.options.length}`}
                  </Text>
                  {group.selectionType === 'SINGLE' ? (
                    <View style={styles.optionRowWrap}>
                      {group.options.map((option) => {
                        const selected = (selection[group.id] ?? []).includes(option.id);
                        return (
                          <TouchableOpacity
                            key={option.id}
                            style={[styles.optionChip, selected && styles.optionChipSelected]}
                            onPress={() => setSelection((current) => ({ ...current, [group.id]: [option.id] }))}
                          >
                            <Text style={[styles.optionText, selected && styles.optionTextSelected]}>
                              {option.name} ({toNumber(option.priceDelta) >= 0 ? '+' : ''}GHS {option.priceDelta})
                            </Text>
                          </TouchableOpacity>
                        );
                      })}
                    </View>
                  ) : (
                    <View style={styles.optionRowWrap}>
                      {group.options.map((option) => {
                        const selected = (selection[group.id] ?? []).includes(option.id);
                        return (
                          <TouchableOpacity
                            key={option.id}
                            style={[styles.optionChip, selected && styles.optionChipSelected]}
                            onPress={() => toggleMulti(group, option.id)}
                          >
                            <Text style={[styles.optionText, selected && styles.optionTextSelected]}>
                              {option.name} ({toNumber(option.priceDelta) >= 0 ? '+' : ''}GHS {option.priceDelta})
                            </Text>
                          </TouchableOpacity>
                        );
                      })}
                    </View>
                  )}
                </View>
              ))}
            </ScrollView>
            {modalError ? <Text style={styles.modalError}>{modalError}</Text> : null}
            <View style={styles.modalButtons}>
              <TouchableOpacity style={[styles.modalBtn, styles.modalBtnGhost]} onPress={() => setModifierItem(null)}>
                <Text style={styles.modalBtnGhostText}>Cancel</Text>
              </TouchableOpacity>
              <TouchableOpacity style={[styles.modalBtn, styles.modalBtnPrimary]} onPress={confirmModifiers}>
                <Text style={styles.modalBtnPrimaryText}>Add to Cart</Text>
              </TouchableOpacity>
            </View>
          </View>
        </View>
      </Modal>
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
  modalBackdrop: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.4)',
    justifyContent: 'center',
    padding: 16,
  },
  modalCard: {
    backgroundColor: '#fff',
    borderRadius: 14,
    padding: 16,
  },
  modalTitle: { fontSize: 17, fontWeight: '700', color: '#111827', marginBottom: 12 },
  groupBlock: { marginBottom: 10 },
  groupTitle: { fontSize: 14, fontWeight: '700', color: '#1F2937' },
  groupHint: { fontSize: 12, color: '#6B7280', marginBottom: 6 },
  optionRowWrap: { flexDirection: 'row', flexWrap: 'wrap', gap: 6 },
  optionChip: {
    borderWidth: 1,
    borderColor: '#D1D5DB',
    borderRadius: 18,
    paddingHorizontal: 10,
    paddingVertical: 6,
  },
  optionChipSelected: { borderColor: '#2563EB', backgroundColor: '#EFF6FF' },
  optionText: { fontSize: 12, color: '#374151' },
  optionTextSelected: { color: '#1D4ED8', fontWeight: '600' },
  modalError: { color: '#B91C1C', fontSize: 12, marginTop: 8 },
  modalButtons: { flexDirection: 'row', justifyContent: 'flex-end', gap: 8, marginTop: 12 },
  modalBtn: { borderRadius: 8, paddingHorizontal: 12, paddingVertical: 8 },
  modalBtnGhost: { backgroundColor: '#E5E7EB' },
  modalBtnPrimary: { backgroundColor: '#2563EB' },
  modalBtnGhostText: { color: '#374151', fontWeight: '600' },
  modalBtnPrimaryText: { color: '#fff', fontWeight: '700' },
});
