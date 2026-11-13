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
import { fetchPublicOffers, offersFor, type PublicOffer } from '../../src/api/offers';
import { useCartStore } from '../../src/store/cart';
import { useOfflineStore } from '../../src/store/offline';
import { OfflineBanner } from '../../src/components/OfflineBanner';
import type { MenuCategory, MenuItem, MenuModifierGroup } from '../../src/types/api';
import { brand, colors, money, radius, shadow } from '../../src/theme';
import { fetchFavourites, setFavourite } from '../../src/api/favourites';
import { t } from '../../src/i18n';
import { useAuthStore } from '../../src/store/auth';
import { Ionicons } from '@expo/vector-icons';

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
  const canFavourite = useAuthStore((st) => st.isAuthenticated && st.role === 'CUSTOMER');
  const [favouriteIds, setFavouriteIds] = useState<Set<string>>(new Set());
  const [offers, setOffers] = useState<PublicOffer[]>([]);

  useEffect(() => {
    if (!isOnline) return;
    fetchPublicOffers().then(setOffers).catch(() => setOffers([]));
  }, [isOnline]);

  useEffect(() => {
    if (!canFavourite) return;
    fetchFavourites().then((items) => setFavouriteIds(new Set(items.map((i) => i.id)))).catch(() => {});
  }, [canFavourite]);

  async function toggleFavourite(item: MenuItem) {
    const next = !favouriteIds.has(item.id);
    setFavouriteIds((current) => {
      const copy = new Set(current);
      if (next) copy.add(item.id); else copy.delete(item.id);
      return copy;
    });
    try {
      await setFavourite(item.id, next);
    } catch {
      setFavouriteIds((current) => {
        const copy = new Set(current);
        if (next) copy.delete(item.id); else copy.add(item.id);
        return copy;
      });
    }
  }

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

  const hour = new Date().getHours();
  const greeting = hour < 12 ? 'Good morning' : hour < 18 ? 'Good afternoon' : 'Good evening';
  const count = itemCount();
  const modalGroups = modifierItem ? modifierGroupsByItemId[modifierItem.id] ?? [] : [];

  return (
    <View style={styles.container}>
      <OfflineBanner />

      <FlatList
        data={loading ? [] : items}
        keyExtractor={(item) => item.id}
        contentContainerStyle={{ paddingBottom: count > 0 ? 120 : 32 }}
        ListHeaderComponent={
          <View>
            {/* Hero header */}
            <View style={styles.hero}>
              <Text style={styles.heroEyebrow}>{greeting}</Text>
              <Text style={styles.heroTitle}>What would you like from {brand.name}?</Text>
              <TextInput
                style={styles.search}
                placeholder="Search dishes"
                placeholderTextColor={colors.inkMuted}
                value={search}
                onChangeText={setSearch}
                accessibilityLabel="Search dishes"
              />
            </View>

            {/* Quick actions */}
            <View style={styles.quickRow}>
              <TouchableOpacity style={styles.quickCard} onPress={() => router.push('/qr-scan')} accessibilityRole="button">
                <Text style={styles.quickTitle}>At the restaurant?</Text>
                <Text style={styles.quickText}>Scan your table code</Text>
              </TouchableOpacity>
              <TouchableOpacity style={[styles.quickCard, styles.quickCardAlt]} onPress={() => router.push('/reservation')} accessibilityRole="button">
                <Text style={[styles.quickTitle, { color: colors.espressoInk }]}>Book a table</Text>
                <Text style={[styles.quickText, { color: colors.espressoSoft }]}>Reserve for later</Text>
              </TouchableOpacity>
            </View>

            {stale && (
              <View style={styles.staleBanner}>
                <Text style={styles.staleText}>{"Showing the menu from your last visit — you're offline."}</Text>
              </View>
            )}

            {offers.length > 0 ? (
              <View style={styles.dealsBox} accessibilityLabel="Deals">
                <Text style={styles.dealsTitle}>Deals</Text>
                {[...offers].sort((a, b) => Number(b.runningNow) - Number(a.runningNow)).slice(0, 4).map((offer) => (
                  <View key={offer.id} style={styles.dealRow}>
                    <Ionicons name="pricetag" size={14} color={colors.brand} style={{ marginTop: 2 }} />
                    <View style={{ flex: 1 }}>
                      <Text style={styles.dealSummary}>{offer.summary}</Text>
                      <Text style={styles.dealWhen}>
                        {offer.runningNow ? 'On now' : offer.schedule ? `Runs ${offer.schedule}` : 'Coming soon'}
                        {offer.runningNow && offer.schedule ? ` · ${offer.schedule}` : ''}
                        {offer.branchName ? ` · ${offer.branchName} only` : ''}
                      </Text>
                    </View>
                  </View>
                ))}
              </View>
            ) : null}

            {/* Categories */}
            <FlatList
              horizontal
              data={categoryTabs}
              keyExtractor={(category) => category.id ?? 'all'}
              renderItem={({ item: category }) => {
                const active = selectedCat === category.id;
                return (
                  <TouchableOpacity
                    style={[styles.catChip, active && styles.catChipActive]}
                    onPress={() => setSelectedCat(category.id)}
                    accessibilityRole="button"
                    accessibilityState={{ selected: active }}
                  >
                    <Text style={[styles.catText, active && styles.catTextActive]}>{category.name}</Text>
                  </TouchableOpacity>
                );
              }}
              contentContainerStyle={styles.cats}
              showsHorizontalScrollIndicator={false}
            />

            <Text style={styles.sectionTitle}>
              {selectedCat ? categoryTabs.find((c) => c.id === selectedCat)?.name : 'On the menu today'}
            </Text>

            {loading ? (
              <View style={{ paddingHorizontal: 16, gap: 12 }}>
                {[0, 1, 2, 3].map((key) => (
                  <View key={key} style={styles.skeleton} />
                ))}
                <ActivityIndicator color={colors.brand} />
              </View>
            ) : null}

            {!loading && items.length === 0 ? (
              <View style={styles.empty}>
                <Text style={styles.emptyTitle}>{search ? 'No dishes match' : 'Nothing here yet'}</Text>
                <Text style={styles.emptyText}>{search ? 'Try a different word.' : 'Dishes will appear when the kitchen publishes them.'}</Text>
              </View>
            ) : null}
          </View>
        }
        renderItem={({ item }) => (
          <TouchableOpacity style={styles.row} onPress={() => void beginAdd(item)} activeOpacity={0.85} accessibilityLabel={`${item.name}, ${money(item.price)}`}>
            {item.imageUrl ? (
              <Image source={{ uri: item.imageUrl }} style={styles.rowImage} />
            ) : (
              <View style={[styles.rowImage, styles.rowImagePlaceholder]}>
                <Text style={styles.rowImageLetter}>{item.name.charAt(0).toUpperCase()}</Text>
              </View>
            )}
            <View style={styles.rowBody}>
              <Text style={styles.rowName} numberOfLines={2}>{item.name}</Text>
              {item.description ? <Text style={styles.rowDesc} numberOfLines={2}>{item.description}</Text> : null}
              {offersFor(item.id, offers)[0] ? (
                <View style={[styles.offerPill, !offersFor(item.id, offers)[0].runningNow && styles.offerPillLater]}>
                  <Ionicons name="pricetag" size={11} color={offersFor(item.id, offers)[0].runningNow ? colors.white : colors.brand} />
                  <Text
                    style={[styles.offerPillText, !offersFor(item.id, offers)[0].runningNow && styles.offerPillTextLater]}
                    numberOfLines={1}
                  >
                    {offersFor(item.id, offers)[0].summary}
                  </Text>
                </View>
              ) : null}
              <View style={styles.rowFooter}>
                <Text style={styles.rowPrice}>{money(item.price)}</Text>
                {canFavourite ? (
                  <TouchableOpacity
                    onPress={() => void toggleFavourite(item)}
                    hitSlop={8}
                    accessibilityRole="button"
                    accessibilityLabel={favouriteIds.has(item.id) ? t('favourites.remove') : t('favourites.add')}
                    style={{ marginLeft: 'auto', marginRight: 12 }}
                  >
                    <Ionicons name={favouriteIds.has(item.id) ? 'heart' : 'heart-outline'} size={22} color={colors.brand} />
                  </TouchableOpacity>
                ) : null}
                <TouchableOpacity style={styles.addBtn} onPress={() => void beginAdd(item)} accessibilityLabel={`Add ${item.name}`} hitSlop={8}>
                  <Text style={styles.addBtnText}>Add</Text>
                </TouchableOpacity>
              </View>
            </View>
          </TouchableOpacity>
        )}
      />

      {count > 0 && (
        <TouchableOpacity style={styles.cartBar} onPress={() => router.push('/cart')} activeOpacity={0.9} accessibilityRole="button">
          <View style={styles.cartCount}>
            <Text style={styles.cartCountText}>{count}</Text>
          </View>
          <Text style={styles.cartBarText}>View order</Text>
          <Text style={styles.cartBarTotal}>{money(useCartStore.getState().total())}</Text>
        </TouchableOpacity>
      )}

      <Modal visible={!!modifierItem} transparent animationType="slide" onRequestClose={() => setModifierItem(null)}>
        <View style={styles.modalBackdrop}>
          <View style={styles.sheet}>
            <View style={styles.sheetHandle} />
            <Text style={styles.modalTitle}>{modifierItem?.name}</Text>
            {modifierItem ? <Text style={styles.sheetPrice}>{money(modifierItem.price)}</Text> : null}
            <ScrollView style={{ maxHeight: 420 }}>
              {modalGroups.map((group) => (
                <View key={group.id} style={styles.groupBlock}>
                  <View style={styles.groupHead}>
                    <Text style={styles.groupTitle}>{group.name}</Text>
                    <Text style={[styles.groupBadge, group.required ? styles.groupBadgeRequired : null]}>
                      {group.required ? 'Required' : 'Optional'}
                    </Text>
                  </View>
                  <Text style={styles.groupHint}>
                    {group.selectionType === 'SINGLE' ? 'Choose one' : `Choose up to ${group.maxSelect ?? group.options.length}`}
                  </Text>
                  {group.options.map((option) => {
                    const selected = (selection[group.id] ?? []).includes(option.id);
                    const delta = toNumber(option.priceDelta);
                    return (
                      <TouchableOpacity
                        key={option.id}
                        style={[styles.optionRow, selected && styles.optionRowSelected]}
                        onPress={() =>
                          group.selectionType === 'SINGLE'
                            ? setSelection((current) => ({ ...current, [group.id]: [option.id] }))
                            : toggleMulti(group, option.id)
                        }
                        accessibilityRole={group.selectionType === 'SINGLE' ? 'radio' : 'checkbox'}
                        accessibilityState={{ checked: selected }}
                      >
                        <View style={[styles.optionMark, group.selectionType === 'SINGLE' && styles.optionMarkRound, selected && styles.optionMarkOn]} />
                        <Text style={[styles.optionText, selected && styles.optionTextSelected]}>{option.name}</Text>
                        <Text style={styles.optionPrice}>{delta > 0 ? `+${money(delta)}` : ''}</Text>
                      </TouchableOpacity>
                    );
                  })}
                </View>
              ))}
            </ScrollView>
            {modalError ? <Text style={styles.modalError}>{modalError}</Text> : null}
            <View style={styles.modalButtons}>
              <TouchableOpacity style={[styles.modalBtn, styles.modalBtnGhost]} onPress={() => setModifierItem(null)}>
                <Text style={styles.modalBtnGhostText}>Cancel</Text>
              </TouchableOpacity>
              <TouchableOpacity style={[styles.modalBtn, styles.modalBtnPrimary]} onPress={confirmModifiers}>
                <Text style={styles.modalBtnPrimaryText}>Add to order</Text>
              </TouchableOpacity>
            </View>
          </View>
        </View>
      </Modal>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.canvas },
  hero: { backgroundColor: colors.espresso, paddingHorizontal: 20, paddingTop: 20, paddingBottom: 24, borderBottomLeftRadius: 24, borderBottomRightRadius: 24 },
  heroEyebrow: { color: colors.gold, fontSize: 12, fontWeight: '700', letterSpacing: 1.4, textTransform: 'uppercase' },
  heroTitle: { color: colors.espressoInk, fontSize: 26, fontWeight: '800', letterSpacing: -0.5, marginTop: 6, lineHeight: 32 },
  search: {
    marginTop: 18,
    height: 48,
    paddingHorizontal: 16,
    borderRadius: radius.md,
    backgroundColor: colors.surface,
    color: colors.ink,
    fontSize: 15,
  },
  quickRow: { flexDirection: 'row', gap: 12, paddingHorizontal: 16, marginTop: 16 },
  quickCard: { flex: 1, borderRadius: radius.lg, padding: 14, backgroundColor: colors.brandSubtle, borderWidth: 1, borderColor: colors.brandSoft, minHeight: 72, justifyContent: 'center' },
  quickCardAlt: { backgroundColor: colors.espressoRaised, borderColor: colors.espressoRaised },
  quickTitle: { fontSize: 15, fontWeight: '700', color: colors.ink },
  quickText: { fontSize: 13, color: colors.inkSoft, marginTop: 2 },
  staleBanner: { marginHorizontal: 16, marginTop: 12, backgroundColor: colors.warningSubtle, padding: 10, borderRadius: radius.md },
  staleText: { color: colors.warningOn, fontSize: 13 },
  cats: { paddingHorizontal: 16, paddingTop: 18, gap: 8 },
  catChip: {
    height: 40,
    paddingHorizontal: 16,
    borderRadius: radius.pill,
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.line,
    justifyContent: 'center',
  },
  catChipActive: { backgroundColor: colors.espresso, borderColor: colors.espresso },
  catText: { color: colors.inkSoft, fontSize: 14, fontWeight: '600' },
  catTextActive: { color: colors.espressoInk },
  sectionTitle: { fontSize: 20, fontWeight: '800', color: colors.ink, letterSpacing: -0.3, paddingHorizontal: 16, marginTop: 22, marginBottom: 10 },
  skeleton: { height: 108, borderRadius: radius.lg, backgroundColor: colors.sunken },
  empty: { margin: 16, padding: 24, borderRadius: radius.lg, borderWidth: 1, borderStyle: 'dashed', borderColor: colors.lineStrong, alignItems: 'center' },
  emptyTitle: { fontSize: 16, fontWeight: '700', color: colors.ink },
  emptyText: { fontSize: 13, color: colors.inkSoft, marginTop: 4, textAlign: 'center' },
  row: {
    flexDirection: 'row',
    gap: 12,
    marginHorizontal: 16,
    marginBottom: 12,
    padding: 10,
    backgroundColor: colors.surface,
    borderRadius: radius.lg,
    borderWidth: 1,
    borderColor: colors.line,
  },
  rowImage: { width: 96, height: 96, borderRadius: radius.md },
  rowImagePlaceholder: { backgroundColor: colors.cream, alignItems: 'center', justifyContent: 'center' },
  rowImageLetter: { fontSize: 32, color: colors.brand, fontWeight: '600' },
  rowBody: { flex: 1, justifyContent: 'space-between' },
  rowName: { fontSize: 15, fontWeight: '700', color: colors.ink },
  rowDesc: { fontSize: 13, color: colors.inkSoft, marginTop: 2, lineHeight: 18 },
  offerPill: {
    flexDirection: 'row', alignItems: 'center', gap: 4, alignSelf: 'flex-start', marginTop: 6,
    backgroundColor: colors.brand, borderRadius: 999, paddingHorizontal: 8, paddingVertical: 3, maxWidth: '100%',
  },
  offerPillLater: { backgroundColor: colors.brandSubtle },
  offerPillText: { fontSize: 11, fontWeight: '700', color: colors.white, flexShrink: 1 },
  offerPillTextLater: { color: colors.brand },
  dealsBox: { marginHorizontal: 16, marginTop: 12, padding: 12, borderRadius: 12, backgroundColor: colors.brandSubtle, gap: 8 },
  dealsTitle: { fontSize: 15, fontWeight: '800', color: colors.ink },
  dealRow: { flexDirection: 'row', gap: 8 },
  dealSummary: { fontSize: 13, fontWeight: '700', color: colors.ink },
  dealWhen: { fontSize: 12, color: colors.inkSoft, marginTop: 1 },
  rowFooter: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginTop: 8 },
  rowPrice: { fontSize: 15, fontWeight: '700', color: colors.ink },
  addBtn: { height: 36, paddingHorizontal: 16, borderRadius: radius.pill, backgroundColor: colors.brand, justifyContent: 'center' },
  addBtnText: { color: colors.white, fontSize: 14, fontWeight: '700' },
  cartBar: {
    position: 'absolute',
    bottom: 16,
    left: 16,
    right: 16,
    height: 60,
    backgroundColor: colors.espresso,
    borderRadius: radius.xl,
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 14,
    gap: 12,
    ...shadow.lifted,
  },
  cartCount: { minWidth: 32, height: 32, borderRadius: 16, backgroundColor: colors.brand, alignItems: 'center', justifyContent: 'center', paddingHorizontal: 8 },
  cartCountText: { color: colors.white, fontWeight: '800' },
  cartBarText: { flex: 1, color: colors.espressoInk, fontSize: 16, fontWeight: '700' },
  cartBarTotal: { color: colors.espressoInk, fontSize: 16, fontWeight: '800' },
  modalBackdrop: { flex: 1, backgroundColor: colors.overlay, justifyContent: 'flex-end' },
  sheet: { backgroundColor: colors.surface, borderTopLeftRadius: 24, borderTopRightRadius: 24, padding: 20, paddingBottom: 32 },
  sheetHandle: { alignSelf: 'center', width: 40, height: 4, borderRadius: 2, backgroundColor: colors.lineStrong, marginBottom: 14 },
  modalTitle: { fontSize: 22, fontWeight: '800', color: colors.ink, letterSpacing: -0.3 },
  sheetPrice: { fontSize: 16, fontWeight: '700', color: colors.brand, marginTop: 4, marginBottom: 14 },
  groupBlock: { marginBottom: 16 },
  groupHead: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  groupTitle: { fontSize: 16, fontWeight: '700', color: colors.ink },
  groupBadge: { fontSize: 11, fontWeight: '700', color: colors.inkSoft, backgroundColor: colors.sunken, paddingHorizontal: 8, paddingVertical: 3, borderRadius: radius.pill, overflow: 'hidden' },
  groupBadgeRequired: { color: colors.warningOn, backgroundColor: colors.warningSubtle },
  groupHint: { fontSize: 12, color: colors.inkMuted, marginTop: 2, marginBottom: 8 },
  optionRow: { flexDirection: 'row', alignItems: 'center', gap: 12, minHeight: 50, paddingHorizontal: 14, borderRadius: radius.md, borderWidth: 1, borderColor: colors.line, marginBottom: 8 },
  optionRowSelected: { borderColor: colors.brand, backgroundColor: colors.brandSubtle },
  optionMark: { width: 20, height: 20, borderRadius: 5, borderWidth: 2, borderColor: colors.lineStrong },
  optionMarkRound: { borderRadius: 10 },
  optionMarkOn: { borderColor: colors.brand, backgroundColor: colors.brand },
  optionText: { flex: 1, fontSize: 15, color: colors.ink },
  optionTextSelected: { fontWeight: '700' },
  optionPrice: { fontSize: 13, color: colors.inkSoft },
  modalError: { color: colors.dangerOn, fontSize: 13, marginTop: 4 },
  modalButtons: { flexDirection: 'row', gap: 10, marginTop: 14 },
  modalBtn: { flex: 1, height: 52, borderRadius: radius.md, alignItems: 'center', justifyContent: 'center' },
  modalBtnGhost: { backgroundColor: colors.sunken },
  modalBtnPrimary: { backgroundColor: colors.brand, flex: 2 },
  modalBtnGhostText: { color: colors.ink, fontWeight: '700', fontSize: 15 },
  modalBtnPrimaryText: { color: colors.white, fontWeight: '800', fontSize: 15 },
});
